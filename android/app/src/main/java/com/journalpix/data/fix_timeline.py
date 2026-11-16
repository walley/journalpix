src = open('OfflineStore.kt').read()

old = """        val start = trip.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = trip.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        val beforePlaces = mutableListOf<PlaceWithPhotos>()
        val afterPlaces = mutableListOf<PlaceWithPhotos>()
        val inRange = mutableListOf<PlaceWithPhotos>()

        for (p in places) {
            val visitLocalDate = p.visitDate?.localDate
            if (visitLocalDate != null && start != null && end != null) {
                if (visitLocalDate.isBefore(start)) {
                    beforePlaces.add(p)
                } else if (visitLocalDate.isAfter(end)) {
                    afterPlaces.add(p)
                } else {
                    inRange.add(p)
                }
            } else {
                inRange.add(p)
            }
        }

        val items = mutableListOf<TimelineItem>()

        if (beforePlaces.isNotEmpty()) {
            val ordered = beforePlaces.sortedWith(
                compareBy({ it.orderIndex }, { placeSortKey(it) })
            )
            items.add(TimelineItem(day = 0, date = BEFORE_KEY, places = ordered))
        }

        // Group in-range places by trip day number.
        val grouped = inRange.groupBy { p ->
            p.visitDate?.localDate?.let { ld ->
                start?.let { s ->
                    java.time.temporal.ChronoUnit.DAYS.between(s, ld).toInt() + 1
                } ?: p.day
            } ?: p.day
        }

        if (start != null && end != null && !end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(
                start,
                end
            ) < 365
        ) {
          val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end)
            .toInt() + 1
          var curr = start
          while (!curr!!.isAfter(end)) {
              val dateStr = curr.toString()
              val dayNum = java.time.temporal.ChronoUnit.DAYS.between(start!!, curr).toInt() + 1
              val list = grouped[dayNum].orEmpty()
                .sortedWith(
                    compareBy({ it.orderIndex }, { placeSortKey(it) })
                )
              items.add(TimelineItem(day = dayNum, date = dateStr, places = list))
              curr = curr.plusDays(1)
          }
          grouped.entries.filter { it.key !in 1 .. totalDays }
            .sortedBy { it.key }
            .forEach { (day, list) ->
              val ordered = list.sortedWith(
                compareBy({ it.orderIndex }, { placeSortKey(it) })
              )
              items.add(TimelineItem(day = day, date = null, places = ordered))
            }
        } else {
            grouped.entries.sortedBy { it.key }
              .forEach { (day, list) ->
                val ordered = list.sortedWith(
                  compareBy({ it.orderIndex }, { placeSortKey(it) })
                )
                items.add(TimelineItem(day = day, date = null, places = ordered))
              }
        }

        if (afterPlaces.isNotEmpty()) {
            val ordered = afterPlaces.sortedWith(
                compareBy({ it.orderIndex }, { placeSortKey(it) })
            )
            items.add(TimelineItem(day = -1, date = AFTER_KEY, places = ordered))
        }

        return TripTimeline(
            trip = trip.copy(
                placesCount = places.size,
                photosCount = places.sumOf { it.photos.size }
            ),
            timeline = items
        )"""

