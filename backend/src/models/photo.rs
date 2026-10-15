use chrono::{DateTime, Utc, NaiveDateTime};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Photo {
    pub id: Uuid,
    pub place_id: Option<Uuid>,
    pub user_id: Uuid,
    pub file_path: String,
    pub thumbnail_path: String,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<NaiveDateTime>,
    pub caption: Option<String>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhotoResponse {
    pub id: Uuid,
    pub place_id: Option<Uuid>,
    pub user_id: Uuid,
    pub file_path: String,
    pub thumbnail_path: String,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<NaiveDateTime>,
    pub caption: Option<String>,
    pub order_index: i32,
    pub created_at: DateTime<Utc>,
    pub place: Option<crate::models::place::PlaceResponse>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct CreatePhotoRequest {
    pub place_id: Option<Uuid>,
    pub file_path: String,
    pub thumbnail_path: String,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<NaiveDateTime>,
    pub caption: Option<String>,
    pub order_index: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UpdatePhotoRequest {
    pub place_id: Option<Uuid>,
    pub caption: Option<String>,
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<NaiveDateTime>,
    pub order_index: Option<i32>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PhotoMetadata {
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<NaiveDateTime>,
}
