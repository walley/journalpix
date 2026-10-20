use crate::database::connection::{
    get_opt_f64, get_opt_naive_datetime, get_opt_string, get_timestamp, get_uuid, DatabaseConnection,
    PHOTO_COLS,
};
use crate::models::photo::PhotoResponse;
use crate::models::place::PlaceWithPhotos;
use crate::models::response::ApiResponse;
use crate::models::trip::{TimelineItem, TripTimeline};
use crate::services::place::map_photo_response;
use crate::services::trip::TripService;
use crate::utils::error::ApiError;
use uuid::Uuid;
use std::sync::Arc;

#[derive(Debug, Clone)]
pub struct TimelineService {
    db_pool: Arc<DatabaseConnection>,
    trip_service: TripService,
}

fn db_err(e: rusqlite::Error) -> ApiError {
    ApiError::DatabaseError(e.to_string())
}

impl TimelineService {
    pub fn new(db_pool: Arc<DatabaseConnection>) -> Self {
        TimelineService {
            trip_service: TripService::new(db_pool.clone()),
            db_pool,
        }
    }

    pub async fn get_timeline(&self, trip_id: Uuid) -> Result<ApiResponse<TripTimeline>, ApiError> {
        let trip = self.trip_service.find_trip(trip_id).await?;

        let places = self
            .db_pool
            .with_conn(|c| {
                let mut stmt = c.prepare(
                    "SELECT id, trip_id, name, description, latitude, longitude, address, visit_date, order_index, created_at, updated_at
                     FROM places WHERE trip_id = ?1 ORDER BY order_index, visit_date",
                )?;
                let rows = stmt
                    .query_map([trip_id.to_string()], |r| {
                        Ok(crate::models::place::Place {
                            id: get_uuid(r, 0)?,
                            trip_id: get_uuid(r, 1)?,
                            name: r.get(2)?,
                            description: get_opt_string(r, 3)?,
                            latitude: get_opt_f64(r, 4)?,
                            longitude: get_opt_f64(r, 5)?,
                            address: get_opt_string(r, 6)?,
                            visit_date: get_opt_naive_datetime(r, 7)?,
                            order_index: r.get(8)?,
                            created_at: get_timestamp(r, 9)?,
                            updated_at: get_timestamp(r, 10)?,
                        })
                    })?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                Ok(rows)
            })
            .map_err(db_err)?;

        let mut timeline: Vec<TimelineItem> = Vec::new();

        for place in places {
            let photos: Vec<PhotoResponse> = self
                .db_pool
                .with_conn(|c| {
                    let mut stmt = c.prepare(&format!(
                        "SELECT {PHOTO_COLS} FROM photos WHERE place_id = ?1 ORDER BY order_index"
                    ))?;
                    let rows = stmt
                        .query_map([place.id.to_string()], map_photo_response)?
                        .collect::<rusqlite::Result<Vec<_>>>()?;
                    Ok(rows)
                })
                .map_err(db_err)?;

            let date = place
                .visit_date
                .map(|v| v.date())
                .unwrap_or(place.created_at.date_naive());

            let item = PlaceWithPhotos {
                id: place.id,
                trip_id: place.trip_id,
                name: place.name,
                description: place.description,
                latitude: place.latitude,
                longitude: place.longitude,
                address: place.address,
                visit_date: place.visit_date,
                order_index: place.order_index,
                created_at: place.created_at,
                photos,
            };

            if let Some(existing) = timeline.iter_mut().find(|i| i.date == date) {
                existing.places.push(item);
            } else {
                timeline.push(TimelineItem {
                    date,
                    places: vec![item],
                });
            }
        }

        let trip_response = self
            .trip_service
            .get_trip(trip_id)
            .await?
            .data
            .ok_or_else(|| ApiError::InternalServerError("missing trip".into()))?;

        Ok(ApiResponse::success(TripTimeline {
            trip: trip_response,
            timeline,
        }))
    }

    pub async fn reorder_timeline(
        &self,
        trip_id: Uuid,
        new_order: Vec<Uuid>,
    ) -> Result<ApiResponse<TripTimeline>, ApiError> {
        // Verify the trip exists
        self.trip_service.find_trip(trip_id).await?;

        let now = chrono::Utc::now().to_rfc3339();
        self.db_pool
            .with_conn(|c| {
                for (idx, place_id) in new_order.iter().enumerate() {
                    c.execute(
                        "UPDATE places SET order_index = ?1, updated_at = ?2
                         WHERE id = ?3 AND trip_id = ?4",
                        rusqlite::params![idx as i32, now, place_id.to_string(), trip_id.to_string()],
                    )?;
                }
                Ok(())
            })
            .map_err(db_err)?;

        self.get_timeline(trip_id).await
    }
}
