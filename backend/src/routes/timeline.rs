use actix_web::{web, HttpResponse, Responder, ResponseError};
use crate::services::timeline::TimelineService;
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

pub fn configure(cfg: &mut web::ServiceConfig) {
    // NOTE: scope prefixes are literal (no {param}); use full-path routes.
    cfg.route("/trips/{trip_id}/timeline", web::get().to(get_timeline))
        .route("/trips/{trip_id}/timeline/reorder", web::put().to(reorder_timeline));
}

pub async fn get_timeline(
    trip_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let timeline_service = TimelineService::new(db_pool.get_ref().clone());

    match timeline_service.get_timeline(*trip_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn reorder_timeline(
    trip_id: web::Path<Uuid>,
    request: web::Json<Vec<Uuid>>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let timeline_service = TimelineService::new(db_pool.get_ref().clone());

    match timeline_service.reorder_timeline(*trip_id, request.into_inner()).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}
