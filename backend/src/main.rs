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
use utils::auth::AuthService;
use utils::file::FileService;

/// The HTTP listener is intentionally hard-coded to loopback. The service
/// must never be reachable from other interfaces; use a reverse proxy on the
/// same host if external access is ever needed.
const BIND_ADDR: &str = "127.0.0.1";

/// Random secret (2× UUIDv4 = 64 hex chars, from the OS RNG) used when
/// none is configured. It is never written anywhere — tokens signed with
/// it simply stop validating on the next restart.
fn generate_ephemeral_jwt_secret() -> String {
    format!(
        "{}{}",
        uuid::Uuid::new_v4().simple(),
        uuid::Uuid::new_v4().simple()
    )
}

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    dotenv().ok();
    env_logger::init();

    // --config <path> (optional). Config: defaults <- INI file <- env vars.
    let args: Vec<String> = std::env::args().collect();
    let cli_config = args
        .iter()
        .position(|a| a == "--config")
        .and_then(|i| args.get(i + 1))
        .map(|s| s.as_str());

    let config = Config::load(cli_config);
    if let Some(p) = &config.config_path {
        println!("Using config file: {}", p.display());
    } else {
        println!("No config file found, using built-in defaults");
    }

    // Create database connection pool
    let db_pool = match create_db_pool(&config).await {
        Ok(pool) => {
            log::info!(
                "Connected to {} database",
                config.database_type.name()
            );
            Arc::new(pool)
        }
        Err(e) => {
            log::error!("Failed to connect to database: {}", e);
            panic!("Database connection failed: {}", e);
        }
    };

    // File storage (uploads/thumbnails)
    let file_service = Arc::new(
        FileService::new(&config.upload_dir, &config.thumbnail_dir)
            .expect("Failed to create upload/thumbnail directories"),
    );

    // JWT secret: signs login tokens. If none is configured we generate a
    // random ephemeral one at startup — safe (never a known default), but
    // logins reset on every restart. Configure [auth] jwt_secret for
    // persistent sessions. Rotating the secret never loses accounts or
    // data; users just log in again.
    let mut config = config;
    if config.jwt_secret_is_ephemeral() {
        config.jwt_secret = generate_ephemeral_jwt_secret();
        log::info!("No [auth] jwt_secret configured: generated a random ephemeral JWT secret.");
        println!("NOTE: jwt_secret not configured — using a random secret for this run.");
        println!("      Logins will NOT survive a restart; users just log in again.");
        println!("      Set a fixed [auth] jwt_secret in the config file for persistent sessions.");
    }

    let auth_service = web::Data::new(AuthService::new(
        config.jwt_secret.clone(),
        config.jwt_expiration_days,
    ));

    println!(
        "Starting JournalPix server on http://{}:{}",
        BIND_ADDR, config.server_port
    );
    println!(
        "Using {} database ({})",
        config.database_type.name(),
        config.database_url
    );

    let config_clone = config.clone();
    let db_pool_clone = db_pool.clone();
    let file_service_clone = file_service.clone();

    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header()
            .max_age(3600);

        let config = config_clone.clone();
        let db_pool = db_pool_clone.clone();
        let file_service = file_service_clone.clone();

        App::new()
            .wrap(cors)
            .app_data(web::JsonConfig::default().error_handler(|err, _req| {
                log::error!("JSON error: {:?}", err);
                actix_web::error::ErrorBadRequest("Invalid JSON")
            }))
            .app_data(web::PayloadConfig::new(1024 * 1024 * 50)) // 50MB max upload
            .app_data(web::Data::new(db_pool))
            .app_data(web::Data::new(file_service))
            .app_data(auth_service.clone())
            .service(actix_files::Files::new("/uploads", &config.upload_dir))
            .service(actix_files::Files::new("/thumbnails", &config.thumbnail_dir))
            .app_data(web::Data::new(config))
            .service(routes::health::health_check)
            .service(
                web::scope("/api")
                    // NOTE: order matters — more specific scopes must be
                    // registered before the broad "/trips" scope, otherwise
                    // actix matches the prefix and 404s without falling through.
                    .configure(routes::auth::configure)
                    .configure(routes::places::configure)
                    .configure(routes::photos::configure)
                    .configure(routes::timeline::configure)
                    .configure(routes::trips::configure),
            )
    })
    .bind(format!("{}:{}", BIND_ADDR, config.server_port))?
    .run()
    .await
}
