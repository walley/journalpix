use actix_web::{post, get, put, delete, web, HttpResponse, Responder, Error};
use actix_multipart::Multipart;
use futures::{StreamExt};
use crate::models::photo::{UpdatePhotoRequest};
use crate::models::response::ApiResponse;
use crate::services::photo::PhotoService;
use crate::utils::error::ApiError;
use crate::utils::file::get_file_extension;
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/places/{place_id}/photos")
            .route("", web::get().to(get_photos_for_place))
            .route("", web::post().to(upload_photo_to_place))
    );
    
    cfg.service(
        web::scope("/photos")
            .route("/{photo_id}", web::get().to(get_photo))
            .route("/{photo_id}", web::put().to(update_photo))
            .route("/{photo_id}", web::delete().to(delete_photo))
    );
}

#[get("/places/{place_id}/photos")]
pub async fn get_photos_for_place(
    place_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return Ok(e.error_response()),
    };
    
    match photo_service.get_photos(Some(*place_id), Uuid::new_v4()).await {
        Ok(response) => Ok(HttpResponse::Ok().json(response)),
        Err(e) => Ok(e.error_response()),
    }
}

#[post("/places/{place_id}/photos")]
pub async fn upload_photo_to_place(
    place_id: web::Path<Uuid>,
    user_id: web::Path<Uuid>,
    payload: Multipart,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return Ok(e.error_response()),
    };
    
    let mut fields = payload.into_fields();
    
    while let Some(result) = fields.next().await {
        let field = match result {
            Ok(f) => f,
            Err(e) => return Ok(ApiError::BadRequest(format!("Failed to read field: {}", e)).error_response()),
        };
        
        let content_type = field.content_type().map(|ct| ct.to_string()).unwrap_or_else(|| "application/octet-stream".to_string());
        let extension = get_file_extension(&content_type);
        let file_name = format!("{}_{}.{}", user_id, Uuid::new_v4(), extension);
        
        let content = match field.bytes().await {
            Ok(c) => c.to_vec(),
            Err(e) => return Ok(ApiError::BadRequest(format!("Failed to read file content: {}", e)).error_response()),
        };
        
        match photo_service.upload_photo(*user_id, Some(*place_id), file_name, content).await {
            Ok(response) => return Ok(HttpResponse::Created().json(response)),
            Err(e) => return Ok(e.error_response()),
        }
    }
    
    Ok(ApiError::BadRequest("No file uploaded".to_string()).error_response())
}

#[get("/photos/{photo_id}")]
pub async fn get_photo(
    photo_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return Ok(e.error_response()),
    };
    
    match photo_service.get_photo(*photo_id).await {
        Ok(response) => Ok(HttpResponse::Ok().json(response)),
        Err(e) => Ok(e.error_response()),
    }
}

#[put("/photos/{photo_id}")]
pub async fn update_photo(
    photo_id: web::Path<Uuid>,
    request: web::Json<UpdatePhotoRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return Ok(e.error_response()),
    };
    
    match photo_service.update_photo(*photo_id, request.into_inner()).await {
        Ok(response) => Ok(HttpResponse::Ok().json(response)),
        Err(e) => Ok(e.error_response()),
    }
}

#[delete("/photos/{photo_id}")]
pub async fn delete_photo(
    photo_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return Ok(e.error_response()),
    };
    
    match photo_service.delete_photo(*photo_id).await {
        Ok(response) => Ok(HttpResponse::Ok().json(response)),
        Err(e) => Ok(e.error_response()),
    }
}
