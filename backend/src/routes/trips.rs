use actix_web::{web, HttpResponse, Responder, ResponseError};
use crate::models::trip::{CreateTripRequest, UpdateTripRequest};
use crate::services::trip::TripService;
use crate::utils::auth::AuthService;
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

use super::auth_user_id;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/trips")
            .route("", web::get().to(get_trips))
            .route("", web::post().to(create_trip))
            .route("/{trip_id}", web::get().to(get_trip))
            .route("/{trip_id}", web::put().to(update_trip))
            .route("/{trip_id}", web::delete().to(delete_trip))
    );
}

pub async fn get_trips(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let trip_service = TripService::new(db_pool.get_ref().clone());

    match trip_service.get_trips(user_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn create_trip(
    req: actix_web::HttpRequest,
    request: web::Json<CreateTripRequest>,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let trip_service = TripService::new(db_pool.get_ref().clone());

    match trip_service.create_trip(user_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Created().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn get_trip(
    trip_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let trip_service = TripService::new(db_pool.get_ref().clone());

    match trip_service.get_trip(*trip_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn update_trip(
    trip_id: web::Path<Uuid>,
    request: web::Json<UpdateTripRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let trip_service = TripService::new(db_pool.get_ref().clone());

    match trip_service.update_trip(*trip_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn delete_trip(
    trip_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let trip_service = TripService::new(db_pool.get_ref().clone());

    match trip_service.delete_trip(*trip_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
