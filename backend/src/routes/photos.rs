use actix_web::{post, get, put, delete, web, HttpResponse, Responder, ResponseError};
use actix_multipart::Multipart;
use futures::{StreamExt};
use crate::models::photo::{UpdatePhotoRequest};
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

pub async fn get_photos_for_place(
    place_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return e.error_response(),
    };
    
    match photo_service.get_photos(Some(*place_id), Uuid::new_v4()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn upload_photo_to_place(
    place_id: web::Path<Uuid>,
    payload: Multipart,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    use actix_multipart::LimitExceeded;
    use futures::StreamExt;
    
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return e.error_response(),
    };
    
    let mut payload_stream = payload;
    
    while let Some(result) = payload_stream.next().await {
        let mut field = match result {
            Ok(f) => f,
            Err(e) => {
                return ApiError::BadRequest(format!("Failed to read field: {}", e)).error_response()
            }
        };
        
        let content_type = field.content_type().as_ref().map(|ct| ct.to_string()).unwrap_or_else(|| "application/octet-stream".to_string());
        let extension = get_file_extension(&content_type);
        let user_id = Uuid::new_v4();
        let file_name = format!("{}_{}.{}", user_id, Uuid::new_v4(), extension);
        
        let bytes_result = field.bytes(10 * 1024 * 1024).await;
        
        let content: Vec<u8> = match bytes_result {
            Ok(Ok(b)) => b.to_vec(),
            Ok(Err(e)) => {
                return ApiError::BadRequest(format!("Failed to read file content: {}", e)).error_response()
            }
            Err(_) => {
                return ApiError::BadRequest("File size exceeded limit".to_string()).error_response()
            }
        };
        
        match photo_service.upload_photo(user_id, Some(*place_id), file_name, content).await {
            Ok(response) => return HttpResponse::Created().json(response),
            Err(e) => return e.error_response(),
        }
    }
    
    ApiError::BadRequest("No file uploaded".to_string()).error_response()
}

pub async fn get_photo(
    photo_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return e.error_response(),
    };
    
    match photo_service.get_photo(*photo_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn update_photo(
    photo_id: web::Path<Uuid>,
    request: web::Json<UpdatePhotoRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return e.error_response(),
    };
    
    match photo_service.update_photo(*photo_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn delete_photo(
    photo_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let photo_service = match PhotoService::new("./uploads", "./thumbnails", db_pool.get_ref().clone()) {
        Ok(s) => s,
        Err(e) => return e.error_response(),
    };
    
    match photo_service.delete_photo(*photo_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
