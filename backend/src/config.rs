// Configuration for JournalPix.
//
// Priority:  built-in defaults  <  INI config file  <  environment variables.
//
// Config file is looked up in this order (first found wins):
//   1. --config <path> CLI argument
//   2. $CONFIG_PATH
//   3. ./journalpix.conf           (development, run from repo)
//   4. /usr/local/etc/journalpix.conf   (make install default PREFIX)
//   5. /etc/journalpix.conf
//
// INI format (sections + `key = value`, `#` or `;` comments):
//
//   [server]
//   port = 8080
//   [database]
//   type = sqlite          # sqlite | postgres (postgres not implemented yet)
//   url  = sqlite:journalpix.db
//   [auth]
//   jwt_secret = change-me          # signing key for login tokens — see below
//   jwt_expiration_days = 30
//   [storage]
//   upload_dir = ./uploads
//   thumbnail_dir = ./thumbnails
//   [cors]
//   origins = http://localhost:3000,http://localhost:8080

use std::collections::BTreeMap;
use std::env;
use std::path::PathBuf;

#[derive(Debug, Clone, PartialEq)]
pub enum DatabaseType {
    Postgres,
    Sqlite,
}

impl DatabaseType {
    pub fn parse(s: &str) -> Self {
        match s.trim().to_lowercase().as_str() {
            "sqlite" => DatabaseType::Sqlite,
            _ => DatabaseType::Postgres,
        }
    }

    pub fn name(&self) -> &'static str {
        match self {
            DatabaseType::Postgres => "PostgreSQL",
            DatabaseType::Sqlite => "SQLite",
        }
    }
}

#[derive(Debug, Clone)]
pub struct Config {
    pub database_url: String,
    pub database_type: DatabaseType,
    /// jwt_secret: secret key used to SIGN login tokens (JWTs). It lets the
    /// server verify "Authorization: Bearer <token>" without storing
    /// sessions. Anyone who knows it can forge a token for ANY user, so
    /// never ship a known default: when this field is EMPTY (unset in the
    /// config), main.rs generates a random ephemeral secret at startup.
    /// Consequence: logins don't survive restarts (users re-login) — set a
    /// fixed value in the config for persistent sessions. Rotating the
    /// secret only invalidates tokens; passwords use bcrypt and are
    /// unaffected.
    pub jwt_secret: String,
    pub jwt_expiration_days: i64,
    pub upload_dir: String,
    pub thumbnail_dir: String,
    pub server_port: u16,
    pub cors_origins: Vec<String>,
    /// Where the loaded config came from (None = defaults only).
    pub config_path: Option<PathBuf>,
}

pub const DEFAULT_CONFIG_NAME: &str = "journalpix.conf";
pub const SYSTEM_CONFIG_DIRS: [&str; 2] = ["/usr/local/etc", "/etc"];

impl Config {
    /// Load config: defaults <- INI file <- environment.
    pub fn load(cli_path: Option<&str>) -> Self {
        let path = cli_path
            .map(PathBuf::from)
            .or_else(|| env::var("CONFIG_PATH").ok().map(PathBuf::from))
            .or_else(|| Self::find_config_file());

        let ini = path.as_ref().and_then(|p| parse_ini_file(p));
        let mut cfg = Self::from_ini(ini);
        if let Some(p) = &path {
            cfg.config_path = Some(p.clone());
        }
        cfg.apply_env_overrides();
        cfg
    }

    /// Old entry point kept for compatibility (env-only).
    pub fn from_env() -> Self {
        Self::load(None)
    }

    fn find_config_file() -> Option<PathBuf> {
        let local = PathBuf::from(DEFAULT_CONFIG_NAME);
        if local.is_file() {
            return Some(local);
        }
        for dir in SYSTEM_CONFIG_DIRS {
            let p = PathBuf::from(dir).join(DEFAULT_CONFIG_NAME);
            if p.is_file() {
                return Some(p);
            }
        }
        None
    }

    fn from_ini(ini: Option<BTreeMap<String, BTreeMap<String, String>>>) -> Self {
        let mut cfg = Self::default_config();
        let Some(ini) = ini else { return cfg };

        let get = |section: &str, key: &str| -> Option<String> {
            ini.get(section)
                .and_then(|s| s.get(key))
                .map(|v| v.trim().to_string())
                .filter(|v| !v.is_empty())
        };

        if let Some(v) = get("server", "port") {
            if let Ok(p) = v.parse() {
                cfg.server_port = p;
            }
        }
        if let Some(v) = get("database", "type") {
            cfg.database_type = DatabaseType::parse(&v);
        }
        if let Some(v) = get("database", "url") {
            cfg.database_url = v;
        }
        if let Some(v) = get("auth", "jwt_secret") {
            cfg.jwt_secret = v;
        }
        if let Some(v) = get("auth", "jwt_expiration_days") {
            if let Ok(d) = v.parse() {
                cfg.jwt_expiration_days = d;
            }
        }
        if let Some(v) = get("storage", "upload_dir") {
            cfg.upload_dir = v;
        }
        if let Some(v) = get("storage", "thumbnail_dir") {
            cfg.thumbnail_dir = v;
        }
        if let Some(v) = get("cors", "origins") {
            cfg.cors_origins = v
                .split(',')
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .collect();
        }
        cfg
    }

