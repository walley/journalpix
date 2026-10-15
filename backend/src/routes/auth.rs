use actix_web::{post, get, web, HttpResponse, Responder, ResponseError};
use crate::models::user::{CreateUserRequest, LoginRequest};
use crate::services::user::UserService;
use crate::utils::auth::AuthService;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/auth")
            .route("/register", web::post().to(register))
            .route("/login", web::post().to(login))
            .route("/me", web::get().to(get_current_user))
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
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_service = UserService::new(db_pool.get_ref().clone());
    
    match user_service.get_user(uuid::Uuid::new_v4()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
