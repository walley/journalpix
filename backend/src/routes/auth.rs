use actix_web::{web, HttpResponse, Responder, ResponseError};
use crate::models::user::{CreateUserRequest, LoginRequest, UpdateUserRequest};
use crate::services::user::UserService;
use crate::utils::auth::AuthService;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

use super::auth_user_id;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/auth")
            .route("/register", web::post().to(register))
            .route("/login", web::post().to(login))
            .route("/me", web::get().to(get_current_user))
            .route("/me", web::put().to(update_current_user))
            .route("/me", web::delete().to(delete_current_user))
    );
}

pub async fn register(
    request: web::Json<CreateUserRequest>,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_service = UserService::new(db_pool.get_ref().clone());

    match user_service.create_user(request.into_inner(), &auth_service).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn login(
    request: web::Json<LoginRequest>,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_service = UserService::new(db_pool.get_ref().clone());

    match user_service.login(request.into_inner(), &auth_service).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn get_current_user(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let user_service = UserService::new(db_pool.get_ref().clone());

    match user_service.get_user(user_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn update_current_user(
    req: actix_web::HttpRequest,
    request: web::Json<UpdateUserRequest>,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let user_service = UserService::new(db_pool.get_ref().clone());

    match user_service.update_user(user_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn delete_current_user(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let user_service = UserService::new(db_pool.get_ref().clone());

    match user_service.delete_user(user_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
