package com.warburg.infektion.simulation

import com.warburg.infektion.stats.HalfOpenRange
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * @author ewarburg
 */

class ScheduleBasedPeopleMover(val schedulesForCitizens: Map<CitizenId, Schedule>) : PeopleMover {
    override fun move(citizen: Citizen, currentLocation: Location?, tick: Tick): Location? {
        val schedule = schedulesForCitizens[citizen.id] ?: return currentLocation
        val currentEvent = schedule.eventAtTime(tick.currentDateTime)
        return currentEvent?.location ?: currentLocation
    }
}

/**
 * Time range from start time (inclusive) to end time (exclusive)
 */
class TimeRange(startTime: LocalTime, endTime: LocalTime) : HalfOpenRange<LocalTime>(startTime, endTime)

// TODO weekly schedules
//   base schedules that individual events are overlaid on top of
//   schedules relative to stock locations that vary by citizen (Work, Home, LocalGroceryStore, etc)

data class ScheduleEvent(val location: Location, val timeRange: TimeRange)

data class Schedule(val events: List<ScheduleEvent>) {
    init {
        for (e in this.events) {
            for (f in this.events) {
                if (e != f && e.location != f.location && e.timeRange.overlapsWith(f.timeRange)) {
                    throw IllegalArgumentException("Schedule puts someone in two places at once: $e and $f")
                }
            }
        }
    }

    fun eventAtTime(time: LocalDateTime): ScheduleEvent? = this.events.firstOrNull { it.timeRange.contains(time.toLocalTime()) }
}

class ScheduleEventBuilder {
    lateinit var location: Location
    lateinit var fromTime: LocalTime
    lateinit var untilTime: LocalTime

    fun at(location: Location): ScheduleEventBuilder = apply { this.location = location }
    fun from(time: LocalTime): ScheduleEventBuilder = apply { this.fromTime = time }
    fun until(time: LocalTime): ScheduleEventBuilder = apply { this.untilTime = time }

    fun build(): ScheduleEvent = ScheduleEvent(this.location, TimeRange(this.fromTime, this.untilTime))
}

class ScheduleBuilder {
    val events = mutableListOf<ScheduleEvent>()

    fun event(init: ScheduleEventBuilder.() -> Unit) {
        val eventBuilder = ScheduleEventBuilder()
        eventBuilder.init()
        this.events.add(eventBuilder.build())
    }

    fun build(): Schedule = Schedule(this.events)
}

inline fun schedule(init: ScheduleBuilder.() -> Unit): Schedule {
    val builder = ScheduleBuilder()
    builder.init()
    return builder.build()
}