use crate::database::connection::{
    get_i64_count, get_opt_naive_date, get_opt_string, get_timestamp, get_trip_status, get_uuid,
    DatabaseConnection,
};
use crate::models::trip::{CreateTripRequest, Trip, TripResponse, TripStatus, UpdateTripRequest};
use crate::models::response::ApiResponse;
use crate::utils::error::ApiError;
use chrono::Utc;
use uuid::Uuid;
use std::sync::Arc;

#[derive(Debug, Clone)]
pub struct TripService {
    db_pool: Arc<DatabaseConnection>,
}

fn db_err(e: rusqlite::Error) -> ApiError {
    ApiError::DatabaseError(e.to_string())
}

const TRIP_COLS: &str =
    "id, user_id, title, description, start_date, end_date, status, created_at, updated_at";

fn map_trip(r: &rusqlite::Row) -> rusqlite::Result<Trip> {
    Ok(Trip {
        id: get_uuid(r, 0)?,
        user_id: get_uuid(r, 1)?,
        title: r.get(2)?,
        description: get_opt_string(r, 3)?,
        start_date: get_opt_naive_date(r, 4)?,
        end_date: get_opt_naive_date(r, 5)?,
        status: get_trip_status(r, 6)?,
        created_at: get_timestamp(r, 7)?,
        updated_at: get_timestamp(r, 8)?,
    })
}

impl TripService {
    pub fn new(db_pool: Arc<DatabaseConnection>) -> Self {
        TripService { db_pool }
    }

    fn counts(&self, trip_id: Uuid) -> Result<(i64, i64), ApiError> {
        let places = self
            .db_pool
            .with_conn(|c| {
                c.query_row(
                    "SELECT COUNT(*) FROM places WHERE trip_id = ?1",
                    [trip_id.to_string()],
                    |r| get_i64_count(r, 0),
                )
            })
            .map_err(db_err)?;
        let photos = self
            .db_pool
            .with_conn(|c| {
                c.query_row(
                    "SELECT COUNT(*) FROM photos p JOIN places pl ON p.place_id = pl.id
                     WHERE pl.trip_id = ?1",
                    [trip_id.to_string()],
                    |r| get_i64_count(r, 0),
                )
            })
            .map_err(db_err)?;
        Ok((places, photos))
    }

    fn to_response(&self, trip: Trip) -> Result<TripResponse, ApiError> {
        let (places_count, photos_count) = self.counts(trip.id)?;
        Ok(TripResponse {
            id: trip.id,
            user_id: trip.user_id,
            title: trip.title,
            description: trip.description,
            start_date: trip.start_date,
            end_date: trip.end_date,
            status: trip.status,
            created_at: trip.created_at,
            updated_at: trip.updated_at,
            places_count: Some(places_count),
            photos_count: Some(photos_count),
        })
    }

    pub async fn create_trip(
        &self,
        user_id: Uuid,
        request: CreateTripRequest,
    ) -> Result<ApiResponse<TripResponse>, ApiError> {
        if request.title.trim().is_empty() {
            return Err(ApiError::ValidationError("Title is required".into()));
        }
        let status = request.status.unwrap_or(TripStatus::Planned);
        let trip = Trip {
            id: Uuid::new_v4(),
            user_id,
            title: request.title,
            description: request.description,
            start_date: request.start_date,
            end_date: request.end_date,
            status,
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };

        self.db_pool
            .with_conn(|c| {
                c.execute(
                    "INSERT INTO trips (id, user_id, title, description, start_date, end_date, status, created_at, updated_at)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?8)",
                    rusqlite::params![
                        trip.id.to_string(),
                        trip.user_id.to_string(),
                        trip.title,
                        trip.description,
                        trip.start_date.map(|d| d.to_string()),
                        trip.end_date.map(|d| d.to_string()),
                        status_str(&trip.status),
                        trip.created_at.to_rfc3339(),
                    ],
                )
            })
            .map_err(db_err)?;

        Ok(ApiResponse::success(self.to_response(trip)?))
    }

