use actix_web::{web, HttpResponse, Responder, ResponseError};
use actix_multipart::Multipart;
use futures::StreamExt;
use crate::models::photo::{UpdatePhotoRequest};
use crate::services::photo::PhotoService;
use crate::utils::auth::AuthService;
use crate::utils::error::ApiError;
use crate::utils::file::{get_file_extension, FileService};
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

use super::auth_user_id;

const MAX_FILE_SIZE: usize = 10 * 1024 * 1024; // 10 MB

pub fn configure(cfg: &mut web::ServiceConfig) {
    // NOTE: scope prefixes are literal (no {param}); use full-path routes.
    cfg.route("/places/{place_id}/photos", web::get().to(get_photos_for_place))
        .route("/places/{place_id}/photos", web::post().to(upload_photo_to_place))
        .route("/photos", web::get().to(get_all_photos))
        .route("/photos/{photo_id}", web::get().to(get_photo))
        .route("/photos/{photo_id}", web::put().to(update_photo))
        .route("/photos/{photo_id}", web::delete().to(delete_photo));
}

pub async fn get_photos_for_place(
    place_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
    file_service: web::Data<Arc<FileService>>,
) -> impl Responder {
    let photo_service = PhotoService::new(file_service.get_ref().clone(), db_pool.get_ref().clone());
    match photo_service.get_photos(Some(*place_id), Uuid::nil()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn upload_photo_to_place(
    req: actix_web::HttpRequest,
    place_id: web::Path<Uuid>,
    payload: Multipart,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
    file_service: web::Data<Arc<FileService>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let photo_service = PhotoService::new(file_service.get_ref().clone(), db_pool.get_ref().clone());
    let mut payload_stream = payload;

    while let Some(result) = payload_stream.next().await {
        let mut field = match result {
            Ok(f) => f,
            Err(e) => {
                return ApiError::BadRequest(format!("Failed to read field: {}", e)).error_response()
            }
        };

        let content_type = field
            .content_type()
            .as_ref()
            .map(|ct| ct.to_string())
            .unwrap_or_else(|| "application/octet-stream".to_string());
        let extension = get_file_extension(&content_type);
        let file_name = format!("{}_{}.{}", user_id, Uuid::new_v4(), extension);

        // Collect bytes up to MAX_FILE_SIZE.
        let mut content: Vec<u8> = Vec::new();
        loop {
            match field.next().await {
                Some(Ok(chunk)) => {
                    content.extend_from_slice(&chunk);
                    if content.len() > MAX_FILE_SIZE {
                        return ApiError::BadRequest("File size exceeded limit (10MB)".to_string())
                            .error_response();
                    }
                }
                None => break,
                Some(Err(e)) => {
                    return ApiError::BadRequest(format!("Failed to read file content: {}", e))
                        .error_response()
                }
            }
        }

        match photo_service
            .upload_photo(user_id, Some(*place_id), file_name, content)
            .await
        {
            Ok(response) => return HttpResponse::Created().json(response),
            Err(e) => return e.error_response(),
        }
    }

    ApiError::BadRequest("No file uploaded".to_string()).error_response()
}

pub async fn get_all_photos(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
    file_service: web::Data<Arc<FileService>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let photo_service = PhotoService::new(file_service.get_ref().clone(), db_pool.get_ref().clone());
    match photo_service.get_photos(None, user_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn get_photo(
    photo_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
    file_service: web::Data<Arc<FileService>>,
) -> impl Responder {
    let photo_service = PhotoService::new(file_service.get_ref().clone(), db_pool.get_ref().clone());

    match photo_service.get_photo(*photo_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn update_photo(
    photo_id: web::Path<Uuid>,
    request: web::Json<UpdatePhotoRequest>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
    file_service: web::Data<Arc<FileService>>,
) -> impl Responder {
    let photo_service = PhotoService::new(file_service.get_ref().clone(), db_pool.get_ref().clone());

    match photo_service.update_photo(*photo_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn delete_photo(
    photo_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
    file_service: web::Data<Arc<FileService>>,
) -> impl Responder {
    let photo_service = PhotoService::new(file_service.get_ref().clone(), db_pool.get_ref().clone());

    match photo_service.delete_photo(*photo_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
