use actix_web::{post, get, put, delete, web, HttpResponse, Responder};
use actix_multipart::Multipart;
use crate::models::place::{CreatePlaceRequest, UpdatePlaceRequest};
use crate::models::response::ApiResponse;
use crate::services::place::PlaceService;
use crate::utils::error::ApiError;
use uuid::Uuid;

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

#[get("/trips/{trip_id}/places")]
pub async fn get_places(
    trip_id: web::Path<Uuid>,
) -> impl Responder {
    let place_service = PlaceService::new();
    
    match place_service.get_places(*trip_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

#[post("/trips/{trip_id}/places")]
pub async fn create_place(
    trip_id: web::Path<Uuid>,
    request: web::Json<CreatePlaceRequest>,
) -> impl Responder {
    let place_service = PlaceService::new();
    
    match place_service.create_place(*trip_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Created().json(response),
        Err(e) => e.error_response(),
    }
}

#[get("/places/{place_id}")]
pub async fn get_place(
    place_id: web::Path<Uuid>,
) -> impl Responder {
    let place_service = PlaceService::new();
    
    match place_service.get_place(*place_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

#[put("/places/{place_id}")]
pub async fn update_place(
    place_id: web::Path<Uuid>,
    request: web::Json<UpdatePlaceRequest>,
) -> impl Responder {
    let place_service = PlaceService::new();
    
    match place_service.update_place(*place_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

#[delete("/places/{place_id}")]
pub async fn delete_place(
    place_id: web::Path<Uuid>,
) -> impl Responder {
    let place_service = PlaceService::new();
    
    match place_service.delete_place(*place_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
