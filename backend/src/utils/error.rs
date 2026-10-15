use actix_web::{HttpResponse, ResponseError};
use serde::Serialize;
use std::fmt;

#[derive(Debug, Serialize, Clone)]
pub enum ApiError {
    NotFound(String),
    BadRequest(String),
    Unauthorized(String),
    Forbidden(String),
    InternalServerError(String),
    ValidationError(String),
    DatabaseError(String),
    FileError(String),
    AuthError(String),
}

impl fmt::Display for ApiError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ApiError::NotFound(msg) => write!(f, "Not Found: {}", msg),
            ApiError::BadRequest(msg) => write!(f, "Bad Request: {}", msg),
            ApiError::Unauthorized(msg) => write!(f, "Unauthorized: {}", msg),
            ApiError::Forbidden(msg) => write!(f, "Forbidden: {}", msg),
            ApiError::InternalServerError(msg) => write!(f, "Internal Server Error: {}", msg),
            ApiError::ValidationError(msg) => write!(f, "Validation Error: {}", msg),
            ApiError::DatabaseError(msg) => write!(f, "Database Error: {}", msg),
            ApiError::FileError(msg) => write!(f, "File Error: {}", msg),
            ApiError::AuthError(msg) => write!(f, "Auth Error: {}", msg),
        }
    }
}

impl ResponseError for ApiError {
    fn error_response(&self) -> HttpResponse {
        match self {
            ApiError::NotFound(msg) => HttpResponse::NotFound().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
            ApiError::BadRequest(msg) => HttpResponse::BadRequest().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
            ApiError::Unauthorized(msg) => HttpResponse::Unauthorized().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
            ApiError::Forbidden(msg) => HttpResponse::Forbidden().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
            ApiError::InternalServerError(msg) => HttpResponse::InternalServerError().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
            ApiError::ValidationError(msg) => HttpResponse::UnprocessableEntity().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
            ApiError::DatabaseError(msg) => HttpResponse::InternalServerError().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
            ApiError::FileError(msg) => HttpResponse::InternalServerError().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
            ApiError::AuthError(msg) => HttpResponse::Unauthorized().json(serde_json::json!({
                "success": false,
                "error": msg
            })),
        }
    }
}

impl std::error::Error for ApiError {}

impl From<std::io::Error> for ApiError {
    fn from(err: std::io::Error) -> Self {
        ApiError::FileError(format!("{:?}", err))
    }
}

impl From<jsonwebtoken::errors::Error> for ApiError {
    fn from(err: jsonwebtoken::errors::Error) -> Self {
        ApiError::AuthError(format!("{:?}", err))
    }
}

impl From<bcrypt::BcryptError> for ApiError {
    fn from(err: bcrypt::BcryptError) -> Self {
        ApiError::AuthError(format!("{:?}", err))
    }
}
