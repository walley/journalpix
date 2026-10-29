use actix_web::{web, HttpResponse, Responder, ResponseError};
use crate::services::timeline::TimelineService;
use crate::services::trip::TripService;
use crate::utils::auth::AuthService;
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

use super::{auth_user_id, require_owner};

pub fn configure(cfg: &mut web::ServiceConfig) {
    // NOTE: scope prefixes are literal (no {param}); use full-path routes.
    cfg.route("/trips/{trip_id}/timeline", web::get().to(get_timeline))
        .route("/trips/{trip_id}/timeline/reorder", web::put().to(reorder_timeline));
}

pub async fn get_timeline(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    trip_id: web::Path<Uuid>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let timeline_service = TimelineService::new(db_pool.get_ref().clone());
    let trip_service = TripService::new(db_pool.get_ref().clone());

    if let Err(e) = require_owner(trip_service.trip_owner(*trip_id).await, user_id) {
        return e.error_response();
    }

    match timeline_service.get_timeline(*trip_id).await {
        Ok(response) => HttpResponse::Ok().json(response),
        Err(e) => e.error_response(),
    }
}

pub async fn reorder_timeline(
    req: actix_web::HttpRequest,
    auth_service: web::Data<AuthService>,
    trip_id: web::Path<Uuid>,
    request: web::Json<Vec<Uuid>>,
    db_pool: web::Data<Arc<DatabaseConnection>>,
) -> impl Responder {
    let user_id = match auth_user_id(&req, &auth_service) {
        Ok(id) => id,
        Err(e) => return e.error_response(),
    };

    let timeline_service = TimelineService::new(db_pool.get_ref().clone());
    let trip_service = TripService::new(db_pool.get_ref().clone());
    let item_count = request.len();

    if let Err(e) = require_owner(trip_service.trip_owner(*trip_id).await, user_id) {
        log::warn!("Timeline reorder user_id={} trip_id={}: failed ({})", user_id, *trip_id, e);
        return e.error_response();
    }

    match timeline_service.reorder_timeline(*trip_id, request.into_inner()).await {
        Ok(response) => {
            log::info!(
                "Timeline reorder user_id={} trip_id={}: success (items={})",
                user_id,
                *trip_id,
                item_count
            );
            HttpResponse::Ok().json(response)
        }
        Err(e) => {
            log::warn!("Timeline reorder user_id={} trip_id={}: failed ({})", user_id, *trip_id, e);
            e.error_response()
        }
    }
}
