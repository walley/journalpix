use actix_web::{dev::ServiceRequest, Error, HttpMessage};
use actix_web_httpauth::extractors::bearer::BearerAuth;
use crate::utils::auth::AuthService;
use crate::utils::error::ApiError;
use actix_web::web;

pub async fn auth_middleware(
    req: ServiceRequest,
    credentials: BearerAuth,
    auth_service: web::Data<AuthService>,
) -> Result<ServiceRequest, Error> {
    let token = credentials.token();
    
    match auth_service.validate_token(token) {
        Ok(claims) => {
            req.extensions_mut().insert(claims);
            Ok(req)
        }
        Err(e) => Err(ApiError::Unauthorized(e.to_string()).into()),
    }
}
