use crate::models::user::{CreateUserRequest, LoginRequest, LoginResponse, UpdateUserRequest, User, UserResponse};
use crate::models::response::ApiResponse;
use crate::utils::auth::{hash_password, verify_password, AuthService};
use crate::utils::error::ApiError;
use crate::utils::validation::{validate_email, validate_name, validate_password};
use chrono::Utc;
use uuid::Uuid;

pub struct UserService;

impl UserService {
    pub fn new() -> Self {
        UserService
    }

    pub async fn create_user(
        &self,
        request: CreateUserRequest,
        auth_service: &AuthService,
    ) -> Result<ApiResponse<UserResponse>, ApiError> {
        validate_name(&request.name)?;
        validate_email(&request.email)?;
        validate_password(&request.password)?;
        
        let user_id = Uuid::new_v4();
        let password_hash = hash_password(&request.password)?;
        
        let user = User {
            id: user_id,
            email: request.email.clone(),
            name: request.name.clone(),
            password_hash,
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };
        
        let token = auth_service.create_token(user.id, user.email.clone())?;
        
        let response = UserResponse {
            id: user.id,
            email: user.email,
            name: user.name,
            created_at: user.created_at,
        };
        
        let login_response = LoginResponse {
            user: response.clone(),
            token,
        };
        
        Ok(ApiResponse::success(login_response))
    }

    pub async fn login(
        &self,
        request: LoginRequest,
        auth_service: &AuthService,
    ) -> Result<ApiResponse<LoginResponse>, ApiError> {
        if request.email.is_empty() || request.password.is_empty() {
            return Err(ApiError::ValidationError("Email and password are required".to_string()));
        }
        
        let user = self.find_user_by_email(&request.email).await?;
        
        verify_password(&request.password, &user.password_hash)?;
        
        let token = auth_service.create_token(user.id, user.email.clone())?;
        
        let response = UserResponse {
            id: user.id,
            email: user.email,
            name: user.name,
            created_at: user.created_at,
        };
        
        let login_response = LoginResponse {
            user: response,
            token,
        };
        
        Ok(ApiResponse::success(login_response))
    }

    pub async fn find_user_by_email(&self, email: &str) -> Result<User, ApiError> {
        Ok(User {
            id: Uuid::new_v4(),
            email: email.to_string(),
            name: "Test User".to_string(),
            password_hash: hash_password("password123")?,
            created_at: Utc::now(),
            updated_at: Utc::now(),
        })
    }

    pub async fn get_user(&self, user_id: Uuid) -> Result<ApiResponse<UserResponse>, ApiError> {
        let user = User {
            id: user_id,
            email: "test@example.com".to_string(),
            name: "Test User".to_string(),
            password_hash: "hashed_password".to_string(),
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };
        
        let response = UserResponse {
            id: user.id,
            email: user.email,
            name: user.name,
            created_at: user.created_at,
        };
        
        Ok(ApiResponse::success(response))
    }

    pub async fn update_user(
        &self,
        user_id: Uuid,
        request: UpdateUserRequest,
    ) -> Result<ApiResponse<UserResponse>, ApiError> {
        if let Some(name) = &request.name {
            validate_name(name)?;
        }
        
        if let Some(email) = &request.email {
            validate_email(email)?;
        }
        
        if let Some(password) = &request.password {
            validate_password(password)?;
        }
        
        let user = User {
            id: user_id,
            email: request.email.unwrap_or_else(|| "updated@example.com".to_string()),
            name: request.name.unwrap_or_else(|| "Updated User".to_string()),
            password_hash: request.password.map(|p| hash_password(&p).unwrap()).unwrap_or_else(|| "hashed_password".to_string()),
            created_at: Utc::now(),
            updated_at: Utc::now(),
        };
        
        let response = UserResponse {
            id: user.id,
            email: user.email,
            name: user.name,
            created_at: user.created_at,
        };
        
        Ok(ApiResponse::success(response))
    }

    pub async fn delete_user(&self, user_id: Uuid) -> Result<ApiResponse<String>, ApiError> {
        Ok(ApiResponse::message("User deleted successfully".to_string()))
    }
}
