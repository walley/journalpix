use crate::database::connection::{
    get_opt_f64, get_opt_naive_datetime, get_opt_string, get_opt_uuid, get_timestamp, get_uuid,
    DatabaseConnection, PHOTO_COLS,
};
use crate::models::photo::{Photo, PhotoMetadata, PhotoResponse, UpdatePhotoRequest};
use crate::models::response::ApiResponse;
use crate::utils::error::ApiError;
use crate::utils::exif::ExifService;
use crate::utils::file::FileService;
use chrono::Utc;
use uuid::Uuid;
use std::sync::Arc;

use crate::services::place::map_photo_response;

#[derive(Debug, Clone)]
pub struct PhotoService {
    file_service: Arc<FileService>,
    exif_service: ExifService,
    db_pool: Arc<DatabaseConnection>,
}

fn db_err(e: rusqlite::Error) -> ApiError {
    ApiError::DatabaseError(e.to_string())
}

impl PhotoService {
    pub fn new(file_service: Arc<FileService>, db_pool: Arc<DatabaseConnection>) -> Self {
        Self {
            file_service,
            exif_service: ExifService::new(),
            db_pool,
        }
    }

    pub async fn upload_photo(
        &self,
        user_id: Uuid,
        place_id: Option<Uuid>,
        file_name: String,
        file_content: Vec<u8>,
    ) -> Result<ApiResponse<PhotoResponse>, ApiError> {
        let metadata = self.exif_service.extract_metadata(&file_content)?;

        let file_path = self.file_service.save_file(&file_name, &file_content)?;
        let thumbnail_name = format!("thumb_{}", file_name);
        let thumbnail_path = self.file_service.save_thumbnail(&thumbnail_name, &file_content)?;

        let photo = Photo {
            id: Uuid::new_v4(),
            place_id,
            user_id,
            file_path,
            thumbnail_path,
            latitude: metadata.latitude,
            longitude: metadata.longitude,
            taken_at: metadata
                .taken_at
                .and_then(|s| chrono::NaiveDateTime::parse_from_str(&s, "%Y:%m:%d %H:%M:%S").ok()),
            caption: None,
            order_index: 0,
            created_at: Utc::now(),
        };

        self.db_pool
            .with_conn(|c| {
                c.execute(
                    "INSERT INTO photos (id, place_id, user_id, file_path, thumbnail_path, latitude, longitude, taken_at, caption, order_index, created_at)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
                    rusqlite::params![
                        photo.id.to_string(),
                        photo.place_id.map(|p| p.to_string()),
                        photo.user_id.to_string(),
                        photo.file_path,
                        photo.thumbnail_path,
                        photo.latitude,
                        photo.longitude,
                        photo.taken_at.map(|d| d.format("%Y-%m-%dT%H:%M:%S").to_string()),
                        photo.caption,
                        photo.order_index,
                        photo.created_at.to_rfc3339(),
                    ],
                )
            })
            .map_err(db_err)?;

        Ok(ApiResponse::success(PhotoResponse {
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
        }))
    }

    pub async fn get_photos(
        &self,
        place_id: Option<Uuid>,
        user_id: Uuid,
    ) -> Result<ApiResponse<Vec<PhotoResponse>>, ApiError> {
        let photos = match place_id {
            Some(pid) => self
                .db_pool
                .with_conn(|c| {
                    let mut stmt =
                        c.prepare(&format!("SELECT {PHOTO_COLS} FROM photos WHERE place_id = ?1 ORDER BY order_index"))?;
                    let rows = stmt
                        .query_map([pid.to_string()], map_photo_response)?
                        .collect::<rusqlite::Result<Vec<_>>>()?;
                    Ok(rows)
                })
                .map_err(db_err)?,
            None => self
                .db_pool
                .with_conn(|c| {
                    let mut stmt =
                        c.prepare(&format!("SELECT {PHOTO_COLS} FROM photos WHERE user_id = ?1 ORDER BY created_at DESC"))?;
                    let rows = stmt
                        .query_map([user_id.to_string()], map_photo_response)?
                        .collect::<rusqlite::Result<Vec<_>>>()?;
                    Ok(rows)
                })
                .map_err(db_err)?,
        };
        Ok(ApiResponse::success(photos))
    }

