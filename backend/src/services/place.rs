use crate::database::connection::{
    get_i64_count, get_opt_f64, get_opt_string, get_opt_uuid, get_opt_zoned_time, get_timestamp,
    get_uuid, DatabaseConnection, PHOTO_COLS, PLACE_COLS,
};
use crate::models::photo::PhotoResponse;
use crate::models::place::{
    CreatePlaceRequest, Place, PlaceResponse, PlaceWithPhotos, UpdatePlaceRequest,
};
use crate::models::response::ApiResponse;
use crate::models::zoned::{ZonedTime, ZonedTimeInput};
use crate::utils::error::ApiError;
use crate::utils::validation::validate_coordinates;
use chrono::Utc;
use chrono_tz::Tz;
use uuid::Uuid;
use std::sync::Arc;

#[derive(Debug, Clone)]
pub struct PlaceService {
    db_pool: Arc<DatabaseConnection>,
}

fn db_err(e: rusqlite::Error) -> ApiError {
    ApiError::DatabaseError(e.to_string())
}

fn map_place(r: &rusqlite::Row) -> rusqlite::Result<Place> {
    Ok(Place {
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
}

pub fn map_photo_response(r: &rusqlite::Row) -> rusqlite::Result<PhotoResponse> {
    Ok(PhotoResponse {
        id: get_uuid(r, 0)?,
        place_id: get_opt_uuid(r, 1)?,
        user_id: get_uuid(r, 2)?,
        file_path: r.get(3)?,
        thumbnail_path: r.get(4)?,
        latitude: get_opt_f64(r, 5)?,
        longitude: get_opt_f64(r, 6)?,
        taken_at: get_opt_zoned_time(r, 7, 8)?,
        caption: get_opt_string(r, 9)?,
        order_index: r.get(10)?,
        created_at: get_timestamp(r, 11)?,
        place: None,
    })
}

impl PlaceService {
    pub fn new(db_pool: Arc<DatabaseConnection>) -> Self {
        PlaceService { db_pool }
    }

    fn photos_count(&self, place_id: Uuid) -> Result<i64, ApiError> {
        self.db_pool
            .with_conn(|c| {
                c.query_row(
                    "SELECT COUNT(*) FROM photos WHERE place_id = ?1",
                    [place_id.to_string()],
                    |r| get_i64_count(r, 0),
                )
            })
            .map_err(db_err)
    }

    fn to_response(&self, p: Place) -> Result<PlaceResponse, ApiError> {
        Ok(PlaceResponse {
            id: p.id,
            trip_id: p.trip_id,
            name: p.name,
            description: p.description,
            latitude: p.latitude,
            longitude: p.longitude,
            address: p.address,
            day: p.day,
            visit_date: p.visit_date,
            order_index: p.order_index,
            created_at: p.created_at,
            updated_at: p.updated_at,
            photos_count: Some(self.photos_count(p.id)?),
        })
    }

    /// Resolve a client-supplied moment against the user's zone. `None` in,
    /// `None` out; an explicit instant or a local reading both work.
    fn resolve_visit(
        input: Option<ZonedTimeInput>,
        default_tz: Tz,
    ) -> Result<Option<ZonedTime>, ApiError> {
        match input {
            None => Ok(None),
            Some(i) => i.resolve(default_tz),
        }
    }

    pub async fn create_place(
        &self,
        trip_id: Uuid,
        request: CreatePlaceRequest,
        default_tz: Tz,
    ) -> Result<ApiResponse<PlaceResponse>, ApiError> {
        if request.name.trim().is_empty() {
            return Err(ApiError::ValidationError("Name is required".into()));
        }
        validate_coordinates(request.latitude, request.longitude)?;

        // Trip must exist
        let exists = self
            .db_pool
            .with_conn(|c| {
                c.query_row(
                    "SELECT COUNT(*) FROM trips WHERE id = ?1",
                    [trip_id.to_string()],
                    |r| get_i64_count(r, 0),
                )
            })
            .map_err(db_err)?;
        if exists == 0 {
            return Err(ApiError::NotFound("Trip not found".into()));
        }

        let day = request.day.unwrap_or(1);
        if day < 1 {
            return Err(ApiError::ValidationError(
                "day must be 1 or greater".into(),
            ));
        }
        let visit_date = Self::resolve_visit(request.visit_date, default_tz)?;

        let place = Place {
            id: Uuid::new_v4(),
            trip_id,
            name: request.name,
            description: request.description,
            latitude: request.latitude,
            longitude: request.longitude,
            address: request.address,
            day,
            visit_date,
            order_index: request.order_index.unwrap_or(0),
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };

        self.db_pool
            .with_conn(|c| {
                c.execute(
                    "INSERT INTO places (id, trip_id, name, description, latitude, longitude, address, day, visit_date, visit_timezone, order_index, created_at, updated_at)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?12)",
                    rusqlite::params![
                        place.id.to_string(),
                        place.trip_id.to_string(),
                        place.name,
                        place.description,
                        place.latitude,
                        place.longitude,
                        place.address,
                        place.day,
                        place.visit_date.as_ref().map(|d| d.utc.to_rfc3339()),
                        place.visit_date.as_ref().map(|d| d.timezone.name().to_string()),
                        place.order_index,
                        place.created_at.to_rfc3339(),
                    ],
                )
            })
            .map_err(db_err)?;

        Ok(ApiResponse::success(self.to_response(place)?))
    }

    pub async fn get_places(&self, trip_id: Uuid) -> Result<ApiResponse<Vec<PlaceResponse>>, ApiError> {
        let places = self
            .db_pool
            .with_conn(|c| {
                let mut stmt = c.prepare(&format!(
                    "SELECT {PLACE_COLS} FROM places WHERE trip_id = ?1 ORDER BY day, order_index, visit_date"
                ))?;
                let rows = stmt
                    .query_map([trip_id.to_string()], map_place)?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                Ok(rows)
            })
            .map_err(db_err)?;

        let mut out = Vec::new();
        for p in places {
            out.push(self.to_response(p)?);
        }
        Ok(ApiResponse::success(out))
    }

    pub async fn find_place(&self, place_id: Uuid) -> Result<Place, ApiError> {
        self.db_pool
            .with_conn(|c| {
                c.query_row(
                    &format!("SELECT {PLACE_COLS} FROM places WHERE id = ?1"),
                    [place_id.to_string()],
                    map_place,
                )
            })
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => ApiError::NotFound("Place not found".into()),
                other => db_err(other),
            })
    }

    /// The user id owning `place_id` via its trip — used for ownership
    /// checks in route handlers (`routes::require_owner`). 404 when the
    /// place (or its trip) does not exist.
    pub async fn place_owner(&self, place_id: Uuid) -> Result<Uuid, ApiError> {
        self.db_pool
            .with_conn(|c| {
                c.query_row(
                    "SELECT t.user_id FROM places p JOIN trips t ON t.id = p.trip_id
                     WHERE p.id = ?1",
                    [place_id.to_string()],
                    |r| get_uuid(r, 0),
                )
            })
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => ApiError::NotFound("Place not found".into()),
                other => db_err(other),
            })
    }

    pub async fn get_place(&self, place_id: Uuid) -> Result<ApiResponse<PlaceWithPhotos>, ApiError> {
        let place = self.find_place(place_id).await?;
        let photos = self
            .db_pool
            .with_conn(|c| {
                let mut stmt = c.prepare(&format!(
                    "SELECT {PHOTO_COLS} FROM photos WHERE place_id = ?1 ORDER BY order_index"
                ))?;
                let rows = stmt
                    .query_map([place_id.to_string()], map_photo_response)?
                    .collect::<rusqlite::Result<Vec<_>>>()?;
                Ok(rows)
            })
            .map_err(db_err)?;

        Ok(ApiResponse::success(PlaceWithPhotos {
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
        }))
    }

    pub async fn update_place(
        &self,
        place_id: Uuid,
        request: UpdatePlaceRequest,
        default_tz: Tz,
    ) -> Result<ApiResponse<PlaceResponse>, ApiError> {
        self.find_place(place_id).await?;
        validate_coordinates(request.latitude, request.longitude)?;

        if let Some(day) = request.day {
            if day < 1 {
                return Err(ApiError::ValidationError(
                    "day must be 1 or greater".into(),
                ));
            }
        }

        let now = Utc::now().to_rfc3339();
        let id = place_id.to_string();

        // Resolve any client-supplied moment before opening the connection,
        // since `resolve_visit` returns ApiError and the closure below must
        // return rusqlite::Error only.
        let resolved_visit: Option<ZonedTime> = match &request.visit_date {
            None => None,
            Some(visit) => visit.clone().resolve(default_tz)?,
        };

        self.db_pool
            .with_conn(|c| {
                if let Some(n) = &request.name {
                    c.execute("UPDATE places SET name = ?1 WHERE id = ?2", rusqlite::params![n, id])?;
                }
                if request.description.is_some() {
                    c.execute(
                        "UPDATE places SET description = ?1 WHERE id = ?2",
                        rusqlite::params![request.description, id],
                    )?;
                }
                if request.latitude.is_some() {
                    c.execute("UPDATE places SET latitude = ?1 WHERE id = ?2", rusqlite::params![request.latitude, id])?;
                }
                if request.longitude.is_some() {
                    c.execute("UPDATE places SET longitude = ?1 WHERE id = ?2", rusqlite::params![request.longitude, id])?;
                }
                if request.address.is_some() {
                    c.execute("UPDATE places SET address = ?1 WHERE id = ?2", rusqlite::params![request.address, id])?;
                }
                if let Some(day) = request.day {
                    c.execute("UPDATE places SET day = ?1 WHERE id = ?2", rusqlite::params![day, id])?;
                }
                if request.visit_date.is_some() {
                    c.execute(
                        "UPDATE places SET visit_date = ?1, visit_timezone = ?2 WHERE id = ?3",
                        rusqlite::params![
                            resolved_visit.as_ref().map(|d| d.utc.to_rfc3339()),
                            resolved_visit.as_ref().map(|d| d.timezone.name().to_string()),
                            id
                        ],
                    )?;
                }
                if let Some(oi) = request.order_index {
                    c.execute("UPDATE places SET order_index = ?1 WHERE id = ?2", rusqlite::params![oi, id])?;
                }
                c.execute("UPDATE places SET updated_at = ?1 WHERE id = ?2", rusqlite::params![now, id])?;
                Ok(())
            })
            .map_err(db_err)?;

        let place = self.find_place(place_id).await?;
        Ok(ApiResponse::success(self.to_response(place)?))
    }

    pub async fn delete_place(&self, place_id: Uuid) -> Result<ApiResponse<String>, ApiError> {
        let n = self
            .db_pool
            .with_conn(|c| c.execute("DELETE FROM places WHERE id = ?1", [place_id.to_string()]))
            .map_err(db_err)?;
        if n == 0 {
            return Err(ApiError::NotFound("Place not found".into()));
        }
        Ok(ApiResponse::message("Place deleted successfully".into()))
    }
}
