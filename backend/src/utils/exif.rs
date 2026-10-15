// EXIF metadata extraction for geolocation
// Simplified version without external dependencies

use super::error::ApiError;

#[derive(Debug, Clone)]
pub struct ExifService;

impl ExifService {
    pub fn new() -> Self {
        ExifService
    }

    pub fn extract_metadata(&self, _image_data: &[u8]) -> Result<ExifMetadata, ApiError> {
        // For now, return default metadata
        // In production, you would parse EXIF data here
        // using a crate like `exif` or `kamadak-exif`
        Ok(ExifMetadata::default())
    }
}

#[derive(Debug, Default, Clone)]
pub struct ExifMetadata {
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<String>,
}

impl ExifMetadata {
    pub fn is_empty(&self) -> bool {
        self.latitude.is_none() && self.longitude.is_none() && self.taken_at.is_none()
    }
}