new = """        val start = trip.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val end = trip.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        // Collect unassigned photos that have a takenAt date matching a trip day
        // These are "day unassigned pool" photos - assigned to a day but no location place
        val unassignedWithDates = unassignedPhotos.values
            .filter { it.takenAt?.localDate != null }
            .groupBy { it.takenAt!!.localDate.toString() }

        val beforePlaces = mutableListOf<PlaceWithPhotos>()
        val afterPlaces = mutableListOf<PlaceWithPhotos>()
        val inRange = mutableListOf<PlaceWithPhotos>()

        for (p in places) {
            val visitLocalDate = p.visitDate?.localDate
            if (visitLocalDate != null && start != null && end != null) {
                if (visitLocalDate.isBefore(start)) {
                    beforePlaces.add(p)
                } else if (visitLocalDate.isAfter(end)) {
                    afterPlaces.add(p)
                } else {
                    inRange.add(p)
                }
            } else {
                inRange.add(p)
            }
        }

        val items = mutableListOf<TimelineItem>()

        if (beforePlaces.isNotEmpty()) {
            val ordered = beforePlaces.sortedWith(
                compareBy({ it.orderIndex }, { placeSortKey(it) })
            )
            // Add unassigned photos for BEFORE_KEY day
            val beforeUnassigned = unassignedWithDates[BEFORE_KEY] ?: emptyList()
            val beforeDayPlaces = if (beforeUnassigned.isNotEmpty()) {
                ordered + listOf(createUnassignedDayPlace(BEFORE_KEY, "Before trip", beforeUnassigned, 0))
            } else ordered
            items.add(TimelineItem(day = 0, date = BEFORE_KEY, places = beforeDayPlaces))
        }

        // Group in-range places by trip day number.
        val grouped = inRange.groupBy { p ->
            p.visitDate?.localDate?.let { ld ->
                start?.let { s ->
                    java.time.temporal.ChronoUnit.DAYS.between(s, ld).toInt() + 1
                } ?: p.day
            } ?: p.day
        }

        if (start != null && end != null && !end.isBefore(start) && java.time.temporal.ChronoUnit.DAYS.between(
                start,
                end
            ) < 365
        ) {
          val totalDays = java.time.temporal.ChronoUnit.DAYS.between(start, end)
            .toInt() + 1
          var curr = start
          while (!curr!!.isAfter(end)) {
              val dateStr = curr.toString()
              val dayNum = java.time.temporal.ChronoUnit.DAYS.between(start!!, curr).toInt() + 1
              val list = grouped[dayNum].orEmpty()
                .sortedWith(
                    compareBy({ it.orderIndex }, { placeSortKey(it) })
                )
              // Add unassigned photos for this day
              val dayUnassigned = unassignedWithDates[dateStr] ?: emptyList()
              val dayPlaces = if (dayUnassigned.isNotEmpty()) {
                  list + listOf(createUnassignedDayPlace(dateStr, dateStr, dayUnassigned, dayNum))
              } else list
              items.add(TimelineItem(day = dayNum, date = dateStr, places = dayPlaces))
              curr = curr.plusDays(1)
          }
          grouped.entries.filter { it.key !in 1 .. totalDays }
            .sortedBy { it.key }
            .forEach { (day, list) ->
              val ordered = list.sortedWith(
                compareBy({ it.orderIndex }, { placeSortKey(it) })
              )
              items.add(TimelineItem(day = day, date = null, places = ordered))
            }
        } else {
            grouped.entries.sortedBy { it.key }
              .forEach { (day, list) ->
                val ordered = list.sortedWith(
                  compareBy({ it.orderIndex }, { placeSortKey(it) })
                )
                items.add(TimelineItem(day = day, date = null, places = ordered))
              }
        }

        if (afterPlaces.isNotEmpty()) {
            val ordered = afterPlaces.sortedWith(
                compareBy({ it.orderIndex }, { placeSortKey(it) })
            )
            val afterUnassigned = unassignedWithDates[AFTER_KEY] ?: emptyList()
            val afterDayPlaces = if (afterUnassigned.isNotEmpty()) {
                ordered + listOf(createUnassignedDayPlace(AFTER_KEY, "After trip", afterUnassigned, -1))
            } else ordered
            items.add(TimelineItem(day = -1, date = AFTER_KEY, places = afterDayPlaces))
        }

        return TripTimeline(
            trip = trip.copy(
                placesCount = places.size,
                photosCount = places.sumOf { it.photos.size } + unassignedWithDates.values.flatten().size
            ),
            timeline = items
        )"""

if src.count(old) == 1:
    src = src.replace(old, new)
    open('OfflineStore.kt','w').write(src)
    print("ok")
else:
    print(f"old count: {src.count(old)}")