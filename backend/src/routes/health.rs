use crate::routes::APP_VERSION;
use actix_web::{get, HttpResponse, Responder};
use serde::Serialize;

#[derive(Serialize)]
struct HealthCheck {
    status: String,
    version: String,
    timestamp: String,
}

#[get("/health")]
pub async fn health_check() -> impl Responder {
    HttpResponse::Ok().json(HealthCheck {
        status: "ok".to_string(),
        version: APP_VERSION.to_string(),
        timestamp: chrono::Utc::now().to_rfc3339(),
    })
}
