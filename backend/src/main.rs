use actix_cors::Cors;
use actix_web::{web, App, HttpServer};
use dotenv::dotenv;
use std::sync::Arc;

mod config;
mod database;
mod middleware;
mod models;
mod routes;
mod services;
mod utils;

use config::Config;
use database::connection::create_db_pool;

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    dotenv().ok();
    
    env_logger::init();
    
    let config = Config::from_env();
    
    // Create database connection pool
    let db_pool = match create_db_pool(&config).await {
        Ok(pool) => {
            log::info!("Connected to {} database", 
                match config.database_type {
                    config::DatabaseType::Postgres => "PostgreSQL",
                    config::DatabaseType::Sqlite => "SQLite",
                });
            Arc::new(pool)
        }
        Err(e) => {
            log::error!("Failed to connect to database: {}", e);
            panic!("Database connection failed: {}", e);
        }
    };
    
    println!("Starting JournalPix server on port {}", config.server_port);
    println!("Using {} database", 
        match config.database_type {
            config::DatabaseType::Postgres => "PostgreSQL",
            config::DatabaseType::Sqlite => "SQLite",
        });
    
    let config_clone = config.clone();
    let db_pool_clone = db_pool.clone();
    
    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header()
            .max_age(3600);
        
        let config = config_clone.clone();
        let db_pool = db_pool_clone.clone();
        
        App::new()
            .wrap(cors)
            .app_data(web::JsonConfig::default().error_handler(|err, _req| {
                log::error!("JSON error: {:?}", err);
                actix_web::error::ErrorBadRequest("Invalid JSON")
            }))
            .app_data(web::PayloadConfig::new(1024 * 1024 * 50)) // 50MB max upload
            .app_data(web::Data::new(db_pool))
            .app_data(web::Data::new(config))
            .service(routes::health::health_check)
            .service(
                web::scope("/api")
                    .configure(routes::auth::configure)
                    .configure(routes::trips::configure)
                    .configure(routes::places::configure)
                    .configure(routes::photos::configure)
                    .configure(routes::timeline::configure)
            )
    })
    .bind(format!("0.0.0.0:{}", config.server_port))?
    .run()
    .await
}
