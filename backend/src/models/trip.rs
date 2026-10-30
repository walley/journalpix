use chrono::{DateTime, NaiveDate, Utc};
use chrono_tz::Tz;
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use crate::models::zoned::RenderInZone;

#[derive(Debug, Clone)]
pub struct Trip {
    pub id: Uuid,
    pub user_id: Uuid,
    pub title: String,
    pub description: Option<String>,
    /// Day 1 of the trip.
    pub start_date: Option<NaiveDate>,
    /// IANA zone in effect at departure.
    pub start_timezone: Option<String>,
    /// Last day of the trip.
    pub end_date: Option<NaiveDate>,
    /// IANA zone in effect at the end.
    pub end_timezone: Option<String>,
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

#[derive(Debug, Clone, Serialize)]
pub struct TripResponse {
    pub id: Uuid,
    pub user_id: Uuid,
    pub title: String,
    pub description: Option<String>,
    pub start_date: Option<NaiveDate>,
    pub start_timezone: Option<String>,
    pub end_date: Option<NaiveDate>,
    pub end_timezone: Option<String>,
    /// Number of trip days, inclusive of both ends. `null` until both dates
    /// are set. Trip days increment by one regardless of timezone jumps.
    pub days_count: Option<i64>,
    pub status: TripStatus,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub places_count: Option<i64>,
    pub photos_count: Option<i64>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CreateTripRequest {
    pub title: String,
    pub description: Option<String>,
    pub start_date: Option<NaiveDate>,
    pub start_timezone: Option<String>,
    pub end_date: Option<NaiveDate>,
    pub end_timezone: Option<String>,
    pub status: Option<TripStatus>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct UpdateTripRequest {
    pub title: Option<String>,
    pub description: Option<String>,
    pub start_date: Option<NaiveDate>,
    pub start_timezone: Option<String>,
    pub end_date: Option<NaiveDate>,
    pub end_timezone: Option<String>,
    pub status: Option<TripStatus>,
}

#[derive(Debug, Clone, Serialize)]
pub struct TripWithPlaces {
    pub trip: TripResponse,
    pub places: Vec<crate::models::place::PlaceResponse>,
}

#[derive(Debug, Clone, Serialize)]
pub struct TripTimeline {
    pub trip: TripResponse,
    pub timeline: Vec<TimelineItem>,
}

#[derive(Debug, Clone, Serialize)]
pub struct TimelineItem {
    /// 1-based trip day. Sequential: day N's calendar date is
    /// `trip.start_date + (N-1)` days, computed without regard to zones.
    pub day: i32,
    /// Calendar date of this trip day (`start_date + day - 1`), or `null`
    /// when the trip has no `start_date`.
    pub date: Option<NaiveDate>,
    pub places: Vec<crate::models::place::PlaceWithPhotos>,
}

impl RenderInZone for TimelineItem {
    fn render_in_zone(&mut self, tz: Tz) {
        for place in &mut self.places {
            place.render_in_zone(tz);
        }
    }
}

impl RenderInZone for TripTimeline {
    fn render_in_zone(&mut self, tz: Tz) {
        for item in &mut self.timeline {
            item.render_in_zone(tz);
        }
    }
}
