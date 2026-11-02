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
use chrono_tz::Tz;
use uuid::Uuid;

/// The zone a response should be rendered in, when the caller asks for one.
///
/// Two ways to ask, query parameter winning over the header:
///   * `?tz=Asia/Tokyo`
///   * `X-Timezone: Asia/Tokyo`
///
/// This only affects how `local` is rendered — the stored instant and the
/// record's own zone are untouched. An unknown name is a 422 rather than a
/// silent fallback, because silently rendering in UTC would show the caller
/// a time they explicitly said they did not want.
pub fn requested_tz(req: &HttpRequest) -> Result<Option<Tz>, ApiError> {
    let raw = req
        .query_string()
        .split('&')
        .find_map(|kv| kv.strip_prefix("tz="))
        .or_else(|| {
            req.headers()
                .get("X-Timezone")
                .and_then(|v| v.to_str().ok())
        });

    match raw {
        None => Ok(None),
        Some(s) if s.trim().is_empty() => Ok(None),
        Some(s) => {
            let decoded = url_decode(s);
            match crate::models::zoned::parse_tz(&decoded) {
                Some(tz) => Ok(Some(tz)),
                None => Err(ApiError::ValidationError(format!(
                    "Unknown timezone: {}",
                    decoded
                ))),
            }
        }
    }
}

/// Percent-decode a query value (`%2F` -> `/`), which is how a client must
/// send an IANA name containing a slash.
fn url_decode(s: &str) -> String {
    let bytes = s.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut i = 0;
    while i < bytes.len() {
        // `%XX` needs two hex digits after the '%'.
        if bytes[i] == b'%' && i + 3 <= bytes.len() {
            let hex = std::str::from_utf8(&bytes[i + 1..i + 3]).unwrap_or("");
            match u8::from_str_radix(hex, 16) {
                Ok(b) => {
                    out.push(b);
                    i += 3;
                    continue;
                }
                Err(_) => {}
            }
        }
        out.push(if bytes[i] == b'+' { b' ' } else { bytes[i] });
        i += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

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
