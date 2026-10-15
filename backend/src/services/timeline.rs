use crate::models::place::PlaceWithPhotos;
use crate::models::trip::{TimelineItem, TripTimeline};
use crate::models::response::ApiResponse;
use crate::models::trip::TripResponse;
use crate::utils::error::ApiError;
use chrono::{NaiveDate, Utc};
use uuid::Uuid;
use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

#[derive(Debug, Clone)]
pub struct TimelineService {
    db_pool: Arc<DatabaseConnection>,
}

impl TimelineService {
    pub fn new(db_pool: Arc<DatabaseConnection>) -> Self {
        TimelineService { db_pool }
    }

    pub async fn get_timeline(&self, trip_id: Uuid) -> Result<ApiResponse<TripTimeline>, ApiError> {
        let trip = TripResponse {
            id: trip_id,
            user_id: Uuid::new_v4(),
            title: "Summer Vacation".to_string(),
            description: Some("A wonderful summer trip".to_string()),
            start_date: Some(NaiveDate::from_ymd_opt(2024, 6, 1).unwrap()),
            end_date: Some(NaiveDate::from_ymd_opt(2024, 6, 15).unwrap()),
            status: crate::models::trip::TripStatus::Completed,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            places_count: Some(3),
            photos_count: Some(10),
        };

        let timeline = vec![
            TimelineItem {
                date: NaiveDate::from_ymd_opt(2024, 6, 1).unwrap(),
                places: vec![
                    PlaceWithPhotos {
                        id: Uuid::new_v4(),
                        trip_id,
                        name: "Eiffel Tower".to_string(),
                        description: Some("Iconic landmark in Paris".to_string()),
                        latitude: Some(48.8584),
                        longitude: Some(2.2945),
                        address: Some("Champ de Mars, 5 Av. Anatole France, 75007 Paris, France".to_string()),
                        visit_date: Some(NaiveDate::from_ymd_opt(2024, 6, 1).unwrap().and_hms_opt(14, 30, 0).unwrap()),
                        order_index: 1,
                        created_at: Utc::now(),
                        photos: vec![
                            crate::models::photo::PhotoResponse {
                                id: Uuid::new_v4(),
                                place_id: Some(Uuid::new_v4()),
                                user_id: Uuid::new_v4(),
                                file_path: "/uploads/eiffel1.jpg".to_string(),
                                thumbnail_path: "/thumbnails/eiffel1.jpg".to_string(),
                                latitude: Some(48.8584),
                                longitude: Some(2.2945),
                                taken_at: Some(NaiveDate::from_ymd_opt(2024, 6, 1).unwrap().and_hms_opt(14, 30, 0).unwrap()),
                                caption: Some("Eiffel Tower from below".to_string()),
                                order_index: 1,
                                created_at: Utc::now(),
                                place: None,
                            },
                            crate::models::photo::PhotoResponse {
                                id: Uuid::new_v4(),
                                place_id: Some(Uuid::new_v4()),
                                user_id: Uuid::new_v4(),
                                file_path: "/uploads/eiffel2.jpg".to_string(),
                                thumbnail_path: "/thumbnails/eiffel2.jpg".to_string(),
                                latitude: Some(48.8584),
                                longitude: Some(2.2945),
                                taken_at: Some(NaiveDate::from_ymd_opt(2024, 6, 1).unwrap().and_hms_opt(15, 0, 0).unwrap()),
                                caption: Some("Eiffel Tower at sunset".to_string()),
                                order_index: 2,
                                created_at: Utc::now(),
                                place: None,
                            },
                        ],
                    },
                ],
            },
            TimelineItem {
                date: NaiveDate::from_ymd_opt(2024, 6, 5).unwrap(),
                places: vec![
                    PlaceWithPhotos {
                        id: Uuid::new_v4(),
                        trip_id,
                        name: "Louvre Museum".to_string(),
                        description: Some("World's largest art museum".to_string()),
                        latitude: Some(48.8606),
                        longitude: Some(2.3376),
                        address: Some("Rue de Rivoli, 75001 Paris, France".to_string()),
                        visit_date: Some(NaiveDate::from_ymd_opt(2024, 6, 5).unwrap().and_hms_opt(10, 0, 0).unwrap()),
                        order_index: 2,
                        created_at: Utc::now(),
                        photos: vec![
                            crate::models::photo::PhotoResponse {
                                id: Uuid::new_v4(),
                                place_id: Some(Uuid::new_v4()),
                                user_id: Uuid::new_v4(),
                                file_path: "/uploads/louvre1.jpg".to_string(),
                                thumbnail_path: "/thumbnails/louvre1.jpg".to_string(),
                                latitude: Some(48.8606),
                                longitude: Some(2.3376),
                                taken_at: Some(NaiveDate::from_ymd_opt(2024, 6, 5).unwrap().and_hms_opt(10, 30, 0).unwrap()),
                                caption: Some("Mona Lisa".to_string()),
                                order_index: 1,
                                created_at: Utc::now(),
                                place: None,
                            },
                        ],
                    },
                ],
            },
        ];

        let result = TripTimeline {
            trip,
            timeline,
        };

        Ok(ApiResponse::success(result))
    }

    pub async fn reorder_timeline(
        &self,
        trip_id: Uuid,
        _new_order: Vec<Uuid>,
    ) -> Result<ApiResponse<TripTimeline>, ApiError> {
        let timeline = self.get_timeline(trip_id).await?;
        Ok(timeline)
    }
}
