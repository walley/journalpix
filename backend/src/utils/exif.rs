use exif::Exif;
use std::io::Cursor;

use super::error::ApiError;

pub struct ExifService;

impl ExifService {
    pub fn new() -> Self {
        ExifService
    }

    pub fn extract_metadata(&self, image_data: &[u8]) -> Result<ExifMetadata, ApiError> {
        let cursor = Cursor::new(image_data);
        let exif_reader = exif::Reader::new();
        
        match exif_reader.read_from_container(&mut exif::Jpeg::new(cursor)) {
            Ok(exif) => {
                let mut metadata = ExifMetadata::default();
                
                if let Some(field) = exif.get_field(exif::Tag::GPSLatitude, exif::In::PRIMARY) {
                    if let Some(value) = field.display_value() {
                        metadata.latitude = parse_gps_coordinate(&value.to_string());
                    }
                }
                
                if let Some(field) = exif.get_field(exif::Tag::GPSLongitude, exif::In::PRIMARY) {
                    if let Some(value) = field.display_value() {
                        metadata.longitude = parse_gps_coordinate(&value.to_string());
                    }
                }
                
                if let Some(field) = exif.get_field(exif::Tag::DateTime, exif::In::PRIMARY) {
                    if let Some(value) = field.display_value() {
                        metadata.taken_at = parse_date_time(&value.to_string());
                    }
                }
                
                if let Some(field) = exif.get_field(exif::Tag::DateTimeOriginal, exif::In::PRIMARY) {
                    if let Some(value) = field.display_value() {
                        metadata.taken_at = parse_date_time(&value.to_string());
                    }
                }
                
                Ok(metadata)
            }
            Err(_) => {
                // Try PNG
                let cursor = Cursor::new(image_data);
                match exif_reader.read_from_container(&mut exif::Png::new(cursor)) {
                    Ok(exif) => {
                        let mut metadata = ExifMetadata::default();
                        
                        if let Some(field) = exif.get_field(exif::Tag::GPSLatitude, exif::In::PRIMARY) {
                            if let Some(value) = field.display_value() {
                                metadata.latitude = parse_gps_coordinate(&value.to_string());
                            }
                        }
                        
                        if let Some(field) = exif.get_field(exif::Tag::GPSLongitude, exif::In::PRIMARY) {
                            if let Some(value) = field.display_value() {
                                metadata.longitude = parse_gps_coordinate(&value.to_string());
                            }
                        }
                        
                        if let Some(field) = exif.get_field(exif::Tag::DateTime, exif::In::PRIMARY) {
                            if let Some(value) = field.display_value() {
                                metadata.taken_at = parse_date_time(&value.to_string());
                            }
                        }
                        
                        Ok(metadata)
                    }
                    Err(_) => Ok(ExifMetadata::default()),
                }
            }
        }
    }
}

#[derive(Debug, Default, Clone)]
pub struct ExifMetadata {
    pub latitude: Option<f64>,
    pub longitude: Option<f64>,
    pub taken_at: Option<String>,
}

fn parse_gps_coordinate(coord: &str) -> Option<f64> {
    // Parse GPS coordinate in format: "55/1, 41/1, 33000/1000" or similar
    let parts: Vec<&str> = coord.split(',').map(|s| s.trim()).collect();
    
    if parts.len() >= 2 {
        let degrees: f64 = parts[0].parse().ok()?;
        let minutes: f64 = parts[1].parse().ok()?;
        let seconds: f64 = parts.get(2).and_then(|s| s.parse().ok()).unwrap_or(0.0);
        
        Some(degrees + minutes / 60.0 + seconds / 3600.0)
    } else {
        None
    }
}

fn parse_date_time(date_str: &str) -> Option<String> {
    // Parse date in format: "2024:01:15 12:30:45"
    let cleaned = date_str.replace('"', "").trim().to_string();
    if cleaned.is_empty() {
        None
    } else {
        Some(cleaned)
    }
}

impl ExifMetadata {
    pub fn is_empty(&self) -> bool {
        self.latitude.is_none() && self.longitude.is_none() && self.taken_at.is_none()
    }
}
