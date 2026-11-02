// Timezone-aware timestamps.
//
// Every moment in JournalPix is stored canonically as a UTC instant plus the
// IANA timezone that was in effect where it happened. Travelers cross zones
// constantly, so the *zone* is part of the data, not a display preference:
// a photo shot in Tokyo must keep Tokyo wall-clock time even after the user
// has landed in Berlin.
//
// Storage (SQLite TEXT):
//   * the instant  -> RFC3339 in UTC, e.g. `2026-07-04T08:30:00+00:00`
//   * the zone     -> IANA name,      e.g. `Europe/Rome`
//
// JSON representation of a stored moment (`ZonedTime`):
//   { "utc": "2026-07-04T08:30:00Z",
//     "timezone": "Europe/Rome",          <- where it happened
//     "local": "2026-07-04T10:30:00" }    <- wall clock in `timezone`
//
// A request may override the rendering zone (query `?tz=` or the
// `X-Timezone` header); when that happens `local` is rendered in the
// requested zone and `local_timezone` names it, so the two can never be
// confused.

use crate::utils::error::ApiError;
use chrono::{DateTime, NaiveDateTime, TimeZone, Utc};
use chrono_tz::Tz;
use serde::{Deserialize, Serialize};
use std::str::FromStr;

/// Zone used when nothing better is known.
pub const UTC: &str = "UTC";

/// Parse an IANA timezone name. Returns `None` for anything unknown.
pub fn parse_tz(name: &str) -> Option<Tz> {
    Tz::from_str(name.trim()).ok()
}

/// Resolve a zone name, falling back to `UTC` when missing or unknown.
/// Callers that must reject bad input use [`parse_tz`] directly.
pub fn tz_or_utc(name: Option<&str>) -> Tz {
    name.and_then(parse_tz).unwrap_or_else(|| parse_tz(UTC).unwrap())
}

// ---------------------------------------------------------------------------
// Stored / returned form

/// A moment in time: the instant, plus the zone it happened in.
#[derive(Debug, Clone, PartialEq)]
pub struct ZonedTime {
    /// The instant, always UTC.
    pub utc: DateTime<Utc>,
    /// IANA zone that was in effect where it happened.
    pub timezone: Tz,
    /// Display override (`?tz=` / `X-Timezone`). `None` renders in
    /// `timezone`.
    render_in: Option<Tz>,
}

impl ZonedTime {
    pub fn new(utc: DateTime<Utc>, timezone: Tz) -> Self {
        Self {
            utc,
            timezone,
            render_in: None,
        }
    }

    /// The wall-clock date/time in the effective rendering zone.
    pub fn local(&self) -> NaiveDateTime {
        self.utc.with_timezone(&self.effective_tz()).naive_local()
    }

    /// The wall-clock **date** in the effective rendering zone. This is what
    /// the timeline groups on — grouping by UTC date would file a 01:00
    /// Rome photo under the previous day.
    pub fn local_date(&self) -> chrono::NaiveDate {
        self.local().date()
    }

    /// The wall-clock date/time in the zone where the moment happened,
    /// ignoring any rendering override.
    pub fn local_in_zone(&self) -> NaiveDateTime {
        self.utc.with_timezone(&self.timezone).naive_local()
    }

    pub fn effective_tz(&self) -> Tz {
        self.render_in.unwrap_or(self.timezone)
    }

    /// Render subsequent output in `tz` (used for the per-request override).
    pub fn render_in(&mut self, tz: Tz) {
        if tz != self.timezone {
            self.render_in = Some(tz);
        }
    }
}

impl Serialize for ZonedTime {
    fn serialize<S: serde::Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        use serde::ser::SerializeStruct;
        let mut s = serializer.serialize_struct("ZonedTime", 4)?;
        // `to_rfc3339` on a Utc emits `+00:00`; clients generally prefer `Z`.
        s.serialize_field("utc", &self.utc.format("%Y-%m-%dT%H:%M:%SZ").to_string())?;
        s.serialize_field("timezone", self.timezone.name())?;
        s.serialize_field("local", &self.local().format("%Y-%m-%dT%H:%M:%S").to_string())?;
        match self.render_in {
            Some(tz) => s.serialize_field("local_timezone", tz.name())?,
            None => s.serialize_field("local_timezone", &None::<&str>)?,
        }
        s.end()
    }
}