    /// Sane defaults — server runs with these even with no config file.
    fn default_config() -> Self {
        Config {
            database_type: DatabaseType::Sqlite,
            database_url: "sqlite:journalpix.db".to_string(),
            jwt_secret: String::new(), // empty = generate random ephemeral at startup
            jwt_expiration_days: 30,
            upload_dir: "./uploads".to_string(),
            thumbnail_dir: "./thumbnails".to_string(),
            server_port: 8080,
            cors_origins: vec![
                "http://localhost:3000".to_string(),
                "http://localhost:8080".to_string(),
            ],
            config_path: None,
        }
    }

    /// Environment variables still override everything (backward compat).
    fn apply_env_overrides(&mut self) {
        if let Ok(v) = env::var("DATABASE_URL") {
            self.database_url = v;
        }
        if let Ok(v) = env::var("DATABASE_TYPE") {
            self.database_type = DatabaseType::parse(&v);
        }
        if let Ok(v) = env::var("JWT_SECRET") {
            self.jwt_secret = v;
        }
        if let Ok(v) = env::var("JWT_EXPIRATION_DAYS") {
            if let Ok(d) = v.parse() {
                self.jwt_expiration_days = d;
            }
        }
        if let Ok(v) = env::var("UPLOAD_DIR") {
            self.upload_dir = v;
        }
        if let Ok(v) = env::var("THUMBNAIL_DIR") {
            self.thumbnail_dir = v;
        }
        if let Ok(v) = env::var("SERVER_PORT") {
            if let Ok(p) = v.parse() {
                self.server_port = p;
            }
        }
        if let Ok(v) = env::var("CORS_ORIGINS") {
            self.cors_origins = v
                .split(',')
                .map(|s| s.trim().to_string())
                .filter(|s| !s.is_empty())
                .collect();
        }
    }
    /// True when no secret is configured — main.rs must generate a random
    /// ephemeral one (logins won't survive a restart).
    pub fn jwt_secret_is_ephemeral(&self) -> bool {
        self.jwt_secret.trim().is_empty()
    }
}

impl Default for Config {
    fn default() -> Self {
        Self::default_config()
    }
}

// ---------------------------------------------------------------------------
// Minimal INI parser: [section] headers, `key = value` lines, # and ; comments.

fn parse_ini_file(path: &PathBuf) -> Option<BTreeMap<String, BTreeMap<String, String>>> {
    let content = std::fs::read_to_string(path).ok()?;
    Some(parse_ini(&content))
}

pub fn parse_ini(content: &str) -> BTreeMap<String, BTreeMap<String, String>> {
    let mut map: BTreeMap<String, BTreeMap<String, String>> = BTreeMap::new();
    let mut section = String::new();
    for line in content.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') || line.starts_with(';') {
            continue;
        }
        if let Some(name) = line.strip_prefix('[').and_then(|l| l.strip_suffix(']')) {
            section = name.trim().to_lowercase();
            map.entry(section.clone()).or_default();
            continue;
        }
        if let Some((key, value)) = line.split_once('=') {
            let key = key.trim().to_lowercase();
            // Strip trailing inline comment unless the value is quoted.
            let mut value = value.trim().to_string();
            if !(value.starts_with('"') && value.ends_with('"') && value.len() >= 2) {
                if let Some(pos) = value.find('#').or_else(|| value.find(';')) {
                    value = value[..pos].trim().to_string();
                }
            } else {
                value = value[1..value.len() - 1].to_string();
            }
            if !key.is_empty() {
                map.entry(section.clone()).or_default().insert(key, value);
            }
        }
    }
    map
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parse_ini_basic() {
        let ini = parse_ini(
            "# comment\n[server]\nport = 9090   ; trailing comment\n\n[database]\ntype = sqlite\n",
        );
        assert_eq!(ini["server"]["port"], "9090");
        assert_eq!(ini["database"]["type"], "sqlite");
    }

    #[test]
    fn parse_ini_quoted_value_keeps_hash() {
        let ini = parse_ini("[auth]\njwt_secret = \"abc#123\"\n");
        assert_eq!(ini["auth"]["jwt_secret"], "abc#123");
    }

    #[test]
    fn config_defaults_sane() {
        let cfg = Config::default();
        assert_eq!(cfg.server_port, 8080);
        assert_eq!(cfg.database_type, DatabaseType::Sqlite);
        assert!(cfg.database_url.starts_with("sqlite:"));
    }

    #[test]
    fn config_from_ini_overrides() {
        let ini = parse_ini(
            "[server]\nport = 9999\n[database]\nurl = /tmp/x.db\n[auth]\njwt_secret = s3cret\n",
        );
        let cfg = Config::from_ini(Some(ini));
        assert_eq!(cfg.server_port, 9999);
        assert_eq!(cfg.database_url, "/tmp/x.db");
        assert_eq!(cfg.jwt_secret, "s3cret");
    }
}
