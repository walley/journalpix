use actix_cors::Cors;
use actix_web::{web, App, HttpServer};
use dotenv::dotenv;
use std::env;

mod config;
mod middleware;
mod models;
mod routes;
mod services;
mod utils;

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    dotenv().ok();
    
    env_logger::init();
    
    let config = config::Config::from_env();
    
    println!("Starting JournalPix server on port {}", config.server_port);
    
    HttpServer::new(move || {
        let cors = Cors::default()
            .allow_any_origin()
            .allow_any_method()
            .allow_any_header()
            .max_age(3600);
        
        App::new()
            .wrap(cors)
            .app_data(web::JsonConfig::default().error_handler(|err, _req| {
                log::error!("JSON error: {:?}", err);
                actix_web::error::ErrorBadRequest("Invalid JSON")
            }))
            .app_data(web::PayloadConfig::new(1024 * 1024 * 50)) // 50MB max upload
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
