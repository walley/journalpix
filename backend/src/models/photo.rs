use chrono::{DateTime, Utc};
use chrono_tz::Tz;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::models::zoned::{RenderInZone, ZonedTime, ZonedTimeInput};

#[derive(Debug, Clone)]
pub struct Photo {
    pub id: Uuid,
    pub place_id: Option<Uuid>,
    pub user_id: Uuid,
    pub file_path: String,
    pub thumbnail_path: String,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    /// When the shutter fired — instant + the zone it happened in.
    pub taken_at: Option<ZonedTime>,
    pub caption: Option<String>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize)]
pub struct PhotoResponse {
    pub id: Uuid,
    pub place_id: Option<Uuid>,
    pub user_id: Uuid,
    pub file_path: String,
    pub thumbnail_path: String,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<ZonedTime>,
    pub caption: Option<String>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
    pub place: Option<crate::models::place::PlaceResponse>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CreatePhotoRequest {
    pub place_id: Option<Uuid>,
    pub file_path: String,
    pub thumbnail_path: String,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<ZonedTimeInput>,
    pub caption: Option<String>,
    pub order_index: Option<i32>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct UpdatePhotoRequest {
    pub place_id: Option<Uuid>,
    pub caption: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    /// Absent = leave unchanged; `null` = clear.
    pub taken_at: Option<ZonedTimeInput>,
    pub order_index: Option<i32>,
}

/// EXIF-derived metadata. `taken_at` is the raw EXIF `DateTimeOriginal`
/// string, which is *local* and zone-less; the caller supplies the zone.
#[derive(Debug, Clone)]
pub struct PhotoMetadata {
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<String>,
}

impl RenderInZone for PhotoResponse {
    fn render_in_zone(&mut self, tz: Tz) {
        self.taken_at.render_in_zone(tz);
    }
}