impl<'de> Deserialize<'de> for ZonedTime {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: serde::Deserializer<'de>,
    {
        let input = ZonedTimeInput::deserialize(deserializer)?;
        let tz = parse_tz("UTC").unwrap(); // fallback when input carries no zone
        match input.resolve(tz).map_err(serde::de::Error::custom)? {
            Some(z) => Ok(z),
            None => Err(serde::de::Error::custom("missing timestamp")),
        }
    }
}

// ---------------------------------------------------------------------------
// Accepted input form

/// What a client may send for a moment. Either an explicit instant, or a
/// wall-clock reading that is interpreted in a zone:
///
/// ```json
/// { "utc": "2026-07-04T08:30:00Z", "timezone": "Europe/Rome" }
/// { "local": "2026-07-04T10:30:00", "timezone": "Europe/Rome" }
/// ```
///
/// A bare string is also accepted: with an offset/`Z` it is read as an
/// instant, without one it is read as wall-clock in `timezone` (or the
/// fallback zone when that is absent).
#[derive(Debug, Clone, Deserialize)]
#[serde(untagged)]
pub enum ZonedTimeInput {
    Str(String),
    Obj(ZonedTimeFields),
}

#[derive(Debug, Clone, Deserialize)]
pub struct ZonedTimeFields {
    pub utc: Option<String>,
    pub local: Option<String>,
    /// Optional explicit offset form, e.g. `"2026-07-04T10:30:00+02:00"`.
    pub offset: Option<String>,
    pub timezone: Option<String>,
}

impl ZonedTimeInput {
    /// Resolve into a stored moment.
    ///
    /// `default_tz` is the zone used when the input carries none (the user's
    /// profile zone, or `UTC`).
    pub fn resolve(self, default_tz: Tz) -> Result<Option<ZonedTime>, ApiError> {
        use crate::utils::error::ApiError;

        let (utc, local, offset, tz_name) = match self {
            ZonedTimeInput::Str(s) => {
                let s = s.trim().to_string();
                if s.is_empty() {
                    return Ok(None);
                }
                // An explicit offset makes it an instant; anything else is
                // treated as wall-clock in the default zone.
                if looks_like_instant(&s) {
                    (Some(s), None, None, None)
                } else {
                    (None, Some(s), None, None)
                }
            }
            ZonedTimeInput::Obj(f) => (f.utc, f.local, f.offset, f.timezone),
        };

        if utc.is_none() && local.is_none() && offset.is_none() {
            return Ok(None);
        }

        // Zone: explicit -> default. An offset string also implies a fixed
        // zone name derived from the offset itself.
        let (tz, tz_from_offset) = match &tz_name {
            Some(name) => {
                let tz = parse_tz(name).ok_or_else(|| {
                    ApiError::ValidationError(format!("Unknown timezone: {}", name))
                })?;
                (tz, false)
            }
            None => (default_tz, false),
        };

        // Explicit instant wins.
        if let Some(s) = utc {
            let dt = parse_instant(&s).ok_or_else(|| {
                ApiError::ValidationError(format!("Invalid UTC timestamp: {}", s))
            })?;
            return Ok(Some(ZonedTime::new(dt, tz)));
        }

        // Offset-bearing local string: `2026-07-04T10:30:00+02:00`.
        if let Some(s) = offset.or(local.clone()) {
            if let Some(dt) = DateTime::parse_from_rfc3339(&s).ok() {
                let dt = dt.with_timezone(&Utc);
                if !tz_from_offset {
                    return Ok(Some(ZonedTime::new(dt, tz)));
                }
            }
        }

        // Wall-clock in a zone. DST gaps and folds have no single answer, so
        // refuse rather than guess.
        if let Some(s) = local {
            let naive = parse_naive(&s).ok_or_else(|| {
                ApiError::ValidationError(format!("Invalid local datetime: {}", s))
            })?;
            return match tz.from_local_datetime(&naive) {
                chrono::LocalResult::Single(dt) => {
                    Ok(Some(ZonedTime::new(dt.with_timezone(&Utc), tz)))
                }
                chrono::LocalResult::None => Err(ApiError::ValidationError(format!(
                    "{} does not exist in {} (DST gap) — pick another time",
                    naive.format("%Y-%m-%dT%H:%M:%S"),
                    tz.name()
                ))),
                chrono::LocalResult::Ambiguous(_, _) => Err(ApiError::ValidationError(
                    format!(
                        "{} is ambiguous in {} (DST fold) — send `utc` instead",
                        naive.format("%Y-%m-%dT%H:%M:%S"),
                        tz.name()
                    ),
                )),
            };
        }

        Ok(None)
    }
}

