package com.journalpix.models

/**
 * How the traveller moved from the previous place to this one.
 * Stored on the destination place as [PlaceWithPhotos.transportFromPrevious].
 */
enum class TransportMode(val label: String, val emoji: String) {
    FOOT("On foot", "🚶"),
    BIKE("Bike", "🚲"),
    SCOOTER("Scooter", "🛴"),
    CAR("Car", "🚗"),
    TAXI("Taxi / rideshare", "🚕"),
    BUS("Bus", "🚌"),
    TRAIN("Train", "🚆"),
    METRO("Metro / subway", "🚇"),
    FERRY("Ferry / boat", "⛴️"),
    PLANE("Plane", "✈️"),
    OTHER("Other", "➡️");

    companion object {
        fun fromStored(value: String?): TransportMode? {
            if (value.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.label.equals(value, ignoreCase = true) }
        }
    }
}
