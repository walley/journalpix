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
        Ok(response) => {
            let user_id = response
                .data
                .as_ref()
                .map(|d| d.user.id.to_string())
                .unwrap_or_else(|| "-".to_string());
            log::info!("User register: success (user_id={})", user_id);
            HttpResponse::Ok().json(response)
        }
        Err(e) => {
            log::warn!("User register: failed ({})", e);
            e.error_response()
        }
    }
}

pub async fn login(
    request: web::Json<LoginRequest>,
    auth_service: web::Data<AuthService>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let login_request = request.into_inner();

    // Audit trail for login attempts: the email as entered plus the outcome.
    // `{:?}` escapes quotes/control characters so a crafted email cannot
    // forge extra log lines. The password is never logged.
    let attempted_email = login_request.email.clone();
    let user_service = UserService::new(db_pool.get_ref().clone());

    match user_service.login(login_request, &auth_service).await {
        Ok(response) => {
            let user_id = response
                .data
                .as_ref()
                .map(|d| d.user.id.to_string())
                .unwrap_or_else(|| "-".to_string());
            log::info!(
                "Login attempt email={:?}: success (user_id={})",
                attempted_email,
                user_id
            );
            HttpResponse::Ok().json(response)
        }
        Err(e) => {
            log::warn!("Login attempt email={:?}: failed ({})", attempted_email, e);
            e.error_response()
        }
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
        Ok(response) => {
            log::info!("User update user_id={}: success", user_id);
            HttpResponse::Ok().json(response)
        }
        Err(e) => {
            log::warn!("User update user_id={}: failed ({})", user_id, e);
            e.error_response()
        }
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
        Ok(response) => {
            log::info!("User delete user_id={}: success", user_id);
            HttpResponse::Ok().json(response)
        }
        Err(e) => {
            log::warn!("User delete user_id={}: failed ({})", user_id, e);
            e.error_response()
        }
    }
}
