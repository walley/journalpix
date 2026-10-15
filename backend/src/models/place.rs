use chrono::{DateTime, Utc, NaiveDateTime};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Place {
    pub id: Uuid,
    pub trip_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    pub visit_date: Option<NaiveDateTime>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlaceResponse {
    pub id: Uuid,
    pub trip_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    pub visit_date: Option<NaiveDateTime>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub photos_count: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlaceWithPhotos {
    pub id: Uuid,
    pub trip_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    pub visit_date: Option<NaiveDateTime>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
    pub photos: Vec<crate::models::photo::PhotoResponse>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreatePlaceRequest {
    pub trip_id: Uuid,
    pub name: String,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    pub visit_date: Option<NaiveDateTime>,
    pub order_index: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdatePlaceRequest {
    pub name: Option<String>,
    pub description: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub address: Option<String>,
    pub visit_date: Option<NaiveDateTime>,
    pub order_index: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PlaceCoordinates {
    pub latitude: f64,
    pub longitude: f64,
}
