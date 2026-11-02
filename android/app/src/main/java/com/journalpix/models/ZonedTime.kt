package com.journalpix.models

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A moment in time as returned by the backend (v0.2.0+).
 *
 * The server stores every moment as a UTC instant plus the IANA zone where it
 * happened. The zone is part of the data, not a display preference: a photo
 * shot in Tokyo keeps Tokyo wall-clock time even after the traveler lands in
 * Berlin.
 *
 * Example JSON:
 * ```json
 * {
 *   "utc": "2026-07-01T01:30:00Z",
 *   "timezone": "Asia/Tokyo",
 *   "local": "2026-07-01T10:30:00",
 *   "local_timezone": null
 * }
 * ```
 *
 * @property utc the instant, always in UTC
 * @property timezone the IANA zone where the moment happened
 * @property local wall clock in the effective zone
 * @property localTimezone `null` by default; when a request override was used,
 *   the zone [local] is rendered in (see [ApiClient] `?tz=` support)
 */
data class ZonedTime(
    val utc: String,
    val timezone: String,
    val local: String,
    val localTimezone: String? = null
) {
    /** The instant on the timeline, independent of any zone. */
    val instant: Instant?
        get() = runCatching { Instant.parse(utc) }.getOrNull()

    /** The zone the moment actually happened in. */
    val zone: ZoneId
        get() = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.of("UTC"))

    /** The zone [local] is rendered in (override zone, or [zone]). */
    val displayZone: ZoneId
        get() = localTimezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone

    /** Wall-clock reading in the effective display zone. */
    val localDateTime: LocalDateTime?
        get() {
            parseLocal(local)?.let { return it }
            // Fall back to converting the instant ourselves.
            return instant?.atZone(displayZone)?.toLocalDateTime()
        }

    /** Wall-clock date in the effective display zone. */
    val localDate: LocalDate?
        get() = localDateTime?.toLocalDate()

    /** Wall-clock date in the zone where it happened (ignores overrides). */
    val dateInZone: LocalDate?
        get() = instant?.atZone(zone)?.toLocalDate()

    /**
     * Short human-readable form, e.g. "10:30" or "10:30 (Tokyo)" when the
     * display zone differs from the zone it happened in.
     */
    fun timeLabel(): String {
        val ldt = localDateTime ?: return local
        val time = ldt.format(DateTimeFormatter.ofPattern("HH:mm"))
        val override = localTimezone
        return if (override != null && override != timezone) {
            "$time (${shortZoneName(override)})"
        } else {
            time
        }
    }

    /** Date + time label, e.g. "Jul 1, 2026 10:30". */
    fun dateTimeLabel(): String {
        val ldt = localDateTime ?: return local
        return ldt.format(DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm"))
    }

    /** Date-only label, e.g. "Jul 1, 2026". */
    fun dateLabel(): String {
        val d = localDate ?: return local.take(10)
        return d.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
    }

    /** Sort key: the UTC instant string sorts chronologically. */
    val sortKey: String get() = utc

    /**
     * The input form to send back to the server. Sending the instant plus the
     * zone preserves both pieces of information losslessly.
     */
    fun toInput(): ZonedTimeInput = ZonedTimeInput(utc = utc, timezone = timezone)

    companion object {
        private fun parseLocal(value: String): LocalDateTime? = try {
            LocalDateTime.parse(value.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        } catch (_: DateTimeParseException) {
            null
        }

        private fun shortZoneName(zoneId: String): String =
            zoneId.substringAfterLast('/').replace('_', ' ')

        /** Build a moment from a device-local wall-clock reading and a zone. */
        fun fromLocal(local: LocalDateTime, zoneId: String): ZonedTime {
            val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
            val zdt = local.atZone(zone)
            return ZonedTime(
                utc = zdt.toInstant().toString(),
                timezone = zone.id,
                local = local.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                localTimezone = null
            )
        }

        /** Build a moment from an instant and the zone it happened in. */
        fun fromInstant(instant: Instant, zoneId: String): ZonedTime {
            val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
            return ZonedTime(
                utc = instant.toString(),
                timezone = zone.id,
                local = instant.atZone(zone).toLocalDateTime()
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                localTimezone = null
            )
        }

        /**
         * Tolerant parser for legacy/plain date strings stored on the device
         * before the timezone change. Accepts `yyyy-MM-dd`, `yyyy-MM-ddTHH:mm:ss`,
         * RFC3339 instants, and EXIF `yyyy:MM:dd HH:mm:ss`.
         */
        fun parseLegacy(value: String?, zoneId: String = ZoneId.systemDefault().id): ZonedTime? {
            if (value.isNullOrBlank()) return null
            val v = value.trim()

            // EXIF: 2026:07:01 10:30:00
            runCatching {
                val dt = LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                return fromLocal(dt, zoneId)
            }
            // Instant with Z/offset
            runCatching {
                return fromInstant(Instant.parse(v), zoneId)
            }
            // ISO local date-time
            runCatching {
                return fromLocal(LocalDateTime.parse(v.take(19), DateTimeFormatter.ISO_LOCAL_DATE_TIME), zoneId)
            }
            // Date only -> midnight local
            runCatching {
                val d = LocalDate.parse(v.take(10))
                return fromLocal(d.atStartOfDay(), zoneId)
            }
            return null
        }
    }
}

/**
 * Input form accepted by the backend for a moment. Exactly one of [utc] or
 * [local] should be set; [timezone] names the zone for [local] (and is kept
 * alongside [utc] so the record remembers where it happened).
 */
data class ZonedTimeInput(
    val utc: String? = null,
    val local: String? = null,
    val timezone: String? = null
)
