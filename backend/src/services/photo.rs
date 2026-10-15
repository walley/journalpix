use crate::models::photo::{CreatePhotoRequest, Photo, PhotoMetadata, PhotoResponse, UpdatePhotoRequest};
use crate::models::response::ApiResponse;
use crate::utils::error::ApiError;
use crate::utils::exif::ExifService;
use crate::utils::file::FileService;
use chrono::Utc;
use uuid::Uuid;

pub struct PhotoService {
    file_service: FileService,
    exif_service: ExifService,
}

impl PhotoService {
    pub fn new(upload_dir: &str, thumbnail_dir: &str) -> Result<Self, ApiError> {
        Ok(Self {
            file_service: FileService::new(upload_dir, thumbnail_dir)?,
            exif_service: ExifService::new(),
        })
    }

    pub async fn upload_photo(
        &self,
        user_id: Uuid,
        place_id: Option<Uuid>,
        file_name: String,
        file_content: Vec<u8>,
    ) -> Result<ApiResponse<PhotoResponse>, ApiError> {
        let photo_id = Uuid::new_v4();
        
        let metadata = self.exif_service.extract_metadata(&file_content)?;
        
        let file_path = self.file_service.save_file(&file_name, &file_content)?;
        
        let thumbnail_name = format!("thumb_{}", file_name);
        let thumbnail_path = self.file_service.save_thumbnail(&thumbnail_name, &file_content)?;
        
        let photo = Photo {
            id: photo_id,
            place_id,
            user_id,
            file_path: file_path.clone(),
            thumbnail_path: thumbnail_path.clone(),
            latitude: metadata.latitude,
            longitude: metadata.longitude,
            taken_at: metadata.taken_at.and_then(|s| chrono::NaiveDateTime::parse_from_str(&s, "%Y:%m:%d %H:%M:%S").ok()),
            caption: None,
            order_index: 0,
            created_at: Utc::now(),
        };
        
        let response = PhotoResponse {
            id: photo.id,
            place_id: photo.place_id,
            user_id: photo.user_id,
            file_path: photo.file_path,
            thumbnail_path: photo.thumbnail_path,
            latitude: photo.latitude,
            longitude: photo.longitude,
            taken_at: photo.taken_at,
            caption: photo.caption,
            order_index: photo.order_index,
            created_at: photo.created_at,
            place: None,
        };
        
        Ok(ApiResponse::success(response))
    }

    pub async fn get_photos(&self, place_id: Option<Uuid>, user_id: Uuid) -> Result<ApiResponse<Vec<PhotoResponse>>, ApiError> {
        let photos = vec![
            PhotoResponse {
                id: Uuid::new_v4(),
                place_id,
                user_id,
                file_path: "/uploads/photo1.jpg".to_string(),
                thumbnail_path: "/thumbnails/photo1.jpg".to_string(),
                latitude: Some(48.8584),
                longitude: Some(2.2945),
                taken_at: Some(chrono::NaiveDate::from_ymd_opt(2024, 1, 1).unwrap().and_hms_opt(12, 0, 0).unwrap()),
                caption: Some("Photo 1".to_string()),
                order_index: 1,
                created_at: Utc::now(),
                place: None,
            },
            PhotoResponse {
                id: Uuid::new_v4(),
                place_id,
                user_id,
                file_path: "/uploads/photo2.jpg".to_string(),
                thumbnail_path: "/thumbnails/photo2.jpg".to_string(),
                latitude: Some(48.8585),
                longitude: Some(2.2946),
                taken_at: Some(chrono::NaiveDate::from_ymd_opt(2024, 1, 1).unwrap().and_hms_opt(12, 30, 0).unwrap()),
                caption: Some("Photo 2".to_string()),
                order_index: 2,
                created_at: Utc::now(),
                place: None,
            },
        ];
        
        Ok(ApiResponse::success(photos))
    }

    pub async fn get_photo(&self, photo_id: Uuid) -> Result<ApiResponse<PhotoResponse>, ApiError> {
        let photo = PhotoResponse {
            id: photo_id,
            place_id: Some(Uuid::new_v4()),
            user_id: Uuid::new_v4(),
            file_path: "/uploads/photo.jpg".to_string(),
            thumbnail_path: "/thumbnails/photo.jpg".to_string(),
            latitude: Some(48.8584),
            longitude: Some(2.2945),
            taken_at: Some(chrono::NaiveDate::from_ymd_opt(2024, 1, 1).unwrap().and_hms_opt(12, 0, 0).unwrap()),
            caption: Some("A beautiful photo".to_string()),
            order_index: 1,
            created_at: Utc::now(),
            place: None,
        };
        
        Ok(ApiResponse::success(photo))
    }

    pub async fn update_photo(
        &self,
        photo_id: Uuid,
        request: UpdatePhotoRequest,
    ) -> Result<ApiResponse<PhotoResponse>, ApiError> {
        let photo = PhotoResponse {
            id: photo_id,
            place_id: request.place_id,
            user_id: Uuid::new_v4(),
            file_path: "/uploads/photo.jpg".to_string(),
            thumbnail_path: "/thumbnails/photo.jpg".to_string(),
            latitude: request.latitude,
            longitude: request.longitude,
            taken_at: request.taken_at,
            caption: request.caption,
            order_index: request.order_index.unwrap_or(0),
            created_at: Utc::now(),
            place: None,
        };
        
        Ok(ApiResponse::success(photo))
    }

    pub async fn delete_photo(&self, photo_id: Uuid) -> Result<ApiResponse<String>, ApiError> {
        Ok(ApiResponse::message("Photo deleted successfully".to_string()))
    }

    pub async fn extract_metadata(&self, image_data: &[u8]) -> Result<PhotoMetadata, ApiError> {
        let metadata = self.exif_service.extract_metadata(image_data)?;
        
        Ok(PhotoMetadata {
            latitude: metadata.latitude,
            longitude: metadata.longitude,
            taken_at: metadata.taken_at.and_then(|s| chrono::NaiveDateTime::parse_from_str(&s, "%Y:%m:%d %H:%M:%S").ok()),
        })
    }
}
