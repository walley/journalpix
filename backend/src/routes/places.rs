use actix_web::{web, HttpResponse, Responder, ResponseError};
use crate::models::place::{CreatePlaceRequest, UpdatePlaceRequest};
use crate::services::place::PlaceService;
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

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
    trip_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let place_service = PlaceService::new(db_pool.get_ref().clone());

    match place_service.get_places(*trip_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn create_place(
    trip_id: web::Path<Uuid>,
    mut request: web::Json<CreatePlaceRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    // Path wins over any trip_id in the body.
    request.trip_id = *trip_id;

    let place_service = PlaceService::new(db_pool.get_ref().clone());

    match place_service.create_place(*trip_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Created().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn get_place(
    place_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let place_service = PlaceService::new(db_pool.get_ref().clone());

    match place_service.get_place(*place_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn update_place(
    place_id: web::Path<Uuid>,
    request: web::Json<UpdatePlaceRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let place_service = PlaceService::new(db_pool.get_ref().clone());

    match place_service.update_place(*place_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn delete_place(
    place_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let place_service = PlaceService::new(db_pool.get_ref().clone());

    match place_service.delete_place(*place_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
