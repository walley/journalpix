use chrono::{Duration, Utc};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

use super::error::ApiError;

#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    pub sub: Uuid,
    pub email: String,
    pub exp: i64,
}

pub struct AuthService {
    secret: String,
    expiration_days: i64,
}

impl AuthService {
    pub fn new(secret: String, expiration_days: i64) -> Self {
        AuthService {
            secret,
            expiration_days,
        }
    }

    pub fn create_token(&self, user_id: Uuid, email: String) -> Result<String, ApiError> {
        let expiration = Utc::now() + Duration::days(self.expiration_days);
        
        let claims = Claims {
            sub: user_id,
            email,
            exp: expiration.timestamp(),
        };

        let encoding_key = EncodingKey::from_secret(self.secret.as_ref());
        
        encode(&Header::default(), &claims, &encoding_key)
            .map_err(|e| ApiError::AuthError(format!("Failed to create token: {}", e)))
    }

    pub fn validate_token(&self, token: &str) -> Result<Claims, ApiError> {
        let decoding_key = DecodingKey::from_secret(self.secret.as_ref());
        
        decode::<Claims>(token, &decoding_key, &Validation::default())
            .map(|data| data.claims)
            .map_err(|e| ApiError::AuthError(format!("Invalid token: {}", e)))
    }

    pub fn get_user_id_from_token(&self, token: &str) -> Result<Uuid, ApiError> {
        let claims = self.validate_token(token)?;
        Ok(claims.sub)
    }
}

pub fn hash_password(password: &str) -> Result<String, ApiError> {
    bcrypt::hash(password, bcrypt::DEFAULT_COST)
        .map_err(|e| ApiError::AuthError(format!("Failed to hash password: {}", e)))
}

pub fn verify_password(password: &str, hash: &str) -> Result<bool, ApiError> {
    bcrypt::verify(password, hash)
        .map_err(|e| ApiError::AuthError(format!("Failed to verify password: {}", e)))
}