    pub async fn get_trips(&self, user_id: Uuid) -> Result<ApiResponse<Vec<TripResponse>>, ApiError> {
        let trips = self
            .db_pool
            .with_conn(|c| {
                let mut stmt = c.prepare(&format!(
                    "SELECT {TRIP_COLS} FROM trips WHERE user_id = ?1 ORDER BY created_at DESC"
                ))?;
                let rows = stmt
                    .query_map([user_id.to_string()], map_trip)?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                Ok(rows)
            })
            .map_err(db_err)?;

        let mut out = Vec::new();
        for t in trips {
            out.push(self.to_response(t)?);
        }
        Ok(ApiResponse::success(out))
    }

    pub async fn get_trip(&self, trip_id: Uuid) -> Result<ApiResponse<TripResponse>, ApiError> {
        let trip = self.find_trip(trip_id).await?;
        Ok(ApiResponse::success(self.to_response(trip)?))
    }

    pub async fn find_trip(&self, trip_id: Uuid) -> Result<Trip, ApiError> {
        self.db_pool
            .with_conn(|c| {
                c.query_row(
                    &format!("SELECT {TRIP_COLS} FROM trips WHERE id = ?1"),
                    [trip_id.to_string()],
                    map_trip,
                )
            })
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => ApiError::NotFound("Trip not found".into()),
                other => db_err(other),
            })
    }

    pub async fn update_trip(
        &self,
        trip_id: Uuid,
        request: UpdateTripRequest,
    ) -> Result<ApiResponse<TripResponse>, ApiError> {
        // Ensure it exists first
        self.find_trip(trip_id).await?;

        let now = Utc::now().to_rfc3339();
        let id = trip_id.to_string();
        self.db_pool
            .with_conn(|c| {
                if let Some(t) = &request.title {
                    if t.trim().is_empty() {
                        return Err(rusqlite::Error::InvalidParameterName("empty title".into()));
                    }
                    c.execute("UPDATE trips SET title = ?1 WHERE id = ?2", rusqlite::params![t, id])?;
                }
                if request.description.is_some() {
                    c.execute(
                        "UPDATE trips SET description = ?1 WHERE id = ?2",
                        rusqlite::params![request.description, id],
                    )?;
                }
                if request.start_date.is_some() {
                    c.execute(
                        "UPDATE trips SET start_date = ?1 WHERE id = ?2",
                        rusqlite::params![request.start_date.map(|d| d.to_string()), id],
                    )?;
                }
                if request.end_date.is_some() {
                    c.execute(
                        "UPDATE trips SET end_date = ?1 WHERE id = ?2",
                        rusqlite::params![request.end_date.map(|d| d.to_string()), id],
                    )?;
                }
                if let Some(s) = &request.status {
                    c.execute(
                        "UPDATE trips SET status = ?1 WHERE id = ?2",
                        rusqlite::params![status_str(s), id],
                    )?;
                }
                c.execute("UPDATE trips SET updated_at = ?1 WHERE id = ?2", rusqlite::params![now, id])?;
                Ok(())
            })
            .map_err(db_err)?;

        let trip = self.find_trip(trip_id).await?;
        Ok(ApiResponse::success(self.to_response(trip)?))
    }

    pub async fn delete_trip(&self, trip_id: Uuid) -> Result<ApiResponse<String>, ApiError> {
        let n = self
            .db_pool
            .with_conn(|c| c.execute("DELETE FROM trips WHERE id = ?1", [trip_id.to_string()]))
            .map_err(db_err)?;
        if n == 0 {
            return Err(ApiError::NotFound("Trip not found".into()));
        }
        Ok(ApiResponse::message("Trip deleted successfully".into()))
    }
}

pub fn status_str(s: &TripStatus) -> &'static str {
    match s {
        TripStatus::Planned => "planned",
        TripStatus::InProgress => "in_progress",
        TripStatus::Completed => "completed",
        TripStatus::Cancelled => "cancelled",
    }
}
