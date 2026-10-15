use super::error::ApiError;

pub fn validate_email(email: &str) -> Result<(), ApiError> {
    if email.is_empty() {
        return Err(ApiError::ValidationError("Email is required".to_string()));
    }
    
    if !email.contains('@') || !email.contains('.') {
        return Err(ApiError::ValidationError("Invalid email format".to_string()));
    }
    
    Ok(())
}

pub fn validate_password(password: &str) -> Result<(), ApiError> {
    if password.is_empty() {
        return Err(ApiError::ValidationError("Password is required".to_string()));
    }
    
    if password.len() < 8 {
        return Err(ApiError::ValidationError("Password must be at least 8 characters".to_string()));
    }
    
    Ok(())
}

pub fn validate_name(name: &str) -> Result<(), ApiError> {
    if name.is_empty() {
        return Err(ApiError::ValidationError("Name is required".to_string()));
    }
    
    if name.len() > 100 {
        return Err(ApiError::ValidationError("Name must be less than 100 characters".to_string()));
    }
    
    Ok(())
}

pub fn validate_coordinates(lat: Option<f64>, lon: Option<f64>) -> Result<(), ApiError> {
    if let Some(lat) = lat {
        if lat < -90.0 || lat > 90.0 {
            return Err(ApiError::ValidationError("Latitude must be between -90 and 90".to_string()));
        }
    }
    
    if let Some(lon) = lon {
        if lon < -180.0 || lon > 180.0 {
            return Err(ApiError::ValidationError("Longitude must be between -180 and 180".to_string()));
        }
    }
    
    Ok(())
}
