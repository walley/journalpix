use actix_web::{post, get, web, HttpResponse, Responder};
use crate::models::user::{CreateUserRequest, LoginRequest};
use crate::models::response::ApiResponse;
use crate::services::user::UserService;
use crate::utils::auth::AuthService;
use crate::utils::error::ApiError;

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
) -> impl Responder {
    let user_service = UserService::new();
    
    match user_service.create_user(request.into_inner(), &auth_service).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

#[post("/auth/login")]
pub async fn login(
    request: web::Json<LoginRequest>,
    auth_service: web::Data<AuthService>,
) -> impl Responder {
    let user_service = UserService::new();
    
    match user_service.login(request.into_inner(), &auth_service).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

#[get("/auth/me")]
pub async fn get_current_user(
    auth_service: web::Data<AuthService>,
    user_service: web::Data<UserService>,
) -> impl Responder {
    match user_service.get_user(Uuid::new_v4()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
