use crate::routes::APP_VERSION;
use actix_web::http::header::ContentType;
use actix_web::{get, HttpResponse, Responder};

const GITHUB_URL: &str = "https://github.com/walley/journalpix";
/// Public health URL: walley.org is Apache and `/journalpix` is reverse-proxied
/// to this backend's loopback listener.
const HEALTH_URL: &str = "https://walley.org/journalpix/health";

/// Simple HTML landing page for the service root. The API itself lives under
/// `/api`; this exists so hitting the base URL returns something useful
/// instead of a 404.
#[get("/")]
pub async fn index() -> impl Responder {
    let html = format!(
        r#"<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>JournalPix</title>
<style>
  :root {{ color-scheme: light dark; }}
  body {{
    font-family: system-ui, -apple-system, "Segoe UI", sans-serif;
    max-width: 42rem;
    margin: 10vh auto;
    padding: 0 1.5rem;
    line-height: 1.6;
  }}
  h1 {{ margin-bottom: 0.25rem; }}
  .tagline {{ margin-top: 0; opacity: 0.7; }}
  a {{ color: #0b6bcb; }}
  footer {{ margin-top: 2.5rem; font-size: 0.9rem; opacity: 0.7; }}
  code {{ font-size: 0.95em; }}
</style>
</head>
<body>
  <h1>JournalPix</h1>
  <p class="tagline">A travel diary with pictures and places.</p>
  <p>
    JournalPix is a travel journal and trip-planning backend. Record trips,
    add places and photos, and build a timeline of your travels.
  </p>
  <p>
    <a href="{GITHUB_URL}">JournalPix on GitHub</a>
  </p>
  <footer>
    Version {APP_VERSION} &middot; <a href="{HEALTH_URL}">Health check</a>
    &middot; API base: <code>/api</code>
  </footer>
</body>
</html>
"#
    );

    HttpResponse::Ok()
        .content_type(ContentType::html())
        .body(html)
}

#[cfg(test)]
mod tests {
    use super::*;
    use actix_web::{test, App};

    #[actix_rt::test]
    async fn root_returns_html_about_page() {
        let app = test::init_service(App::new().service(index)).await;
        let req = test::TestRequest::get().uri("/").to_request();
        let res = test::call_service(&app, req).await;

        assert!(res.status().is_success());
        let content_type = res
            .headers()
            .get(actix_web::http::header::CONTENT_TYPE)
            .and_then(|value| value.to_str().ok())
            .unwrap_or_default();
        assert!(content_type.starts_with("text/html"));

        let body = test::read_body(res).await;
        let body = String::from_utf8_lossy(&body);
        assert!(body.contains("JournalPix"));
        assert!(body.contains(GITHUB_URL));
        assert!(body.contains(HEALTH_URL));
        assert!(body.contains(APP_VERSION));
    }
}
