use std::env;

#[derive(Debug, Clone)]
pub struct Config {
    pub database_url: String,
    pub jwt_secret: String,
    pub jwt_expiration_days: i64,
    pub upload_dir: String,
    pub thumbnail_dir: String,
    pub server_port: u16,
    pub cors_origins: Vec<String>,
}

impl Config {
    pub fn from_env() -> Self {
        Config {
            database_url: env::var("DATABASE_URL")
                .unwrap_or_else(|_| "postgres://localhost:5432/journalpix".to_string()),
            jwt_secret: env::var("JWT_SECRET")
                .unwrap_or_else(|_| "default_secret_change_in_production".to_string()),
            jwt_expiration_days: env::var("JWT_EXPIRATION_DAYS")
                .map(|s| s.parse().unwrap_or(30))
                .unwrap_or(30),
            upload_dir: env::var("UPLOAD_DIR")
                .unwrap_or_else(|_| "./uploads".to_string()),
            thumbnail_dir: env::var("THUMBNAIL_DIR")
                .unwrap_or_else(|_| "./thumbnails".to_string()),
            server_port: env::var("SERVER_PORT")
                .map(|s| s.parse().unwrap_or(8080))
                .unwrap_or(8080),
            cors_origins: env::var("CORS_ORIGINS")
                .map(|s| s.split(',').map(|s| s.trim().to_string()).collect())
                .unwrap_or(vec!["http://localhost:3000".to_string()]),
        }
    }
}

impl Default for Config {
    fn default() -> Self {
        Self::from_env()
    }
}
