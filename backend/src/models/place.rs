use chrono::{DateTime, Utc};
use chrono_tz::Tz;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::models::zoned::{RenderInZone, ZonedTime};

#[derive(Debug, Clone)]
pub struct Place {
    pub id: Uuid,
    pub trip_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    /// 1-based trip day this place belongs to.
    pub day: i32,
    /// When it was visited — instant + the zone it happened in.
    pub visit_date: Option<ZonedTime>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize)]
pub struct PlaceResponse {
    pub id: Uuid,
    pub trip_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    pub day: i32,
    pub visit_date: Option<ZonedTime>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub photos_count: Option<i64>,
}

#[derive(Debug, Clone, Serialize)]
pub struct PlaceWithPhotos {
    pub id: Uuid,
    pub trip_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    pub day: i32,
    pub visit_date: Option<ZonedTime>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
    pub photos: Vec<crate::models::photo::PhotoResponse>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CreatePlaceRequest {
    /// Overridden by the path trip_id in the handler; `default` lets the
    /// client omit it (required purely by history).
    #[serde(default)]
    pub trip_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    /// 1-based trip day. Defaults to 1.
    pub day: Option<i32>,
    pub visit_date: Option<crate::models::zoned::ZonedTimeInput>,
    pub order_index: Option<i32>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct UpdatePlaceRequest {
    pub name: Option<String>,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    pub day: Option<i32>,
    /// Absent = leave unchanged; `null` = clear.
    pub visit_date: Option<crate::models::zoned::ZonedTimeInput>,
    pub order_index: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlaceCoordinates {
    pub latitude: f64,
    pub longitude: f64,
}

impl RenderInZone for PlaceResponse {
    fn render_in_zone(&mut self, tz: Tz) {
        self.visit_date.render_in_zone(tz);
    }
}

impl RenderInZone for PlaceWithPhotos {
    fn render_in_zone(&mut self, tz: Tz) {
        self.visit_date.render_in_zone(tz);
        for photo in &mut self.photos {
            photo.render_in_zone(tz);
        }
    }
}
