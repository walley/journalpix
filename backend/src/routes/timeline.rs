use actix_web::{get, web, HttpResponse, Responder};
use crate::models::response::ApiResponse;
use crate::services::timeline::TimelineService;
use crate::utils::error::ApiError;
use uuid::Uuid;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/trips/{trip_id}/timeline")
            .route("", web::get().to(get_timeline))
    );
}

#[get("/trips/{trip_id}/timeline")]
pub async fn get_timeline(
    trip_id: web::Path<Uuid>,
) -> impl Responder {
    let timeline_service = TimelineService::new();
    
    match timeline_service.get_timeline(*trip_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
