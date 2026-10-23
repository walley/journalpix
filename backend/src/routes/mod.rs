pub mod health;
pub mod auth;
pub mod trips;
pub mod places;
pub mod photos;
pub mod timeline;


use crate::utils::auth::AuthService;
use crate::utils::error::ApiError;
use actix_web::HttpRequest;
use uuid::Uuid;

/// Extract the authenticated user id from the `Authorization: Bearer <jwt>`
/// header. Every protected endpoint goes through this.
pub fn auth_user_id(req: &HttpRequest, auth_service: &AuthService) -> Result<Uuid, ApiError> {
    let header = req
        .headers()
        .get(actix_web::http::header::AUTHORIZATION)
        .and_then(|h| h.to_str().ok())
        .ok_or_else(|| ApiError::Unauthorized("Missing Authorization header".into()))?;

    let token = header
        .strip_prefix("Bearer ")
        .ok_or_else(|| ApiError::Unauthorized("Expected 'Authorization: Bearer <token>'".into()))?;

    auth_service
        .get_user_id_from_token(token)
        .map_err(|e| ApiError::Unauthorized(e.to_string()))
}
