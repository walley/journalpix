pub mod user;
pub mod trip;
pub mod place;
pub mod photo;
pub mod timeline;

pub use user::*;
pub use trip::*;
pub use place::*;
pub use photo::*;
pub use timeline::*;

use std::sync::Arc;
use crate::database::connection::DatabaseConnection;

pub fn create_services(db_pool: Arc<DatabaseConnection>) -> Services {
    Services {
        user: UserService::new(db_pool.clone()),
        trip: TripService::new(db_pool.clone()),
        place: PlaceService::new(db_pool.clone()),
        photo: PhotoService::new("./uploads", "./thumbnails", db_pool.clone()).unwrap(),
        timeline: TimelineService::new(db_pool),
    }
}

#[derive(Debug, Clone)]
pub struct Services {
    pub user: UserService,
    pub trip: TripService,
    pub place: PlaceService,
    pub photo: PhotoService,
    pub timeline: TimelineService,
}