/// True when a string carries an explicit UTC designator or offset, i.e. it
/// already denotes an instant rather than a wall-clock reading.
fn looks_like_instant(s: &str) -> bool {
    s.ends_with('Z') || s.ends_with('z') || has_offset(s)
}

fn has_offset(s: &str) -> bool {
    // `+HH:MM` / `-HH:MM` after the date part.
    let t = s.find('T').unwrap_or(s.len());
    s[t..].contains('+') || s[t..].contains('-')
}

fn parse_instant(s: &str) -> Option<DateTime<Utc>> {
    DateTime::parse_from_rfc3339(s)
        .ok()
        .map(|d| d.with_timezone(&Utc))
        .or_else(|| parse_naive(s).map(|n| n.and_utc()))
}

fn parse_naive(s: &str) -> Option<NaiveDateTime> {
    let s = s.trim();
    NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S")
        .or_else(|_| NaiveDateTime::parse_from_str(s, "%Y-%m-%dT%H:%M:%S%.f"))
        .or_else(|_| NaiveDateTime::parse_from_str(s, "%Y-%m-%d %H:%M:%S"))
        .ok()
}

/// Parse an RFC3339/naive timestamp into a UTC instant, tolerating the
/// offset-less form that older rows (and SQLite's `datetime('now')`) use.
pub fn parse_instant_utc(s: &str) -> Option<DateTime<Utc>> {
    parse_instant(s)
}

/// Re-render every moment inside `T` in `tz` (the per-request override).
pub trait RenderInZone {
    fn render_in_zone(&mut self, tz: Tz);
}

impl RenderInZone for ZonedTime {
    fn render_in_zone(&mut self, tz: Tz) {
        self.render_in(tz);
    }
}

