use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use uuid::Uuid;

use super::error::ApiError;

#[derive(Debug, Clone)]
pub struct FileService {
    upload_dir: PathBuf,
    thumbnail_dir: PathBuf,
}

impl FileService {
    pub fn new(upload_dir: &str, thumbnail_dir: &str) -> Result<Self, ApiError> {
        let upload_path = Path::new(upload_dir);
        let thumbnail_path = Path::new(thumbnail_dir);
        
        if !upload_path.exists() {
            fs::create_dir_all(upload_path)?;
        }
        
        if !thumbnail_path.exists() {
            fs::create_dir_all(thumbnail_path)?;
        }
        
        Ok(Self {
            upload_dir: upload_path.to_path_buf(),
            thumbnail_dir: thumbnail_path.to_path_buf(),
        })
    }

    pub fn save_file(&self, file_name: &str, content: &[u8]) -> Result<String, ApiError> {
        let file_path = self.upload_dir.join(file_name);
        let mut file = File::create(&file_path)?;
        file.write_all(content)?;
        
        Ok(file_path.to_string_lossy().to_string())
    }

    pub fn save_thumbnail(&self, file_name: &str, content: &[u8]) -> Result<String, ApiError> {
        let file_path = self.thumbnail_dir.join(file_name);
        let mut file = File::create(&file_path)?;
        file.write_all(content)?;
        
        Ok(file_path.to_string_lossy().to_string())
    }

    pub fn generate_file_name(&self, user_id: Uuid, extension: &str) -> String {
        let uuid = Uuid::new_v4();
        format!("{}_{}.{}", user_id, uuid, extension)
    }

    pub fn delete_file(&self, file_path: &str) -> Result<(), ApiError> {
        if Path::new(file_path).exists() {
            fs::remove_file(file_path)?;
        }
        Ok(())
    }

    pub fn read_file(&self, file_path: &str) -> Result<Vec<u8>, ApiError> {
        let mut file = File::open(file_path)?;
        let mut buffer = Vec::new();
        file.read_to_end(&mut buffer)?;
        Ok(buffer)
    }
}

pub fn get_file_extension(content_type: &str) -> String {
    match content_type {
        "image/jpeg" | "image/jpg" => "jpg".to_string(),
        "image/png" => "png".to_string(),
        "image/gif" => "gif".to_string(),
        "image/webp" => "webp".to_string(),
        _ => "bin".to_string(),
    }
}
