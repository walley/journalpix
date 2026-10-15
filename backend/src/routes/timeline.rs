use actix_web::{get, web, HttpResponse, Responder, Error};
use crate::models::response::ApiResponse;
use crate::services::timeline::TimelineService;
use crate::utils::error::ApiError;
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

pub fn configure(cfg: &mut web::ServiceConfig) {
    cfg.service(
        web::scope("/trips/{trip_id}/timeline")
            .route("", web::get().to(get_timeline))
    );
}

#[get("/trips/{trip_id}/timeline")]
pub async fn get_timeline(
    trip_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> Result<HttpResponse, Error> {
    let timeline_service = TimelineService::new(db_pool.get_ref().clone());
    
    match timeline_service.get_timeline(*trip_id).await {
        Ok(response) => Ok(HttpResponse::Ok().json(response)),
        Err(e) => Ok(e.error_response()),
    }
}
