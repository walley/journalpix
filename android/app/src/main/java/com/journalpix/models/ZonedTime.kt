package com.journalpix.models

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
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
@JsonAdapter(ZonedTimeAdapter::class)
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
            // ZonedDateTime e.g. 2026-07-01T10:30:00+09:00[Asia/Tokyo]
            runCatching {
                val zdt = ZonedDateTime.parse(v)
                return ZonedTime(
                    utc = zdt.toInstant()
                      .toString(),
                    timezone = zdt.zone.id,
                    local = zdt.toLocalDateTime()
                      .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    localTimezone = null
                )
            }
            // OffsetDateTime e.g. 2026-07-01T10:30:00+09:00
            runCatching {
                val odt = OffsetDateTime.parse(v)
                return fromInstant(odt.toInstant(), zoneId)
            }
            // ISO local date-time (with T or space)
            runCatching {
                val normalized = v.replace(' ', 'T')
                return fromLocal(
                    LocalDateTime.parse(
                        normalized.take(19),
                        DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    ), zoneId
                )
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
 * Custom Gson TypeAdapter for [ZonedTime] that tolerates both JSON Objects
 * (`{ "utc": ..., "timezone": ..., ... }`) and JSON Strings (`"2026-07-01T01:30:00Z"`).
 */
class ZonedTimeAdapter : TypeAdapter<ZonedTime>() {

    override fun write(out: JsonWriter, value: ZonedTime?) {
        if (value == null) {
            out.nullValue()
            return
        }
        out.beginObject()
        out.name("utc")
          .value(value.utc)
        out.name("timezone")
          .value(value.timezone)
        out.name("local")
          .value(value.local)
        if (value.localTimezone != null) {
            out.name("local_timezone")
              .value(value.localTimezone)
        } else {
            out.name("local_timezone")
              .nullValue()
        }
        out.endObject()
    }

    override fun read(reader: JsonReader): ZonedTime? {
        return when (reader.peek()) {
            JsonToken.NULL         -> {
                reader.nextNull()
                null
            }

            JsonToken.STRING       -> {
                val str = reader.nextString()
                if (str.isBlank()) null else ZonedTime.parseLegacy(str)
            }

            JsonToken.BEGIN_OBJECT -> {
                reader.beginObject()
                var utc: String? = null
                var timezone: String? = null
                var local: String? = null
                var localTimezone: String? = null

                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        continue
                    }
                    when (name) {
                        "utc"                                         -> utc = reader.nextString()
                        "timezone"                                    -> timezone =
                            reader.nextString()

                        "local"                                       -> local = reader.nextString()
                        "local_timezone", "localTimezone", "local_tz" -> localTimezone =
                            reader.nextString()

                        else                                          -> reader.skipValue()
                    }
                }
                reader.endObject()

                if (utc != null && timezone != null && local != null) {
                    ZonedTime(utc, timezone, local, localTimezone)
                } else if (utc != null) {
                    ZonedTime.parseLegacy(utc) ?: ZonedTime.fromInstant(
                        runCatching { Instant.parse(utc) }.getOrDefault(Instant.now()),
                        timezone ?: ZoneId.systemDefault().id
                    )
                } else if (local != null) {
                    ZonedTime.parseLegacy(local, timezone ?: ZoneId.systemDefault().id)
                } else null
            }

            else                   -> {
                reader.skipValue()
                null
            }
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
