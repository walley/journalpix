use crate::models::place::{CreatePlaceRequest, Place, PlaceResponse, PlaceWithPhotos, UpdatePlaceRequest};
use crate::models::photo::PhotoResponse;
use crate::models::response::ApiResponse;
use crate::utils::error::ApiError;
use chrono::Utc;
use uuid::Uuid;

pub struct PlaceService;

impl PlaceService {
    pub fn new() -> Self {
        PlaceService
    }

    pub async fn create_place(
        &self,
        trip_id: Uuid,
        request: CreatePlaceRequest,
    ) -> Result<ApiResponse<PlaceResponse>, ApiError> {
        let place_id = Uuid::new_v4();
        
        let place = Place {
            id: place_id,
            trip_id,
            name: request.name,
            description: request.description,
            latitude: request.latitude,
            longitude: request.longitude,
            address: request.address,
            visit_date: request.visit_date,
            order_index: request.order_index.unwrap_or(0),
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };
        
        let response = PlaceResponse {
            id: place.id,
            trip_id: place.trip_id,
            name: place.name,
            description: place.description,
            latitude: place.latitude,
            longitude: place.longitude,
            address: place.address,
            visit_date: place.visit_date,
            order_index: place.order_index,
            created_at: place.created_at,
            updated_at: place.updated_at,
            photos_count: Some(0),
        };
        
        Ok(ApiResponse::success(response))
    }

    pub async fn get_places(&self, trip_id: Uuid) -> Result<ApiResponse<Vec<PlaceResponse>>, ApiError> {
        let places = vec![
            PlaceResponse {
                id: Uuid::new_v4(),
                trip_id,
                name: "Eiffel Tower".to_string(),
                description: Some("Iconic landmark in Paris".to_string()),
                latitude: Some(48.8584),
                longitude: Some(2.2945),
                address: Some("Champ de Mars, 5 Av. Anatole France, 75007 Paris, France".to_string()),
                visit_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 6, 5).unwrap().and_hms_opt(14, 30, 0).unwrap()),
                order_index: 1,
                created_at: Utc::now(),
                updated_at: Utc::now(),
                photos_count: Some(5),
            },
            PlaceResponse {
                id: Uuid::new_v4(),
                trip_id,
                name: "Louvre Museum".to_string(),
                description: Some("World's largest art museum".to_string()),
                latitude: Some(48.8606),
                longitude: Some(2.3376),
                address: Some("Rue de Rivoli, 75001 Paris, France".to_string()),
                visit_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 6, 6).unwrap().and_hms_opt(10, 0, 0).unwrap()),
                order_index: 2,
                created_at: Utc::now(),
                updated_at: Utc::now(),
                photos_count: Some(3),
            },
        ];
        
        Ok(ApiResponse::success(places))
    }

    pub async fn get_place(&self, place_id: Uuid) -> Result<ApiResponse<PlaceWithPhotos>, ApiError> {
        let place = PlaceWithPhotos {
            id: place_id,
            trip_id: Uuid::new_v4(),
            name: "Sample Place".to_string(),
            description: Some("A sample place".to_string()),
            latitude: Some(48.8584),
            longitude: Some(2.2945),
            address: Some("Sample Address".to_string()),
            visit_date: Some(chrono::NaiveDate::from_ymd_opt(2024, 1, 1).unwrap().and_hms_opt(12, 0, 0).unwrap()),
            order_index: 1,
            created_at: Utc::now(),
            photos: vec![
                PhotoResponse {
                    id: Uuid::new_v4(),
                    place_id: Some(place_id),
                    user_id: Uuid::new_v4(),
                    file_path: "/uploads/photo1.jpg".to_string(),
                    thumbnail_path: "/thumbnails/photo1.jpg".to_string(),
                    latitude: Some(48.8584),
                    longitude: Some(2.2945),
                    taken_at: Some(chrono::NaiveDate::from_ymd_opt(2024, 1, 1).unwrap().and_hms_opt(12, 0, 0).unwrap()),
                    caption: Some("A beautiful photo".to_string()),
                    order_index: 1,
                    created_at: Utc::now(),
                    place: None,
                }
            ],
        };
        
        Ok(ApiResponse::success(place))
    }

    pub async fn update_place(
        &self,
        place_id: Uuid,
        request: UpdatePlaceRequest,
    ) -> Result<ApiResponse<PlaceResponse>, ApiError> {
        let place = PlaceResponse {
            id: place_id,
            trip_id: Uuid::new_v4(),
            name: request.name.unwrap_or_else(|| "Updated Place".to_string()),
            description: request.description,
            latitude: request.latitude,
            longitude: request.longitude,
            address: request.address,
            visit_date: request.visit_date,
            order_index: request.order_index.unwrap_or(0),
            created_at: Utc::now(),
            updated_at: Utc::now(),
            photos_count: Some(0),
        };
        
        Ok(ApiResponse::success(place))
    }

    pub async fn delete_place(&self, place_id: Uuid) -> Result<ApiResponse<String>, ApiError> {
        Ok(ApiResponse::message("Place deleted successfully".to_string()))
    }
}
