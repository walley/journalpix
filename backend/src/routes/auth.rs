use actix_web::{post, get, web, HttpResponse, Responder, Error};
use crate::models::user::{CreateUserRequest, LoginRequest};
use crate::models::response::ApiResponse;
use crate::services::user::UserService;
use crate::utils::auth::AuthService;
use crate::utils::error::ApiError;
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

#[post("/auth/register")]
pub async fn register(
    request: web::Json<CreateUserRequest>,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let user_service = UserService::new(db_pool.get_ref().clone());
    
    match user_service.create_user(request.into_inner(), &auth_service).await {
        Ok(response) => Ok(HttpResponse::Ok().json(response)),
        Err(e) => Ok(e.error_response()),
    }
}

#[post("/auth/login")]
pub async fn login(
    request: web::Json<LoginRequest>,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let user_service = UserService::new(db_pool.get_ref().clone());
    
    match user_service.login(request.into_inner(), &auth_service).await {
        Ok(response) => Ok(HttpResponse::Ok().json(response)),
        Err(e) => Ok(e.error_response()),
    }
}

#[get("/auth/me")]
pub async fn get_current_user(
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let user_service = UserService::new(db_pool.get_ref().clone());
    
    match user_service.get_user(uuid::Uuid::new_v4()).await {
        Ok(response) => Ok(HttpResponse::Ok().json(response)),
        Err(e) => Ok(e.error_response()),
    }
}
