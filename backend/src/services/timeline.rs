use crate::database::connection::{
    get_opt_f64, get_opt_string, get_opt_zoned_time, get_timestamp, get_uuid,
    DatabaseConnection, PHOTO_COLS, PLACE_COLS,
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
                let mut stmt = c.prepare(&format!(
                    "SELECT {PLACE_COLS} FROM places WHERE trip_id = ?1 ORDER BY day, order_index, visit_date"
                ))?;
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
                            day: r.get(7)?,
                            visit_date: get_opt_zoned_time(r, 8, 9)?,
                            order_index: r.get(10)?,
                            created_at: get_timestamp(r, 11)?,
                            updated_at: get_timestamp(r, 12)?,
                        })
                    })?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                Ok(rows)
            })
            .map_err(db_err)?;

        // Group places by trip day. Trip days increment one by one regardless
        // of timezone jumps, so the calendar date of day N is
        // `trip.start_date + (N-1)` days.
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

            let item = PlaceWithPhotos {
                id: place.id,
                trip_id: place.trip_id,
                name: place.name,
                description: place.description,
                latitude: place.latitude,
                longitude: place.longitude,
                address: place.address,
                day: place.day,
                visit_date: place.visit_date,
                order_index: place.order_index,
                created_at: place.created_at,
                photos,
            };

            if let Some(existing) = timeline.iter_mut().find(|i| i.day == place.day) {
                existing.places.push(item);
            } else {
                let date = trip
                    .start_date
                    .map(|s| s + chrono::Duration::days(place.day as i64 - 1));
                timeline.push(TimelineItem {
                    day: place.day,
                    date,
                    places: vec![item],
                });
            }
        }

        // Sort by day so the timeline is always sequential.
        timeline.sort_by_key(|i| i.day);

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
