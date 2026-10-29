pub mod health;
pub mod index;
pub mod auth;
pub mod trips;
pub mod places;
pub mod photos;
pub mod timeline;

/// Version advertised on the root banner, in startup logs and by `/health`.
/// Single source of truth: `[package] version` in Cargo.toml. Bump its patch
/// (third) number with every backend change so the deployed binary can be
/// identified after reinstalling/restarting (`make version`).
pub const APP_VERSION: &str = env!("CARGO_PKG_VERSION");

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

/// Enforce that the resource found by an owner lookup belongs to `user_id`.
/// Owners come from service lookups (`trip_owner`/`place_owner`/`photo_owner`)
/// which resolve 404 themselves; a resource that exists but belongs to
/// another user becomes 403. Every API route is login-gated, and route
/// handlers use this on top to reject cross-user access.
pub fn require_owner(
    lookup: Result<Uuid, ApiError>,
    user_id: Uuid,
) -> Result<(), ApiError> {
    match lookup {
        Err(e) => Err(e),
        Ok(owner) if owner == user_id => Ok(()),
        Ok(_) => Err(ApiError::Forbidden(
            "This resource belongs to another user".into(),
        )),
    }
}
