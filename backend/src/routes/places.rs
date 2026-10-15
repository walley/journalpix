use actix_web::{post, get, put, delete, web, HttpResponse, Responder, ResponseError};
use crate::models::place::{CreatePlaceRequest, UpdatePlaceRequest};
use crate::services::place::PlaceService;
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/trips/{trip_id}/places")
            .route("", web::get().to(get_places))
            .route("", web::post().to(create_place))
    );
    
    cfg.service(
        web::scope("/places")
            .route("/{place_id}", web::get().to(get_place))
            .route("/{place_id}", web::put().to(update_place))
            .route("/{place_id}", web::delete().to(delete_place))
    );
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
    request: web::Json<CreatePlaceRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
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