impl RenderInZone for Option<ZonedTime> {
    fn render_in_zone(&mut self, tz: Tz) {
        if let Some(z) = self {
            z.render_in(tz);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rome() -> Tz {
        parse_tz("Europe/Rome").unwrap()
    }

    #[test]
    fn utc_instant_is_preserved() {
        let z = ZonedTimeInput::Str("2026-07-04T08:30:00Z".into())
            .resolve(rome())
            .unwrap()
            .unwrap();
        assert_eq!(z.utc.to_rfc3339(), "2026-07-04T08:30:00+00:00");
        assert_eq!(z.timezone.name(), "Europe/Rome");
        // Rome is UTC+2 in July.
        assert_eq!(z.local().to_string(), "2026-07-04 10:30:00");
    }

    #[test]
    fn local_time_is_converted_with_dst() {
        // July -> CEST (+02:00)
        let z = ZonedTimeInput::Obj(ZonedTimeFields {
            utc: None,
            local: Some("2026-07-04T10:30:00".into()),
            offset: None,
            timezone: Some("Europe/Rome".into()),
        })
        .resolve(rome())
        .unwrap()
        .unwrap();
        assert_eq!(z.utc.to_rfc3339(), "2026-07-04T08:30:00+00:00");

        // January -> CET (+01:00): same wall clock, different instant
        let w = ZonedTimeInput::Obj(ZonedTimeFields {
            utc: None,
            local: Some("2026-01-04T10:30:00".into()),
            offset: None,
            timezone: Some("Europe/Rome".into()),
        })
        .resolve(rome())
        .unwrap()
        .unwrap();
        assert_eq!(w.utc.to_rfc3339(), "2026-01-04T09:30:00+00:00");
    }

    #[test]
    fn dst_gap_is_rejected() {
        // 2026-03-29 02:30 does not exist in most of Europe.
        let err = ZonedTimeInput::Obj(ZonedTimeFields {
            utc: None,
            local: Some("2026-03-29T02:30:00".into()),
            offset: None,
            timezone: Some("Europe/Rome".into()),
        })
        .resolve(rome())
        .unwrap_err();
        assert!(err.to_string().contains("DST gap"), "{}", err);
    }

    #[test]
    fn dst_fold_is_rejected() {
        // 2026-10-25 02:30 happens twice in most of Europe.
        let err = ZonedTimeInput::Obj(ZonedTimeFields {
            utc: None,
            local: Some("2026-10-25T02:30:00".into()),
            offset: None,
            timezone: Some("Europe/Rome".into()),
        })
        .resolve(rome())
        .unwrap_err();
        assert!(err.to_string().contains("ambiguous"), "{}", err);
    }

    #[test]
    fn offset_string_is_an_instant() {
        let z = ZonedTimeInput::Str("2026-07-04T10:30:00+02:00".into())
            .resolve(rome())
            .unwrap()
            .unwrap();
        assert_eq!(z.utc.to_rfc3339(), "2026-07-04T08:30:00+00:00");
    }

    #[test]
    fn bare_string_defaults_to_fallback_zone() {
        let z = ZonedTimeInput::Str("2026-07-04T10:30:00".into())
            .resolve(rome())
            .unwrap()
            .unwrap();
        assert_eq!(z.utc.to_rfc3339(), "2026-07-04T08:30:00+00:00");
    }

    #[test]
    fn unknown_zone_is_rejected() {
        let err = ZonedTimeInput::Obj(ZonedTimeFields {
            utc: None,
            local: Some("2026-07-04T10:30:00".into()),
            offset: None,
            timezone: Some("Mars/Olympus".into()),
        })
        .resolve(rome())
        .unwrap_err();
        assert!(err.to_string().contains("Unknown timezone"), "{}", err);
    }

    #[test]
    fn empty_input_is_none() {
        assert!(ZonedTimeInput::Str("".into()).resolve(rome()).unwrap().is_none());
        assert!(ZonedTimeInput::Obj(ZonedTimeFields {
            utc: None,
            local: None,
            offset: None,
            timezone: None,
        })
        .resolve(rome())
        .unwrap()
        .is_none());
    }

    #[test]
    fn render_override_changes_local_only() {
        let mut z = ZonedTime::new(
            "2026-07-04T08:30:00Z".parse::<DateTime<Utc>>().unwrap(),
            rome(),
        );
        z.render_in(parse_tz("Asia/Tokyo").unwrap());
        assert_eq!(z.timezone.name(), "Europe/Rome");
        assert_eq!(z.local().to_string(), "2026-07-04 17:30:00");
    }

    #[test]
    fn json_shape() {
        let z = ZonedTime::new(
            "2026-07-04T08:30:00Z".parse::<DateTime<Utc>>().unwrap(),
            rome(),
        );
        let json = serde_json::to_value(&z).unwrap();
        assert_eq!(json["utc"], "2026-07-04T08:30:00Z");
        assert_eq!(json["timezone"], "Europe/Rome");
        assert_eq!(json["local"], "2026-07-04T10:30:00");
        assert_eq!(json["local_timezone"], serde_json::Value::Null);
    }
}
