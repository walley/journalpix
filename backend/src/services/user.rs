use crate::database::connection::{get_timestamp, get_uuid, DatabaseConnection};
use crate::models::user::{CreateUserRequest, LoginRequest, LoginResponse, UpdateUserRequest, User, UserResponse};
use crate::models::response::ApiResponse;
use crate::utils::auth::{hash_password, verify_password, AuthService};
use crate::utils::error::ApiError;
use crate::utils::validation::{validate_email, validate_name, validate_password};
use chrono::Utc;
use uuid::Uuid;
use std::sync::Arc;

#[derive(Debug, Clone)]
pub struct UserService {
    db_pool: Arc<DatabaseConnection>,
}

fn db_err(e: rusqlite::Error) -> ApiError {
    ApiError::DatabaseError(e.to_string())
}

impl UserService {
    pub fn new(db_pool: Arc<DatabaseConnection>) -> Self {
        UserService { db_pool }
    }

    pub async fn create_user(
        &self,
        request: CreateUserRequest,
        auth_service: &AuthService,
    ) -> Result<ApiResponse<LoginResponse>, ApiError> {
        validate_name(&request.name)?;
        validate_email(&request.email)?;
        validate_password(&request.password)?;

        let email = request.email.trim().to_lowercase();
        let user_id = Uuid::new_v4();
        let password_hash = hash_password(&request.password)?;
        let now = Utc::now().to_rfc3339();

        // Unique email check
        let exists = self
            .db_pool
            .with_conn(|c| {
                c.query_row(
                    "SELECT COUNT(*) FROM users WHERE email = ?1",
                    [&email],
                    |r| r.get::<_, i64>(0),
                )
            })
            .map_err(db_err)?;
        if exists > 0 {
            return Err(ApiError::ValidationError("Email already registered".into()));
        }

        self.db_pool
            .with_conn(|c| {
                c.execute(
                    "INSERT INTO users (id, email, name, password_hash, created_at, updated_at)
                     VALUES (?1, ?2, ?3, ?4, ?5, ?5)",
                    rusqlite::params![user_id.to_string(), email, request.name, password_hash, now],
                )
            })
            .map_err(db_err)?;

        let response = UserResponse {
            id: user_id,
            email,
            name: request.name,
            created_at: Utc::now(),
        };

        let token = auth_service.create_token(user_id, response.email.clone())?;
        Ok(ApiResponse::success(LoginResponse {
            user: response,
            token,
        }))
    }

    pub async fn login(
        &self,
        request: LoginRequest,
        auth_service: &AuthService,
    ) -> Result<ApiResponse<LoginResponse>, ApiError> {
        if request.email.is_empty() || request.password.is_empty() {
            return Err(ApiError::ValidationError("Email and password are required".into()));
        }

        let user = self
            .find_user_by_email(request.email.trim())
            .await?;

        let ok = verify_password(&request.password, &user.password_hash).map_err(db_err)?;
        if !ok {
            return Err(ApiError::Unauthorized("Invalid email or password".into()));
        }

        let response = UserResponse {
            id: user.id,
            email: user.email,
            name: user.name,
            created_at: user.created_at,
        };
        let token = auth_service.create_token(user.id, user.email.clone())?;
        Ok(ApiResponse::success(LoginResponse {
            user: response,
            token,
        }))
    }

    pub async fn find_user_by_email(&self, email: &str) -> Result<User, ApiError> {
        self.db_pool
            .with_conn(|c| {
                c.query_row(
                    "SELECT id, email, name, password_hash, created_at, updated_at
                     FROM users WHERE email = ?1",
                    [email.trim().to_lowercase()],
                    |r| {
                        Ok(User {
                            id: get_uuid(r, 0)?,
                            email: r.get(1)?,
                            name: r.get(2)?,
                            password_hash: r.get(3)?,
                            created_at: get_timestamp(r, 4)?,
                            updated_at: get_timestamp(r, 5)?,
                        })
                    },
                )
            })
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => {
                    ApiError::Unauthorized("Invalid email or password".into())
                }
                other => db_err(other),
            })
    }

    pub async fn get_user(&self, user_id: Uuid) -> Result<ApiResponse<UserResponse>, ApiError> {
        self.db_pool
            .with_conn(|c| {
                c.query_row(
                    "SELECT id, email, name, created_at FROM users WHERE id = ?1",
                    [user_id.to_string()],
                    |r| {
                        Ok(UserResponse {
                            id: get_uuid(r, 0)?,
                            email: r.get(1)?,
                            name: r.get(2)?,
                            created_at: get_timestamp(r, 3)?,
                        })
                    },
                )
            })
            .map_err(|e| match e {
                rusqlite::Error::QueryReturnedNoRows => ApiError::NotFound("User not found".into()),
                other => db_err(other),
            })
            .map(ApiResponse::success)
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

        let now = Utc::now().to_rfc3339();
        let id = user_id.to_string();

        self.db_pool
            .with_conn(|c| {
                if let Some(name) = &request.name {
                    c.execute("UPDATE users SET name = ?1, updated_at = ?2 WHERE id = ?3", rusqlite::params![name, now, id])?;
                }
                if let Some(email) = &request.email {
                    c.execute("UPDATE users SET email = ?1, updated_at = ?2 WHERE id = ?3", rusqlite::params![email.trim().to_lowercase(), now, id])?;
                }
                if let Some(password) = &request.password {
                    let hash = hash_password(password)?;
                    c.execute("UPDATE users SET password_hash = ?1, updated_at = ?2 WHERE id = ?3", rusqlite::params![hash, now, id])?;
                }
                Ok(())
            })
            .map_err(db_err)?;

        self.get_user(user_id).await
    }

    pub async fn delete_user(&self, user_id: Uuid) -> Result<ApiResponse<String>, ApiError> {
        let n = self
            .db_pool
            .with_conn(|c| c.execute("DELETE FROM users WHERE id = ?1", [user_id.to_string()]))
            .map_err(db_err)?;
        if n == 0 {
            return Err(ApiError::NotFound("User not found".into()));
        }
        Ok(ApiResponse::message("User deleted successfully".into()))
    }
}
