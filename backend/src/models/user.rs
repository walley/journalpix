use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

#[derive(Debug, Clone)]
pub struct User {
    pub id: Uuid,
    pub email: String,
    pub name: String,
    pub password_hash: String,
    /// IANA zone used as the default when a record carries none of its own.
    pub timezone: Option<String>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize)]
pub struct UserResponse {
    pub id: Uuid,
    pub email: String,
    pub name: String,
    /// IANA zone name, or `null` when the user has not set one.
    pub timezone: Option<String>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct CreateUserRequest {
    pub email: String,
    pub name: String,
    pub password: String,
    /// Optional; falls back to `UTC` when absent.
    pub timezone: Option<String>,
}

#[derive(Debug, Clone, Deserialize)]
pub struct LoginRequest {
    /// Login identifier: either the email **or** the account name. The
    /// field name stays `email` for old clients; `username` is accepted as
    /// an alias for the same value. Empty means "missing".
    #[serde(default)]
    pub email: Option<String>,
    #[serde(default)]
    pub username: Option<String>,
    pub password: String,
}

impl LoginRequest {
    /// The single identifier to resolve: `email` wins when both fields are
    /// set (older clients always populate it).
    pub fn identifier(&self) -> String {
        self.email
            .clone()
            .filter(|v| !v.trim().is_empty())
            .or_else(|| self.username.clone().filter(|v| !v.trim().is_empty()))
            .unwrap_or_default()
    }
}

#[derive(Debug, Clone, Serialize)]
pub struct LoginResponse {
    pub user: UserResponse,
    pub token: String,
}

#[derive(Debug, Clone, Deserialize)]
pub struct UpdateUserRequest {
    pub name: Option<String>,
    pub email: Option<String>,
    pub password: Option<String>,
    /// `null` clears the zone (back to the server default).
    pub timezone: Option<String>,
}
