use chrono::{DateTime, Utc, NaiveDate};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Trip {
    pub id: Uuid,
    pub user_id: Uuid,
    pub title: String,
    pub description: Option<String>,
    pub start_date: Option<NaiveDate>,
    pub end_date: Option<NaiveDate>,
    pub status: TripStatus,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum TripStatus {
    Planned,
    InProgress,
    Completed,
    Cancelled,
}

impl Default for TripStatus {
    fn default() -> Self {
        TripStatus::Planned
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TripResponse {
    pub id: Uuid,
    pub user_id: Uuid,
    pub title: String,
    pub description: Option<String>,
    pub start_date: Option<NaiveDate>,
    pub end_date: Option<NaiveDate>,
    pub status: TripStatus,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub places_count: Option<i64>,
    pub photos_count: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreateTripRequest {
    pub title: String,
    pub description: Option<String>,
    pub start_date: Option<NaiveDate>,
    pub end_date: Option<NaiveDate>,
    pub status: Option<TripStatus>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdateTripRequest {
    pub title: Option<String>,
    pub description: Option<String>,
    pub start_date: Option<NaiveDate>,
    pub end_date: Option<NaiveDate>,
    pub status: Option<TripStatus>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TripWithPlaces {
    pub trip: TripResponse,
    pub places: Vec<crate::models::place::PlaceResponse>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TripTimeline {
    pub trip: TripResponse,
    pub timeline: Vec<TimelineItem>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TimelineItem {
    pub date: NaiveDate,
    pub places: Vec<crate::models::place::PlaceWithPhotos>,
}
