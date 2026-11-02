use actix_web::{web, HttpResponse, Responder, ResponseError};
use crate::models::place::{CreatePlaceRequest, UpdatePlaceRequest};
use crate::models::zoned::RenderInZone;
use crate::config::Config;
use crate::services::place::PlaceService;
use crate::services::trip::TripService;
use crate::utils::auth::AuthService;
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

use super::{auth_user_id, require_owner, requested_tz};

pub fn configure(cfg: &mut web::ServiceConfig) {
    // NOTE: actix-web scope prefixes are matched literally (no {param}
    // support), so these must be plain routes with full paths.
    cfg.route("/trips/{trip_id}/places", web::get().to(get_places))
        .route("/trips/{trip_id}/places", web::post().to(create_place))
        .route("/places/{place_id}", web::get().to(get_place))
        .route("/places/{place_id}", web::put().to(update_place))
        .route("/places/{place_id}", web::delete().to(delete_place));
}

pub async fn get_places(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    trip_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let place_service = PlaceService::new(db_pool.get_ref().clone());
    let trip_service = TripService::new(db_pool.get_ref().clone());

    if let Err(e) = require_owner(trip_service.trip_owner(*trip_id).await, user_id) {
        return e.error_response();
    }

    match place_service.get_places(*trip_id).await {
        Ok(mut response) => {
            if let Ok(Some(tz)) = requested_tz(&req) {
                if let Some(data) = response.data.as_mut() {
                    for place in data.iter_mut() {
                        place.render_in_zone(tz);
                    }
                }
            }
            HttpResponse::Ok().json(response)
        }
        Err(e) => e.error_response(),
    }
}

pub async fn create_place(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    trip_id: web::Path<Uuid>,
    mut request: web::Json<CreatePlaceRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
    config: web::Data<Config>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    // Path wins over any trip_id in the body.
    request.trip_id = *trip_id;

    let place_service = PlaceService::new(db_pool.get_ref().clone());
    let trip_service = TripService::new(db_pool.get_ref().clone());

    if let Err(e) = require_owner(trip_service.trip_owner(*trip_id).await, user_id) {
        log::warn!("Place create user_id={} trip_id={}: failed ({})", user_id, *trip_id, e);
        return e.error_response();
    }

    match place_service.create_place(*trip_id, request.into_inner(), config.default_tz()).await {
        Ok(mut response) => {
            if let Ok(Some(tz)) = requested_tz(&req) {
                if let Some(data) = response.data.as_mut() {
                    data.render_in_zone(tz);
                }
            }
            let place_id = response
                .data
                .as_ref()
                .map(|p| p.id.to_string())
                .unwrap_or_else(|| "-".to_string());
            log::info!(
                "Place create user_id={} trip_id={}: success (place_id={})",
                user_id,
                *trip_id,
                place_id
            );
            HttpResponse::Created().json(response)
        }
        Err(e) => {
            log::warn!("Place create user_id={} trip_id={}: failed ({})", user_id, *trip_id, e);
            e.error_response()
        }
    }
}

pub async fn get_place(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    place_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let place_service = PlaceService::new(db_pool.get_ref().clone());

    if let Err(e) = require_owner(place_service.place_owner(*place_id).await, user_id) {
        return e.error_response();
    }

    match place_service.get_place(*place_id).await {
        Ok(mut response) => {
            if let Ok(Some(tz)) = requested_tz(&req) {
                if let Some(data) = response.data.as_mut() {
                    data.render_in_zone(tz);
                }
            }
            HttpResponse::Ok().json(response)
        }
        Err(e) => e.error_response(),
    }
}

pub async fn update_place(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    place_id: web::Path<Uuid>,
    request: web::Json<UpdatePlaceRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
    config: web::Data<Config>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let place_service = PlaceService::new(db_pool.get_ref().clone());

    if let Err(e) = require_owner(place_service.place_owner(*place_id).await, user_id) {
        log::warn!("Place update user_id={} place_id={}: failed ({})", user_id, *place_id, e);
        return e.error_response();
    }

    match place_service.update_place(*place_id, request.into_inner(), config.default_tz()).await {
        Ok(mut response) => {
            if let Ok(Some(tz)) = requested_tz(&req) {
                if let Some(data) = response.data.as_mut() {
                    data.render_in_zone(tz);
                }
            }
            log::info!(
                "Place update user_id={} place_id={}: success",
                user_id,
                *place_id
            );
            HttpResponse::Ok().json(response)
        }
        Err(e) => {
            log::warn!("Place update user_id={} place_id={}: failed ({})", user_id, *place_id, e);
            e.error_response()
        }
    }
}

pub async fn delete_place(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    place_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let place_service = PlaceService::new(db_pool.get_ref().clone());

    if let Err(e) = require_owner(place_service.place_owner(*place_id).await, user_id) {
        log::warn!("Place delete user_id={} place_id={}: failed ({})", user_id, *place_id, e);
        return e.error_response();
    }

    match place_service.delete_place(*place_id).await {
        Ok(response) => {
            log::info!("Place delete user_id={} place_id={}: success", user_id, *place_id);
            HttpResponse::Ok().json(response)
        }
        Err(e) => {
            log::warn!("Place delete user_id={} place_id={}: failed ({})", user_id, *place_id, e);
            e.error_response()
        }
    }
}
