use crate::models::trip::{CreateTripRequest, Trip, TripResponse, TripStatus, UpdateTripRequest};
use crate::models::response::ApiResponse;
use crate::utils::error::ApiError;
use chrono::Utc;
use uuid::Uuid;

pub struct TripService;

impl TripService {
    pub fn new() -> Self {
        TripService
    }

    pub async fn create_trip(
        &self,
        user_id: Uuid,
        request: CreateTripRequest,
    ) -> Result<ApiResponse<TripResponse>, ApiError> {
        let trip_id = Uuid::new_v4();
        
        let trip = Trip {
            id: trip_id,
            user_id,
            title: request.title,
            description: request.description,
            start_date: request.start_date,
            end_date: request.end_date,
            status: request.status.unwrap_or(TripStatus::Planned),
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };
        
        let response = TripResponse {
            id: trip.id,
            user_id: trip.user_id,
            title: trip.title,
            description: trip.description,
            start_date: trip.start_date,
            end_date: trip.end_date,
            status: trip.status,
            created_at: trip.created_at,
            updated_at: trip.updated_at,
            places_count: Some(0),
            photos_count: Some(0),
        };
        
        Ok(ApiResponse::success(response))
    }

    pub async fn get_trips(&self, user_id: Uuid) -> Result<ApiResponse<Vec<TripResponse>>, ApiError> {
        let trips = vec![
            TripResponse {
                id: Uuid::new_v4(),
                user_id,
                title: "Summer Vacation".to_string(),
                description: Some("A wonderful summer trip".to_string()),
                start_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 6, 1).unwrap()),
                end_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 6, 15).unwrap()),
                status: TripStatus::Completed,
                created_at: Utc::now(),
                updated_at: Utc::now(),
                places_count: Some(5),
                photos_count: Some(20),
            },
            TripResponse {
                id: Uuid::new_v4(),
                user_id,
                title: "Winter Adventure".to_string(),
                description: Some("Skiing in the mountains".to_string()),
                start_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 12, 20).unwrap()),
                end_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 12, 31).unwrap()),
                status: TripStatus::Planned,
                created_at: Utc::now(),
                updated_at: Utc::now(),
                places_count: Some(0),
                photos_count: Some(0),
            },
        ];
        
        Ok(ApiResponse::success(trips))
    }

    pub async fn get_trip(&self, trip_id: Uuid) -> Result<ApiResponse<TripResponse>, ApiError> {
        let trip = TripResponse {
            id: trip_id,
            user_id: Uuid::new_v4(),
            title: "Sample Trip".to_string(),
            description: Some("A sample trip".to_string()),
            start_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 1, 1).unwrap()),
            end_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 1, 10).unwrap()),
            status: TripStatus::InProgress,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            places_count: Some(3),
            photos_count: Some(15),
        };
        
        Ok(ApiResponse::success(trip))
    }

    pub async fn update_trip(
        &self,
        trip_id: Uuid,
        request: UpdateTripRequest,
    ) -> Result<ApiResponse<TripResponse>, ApiError> {
        let trip = TripResponse {
            id: trip_id,
            user_id: Uuid::new_v4(),
            title: request.title.unwrap_or_else(|| "Updated Trip".to_string()),
            description: request.description,
            start_date: request.start_date,
            end_date: request.end_date,
            status: request.status.unwrap_or(TripStatus::InProgress),
            created_at: Utc::now(),
            updated_at: Utc::now(),
            places_count: Some(0),
            photos_count: Some(0),
        };
        
        Ok(ApiResponse::success(trip))
    }

    pub async fn delete_trip(&self, trip_id: Uuid) -> Result<ApiResponse<String>, ApiError> {
        Ok(ApiResponse::message("Trip deleted successfully".to_string()))
    }
}