    pub async fn find_photo(&self, photo_id: Uuid) -> Result<PhotoResponse, ApiError> {
        self.db_pool
            .with_conn(|c| {
                c.query_row(
                    &format!("SELECT {PHOTO_COLS} FROM photos WHERE id = ?1"),
                    [photo_id.to_string()],
                    map_photo_response,
                )
            })
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => ApiError::NotFound("Photo not found".into()),
                other => db_err(other),
            })
    }

    pub async fn get_photo(&self, photo_id: Uuid) -> Result<ApiResponse<PhotoResponse>, ApiError> {
        Ok(ApiResponse::success(self.find_photo(photo_id).await?))
    }

    pub async fn update_photo(
        &self,
        photo_id: Uuid,
        request: UpdatePhotoRequest,
    ) -> Result<ApiResponse<PhotoResponse>, ApiError> {
        self.find_photo(photo_id).await?;

        self.db_pool
            .with_conn(|c| {
                if request.place_id.is_some() {
                    c.execute(
                        "UPDATE photos SET place_id = ?1 WHERE id = ?2",
                        rusqlite::params![request.place_id.map(|p| p.to_string()), photo_id.to_string()],
                    )?;
                }
                if request.caption.is_some() {
                    c.execute(
                        "UPDATE photos SET caption = ?1 WHERE id = ?2",
                        rusqlite::params![request.caption, photo_id.to_string()],
                    )?;
                }
                if request.latitude.is_some() {
                    c.execute(
                        "UPDATE photos SET latitude = ?1 WHERE id = ?2",
                        rusqlite::params![request.latitude, photo_id.to_string()],
                    )?;
                }
                if request.longitude.is_some() {
                    c.execute(
                        "UPDATE photos SET longitude = ?1 WHERE id = ?2",
                        rusqlite::params![request.longitude, photo_id.to_string()],
                    )?;
                }
                if request.taken_at.is_some() {
                    c.execute(
                        "UPDATE photos SET taken_at = ?1 WHERE id = ?2",
                        rusqlite::params![request.taken_at.map(|d| d.format("%Y-%m-%dT%H:%M:%S").to_string()), photo_id.to_string()],
                    )?;
                }
                if let Some(oi) = request.order_index {
                    c.execute(
                        "UPDATE photos SET order_index = ?1 WHERE id = ?2",
                        rusqlite::params![oi, photo_id.to_string()],
                    )?;
                }
                Ok(())
            })
            .map_err(db_err)?;

        Ok(ApiResponse::success(self.find_photo(photo_id).await?))
    }

    pub async fn delete_photo(&self, photo_id: Uuid) -> Result<ApiResponse<String>, ApiError> {
        // Remove files first
        if let Ok(photo) = self.find_photo(photo_id).await {
            let _ = self.file_service.delete_file(&photo.file_path);
            let _ = self.file_service.delete_file(&photo.thumbnail_path);
        }
        let n = self
            .db_pool
            .with_conn(|c| c.execute("DELETE FROM photos WHERE id = ?1", [photo_id.to_string()]))
            .map_err(db_err)?;
        if n == 0 {
            return Err(ApiError::NotFound("Photo not found".into()));
        }
        Ok(ApiResponse::message("Photo deleted successfully".into()))
    }

    pub async fn extract_metadata(&self, image_data: &[u8]) -> Result<PhotoMetadata, ApiError> {
        let metadata = self.exif_service.extract_metadata(image_data)?;
        Ok(PhotoMetadata {
            latitude: metadata.latitude,
            longitude: metadata.longitude,
            taken_at: metadata
                .taken_at
                .and_then(|s| chrono::NaiveDateTime::parse_from_str(&s, "%Y:%m:%d %H:%M:%S").ok()),
        })
    }
}
