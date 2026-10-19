package com.nobelle.phone.progress

import com.nobelle.core.data.repository.WorkoutPayload
import com.nobelle.core.model.ExerciseSet
import com.nobelle.core.model.FastingSession
import com.nobelle.core.model.GymVisit
import com.nobelle.core.model.HealthObservation
import com.nobelle.core.model.MarkerGroup
import com.nobelle.core.model.Provenance
import com.nobelle.core.model.RecordingMethod
import com.nobelle.core.model.WatchWorkoutPayload
import com.nobelle.core.model.WorkoutSession
import com.nobelle.core.model.WorkoutState
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSummaryTest {
    private val zone = TimeZone.UTC
    // 2024-06-15 is a Saturday; its ISO week runs Mon 2024-06-10 .. Sun 2024-06-16.
    private val today = LocalDate(2024, 6, 15)

    private fun payload(
        id: String,
        ended: String? = null,
        sets: List<ExerciseSet> = emptyList(),
        state: WorkoutState = WorkoutState.COMPLETED,
        deleted: Instant? = null,
    ) = WorkoutPayload(
        WorkoutSession(
            id = id,
            ownerUID = "issuer:subject",
            category = "Strength",
            state = state,
            startedUtc = ended?.let { Instant.parse(it) },
            endedUtc = ended?.let { Instant.parse(it) },
            originDeviceId = "device",
            deletedUtc = deleted,
        ),
        sets.map { it.copy(sessionId = id) },
    )

    private fun set(exercise: String, reps: Int?, loadKg: Double?) = ExerciseSet(
        id = "$exercise-$reps-$loadKg",
        sessionId = "s",
        exerciseId = exercise,
        setIndex = 0,
        reps = reps,
        loadKg = loadKg,
    )

    private fun visit(date: String, deleted: Instant? = null) = GymVisit(
        id = "visit-$date-${deleted != null}",
        ownerUID = "issuer:subject",
        startInstant = Instant.parse("${date}T10:00:00Z"),
        originalTimeZone = "UTC",
        localAttendanceDate = date,
        deletedUtc = deleted,
    )

    private fun fast(
        status: FastingSession.Status,
        start: String,
        end: String? = null,
        deleted: Instant? = null,
    ) = FastingSession(
        id = "fast-$start-$end",
        ownerUID = "issuer:subject",
        actualStartInstant = Instant.parse(start),
        actualEndInstant = end?.let { Instant.parse(it) },
        originalStartZone = "UTC",
        targetDurationSec = 57_600,
        status = status,
        deletedUtc = deleted,
    )

    private fun watch(start: String, durationSec: Long) = WatchWorkoutPayload(
        id = "watch-$start",
        startedUtc = Instant.parse(start).toEpochMilliseconds(),
        endedUtc = Instant.parse(start).toEpochMilliseconds() + durationSec * 1_000,
        durationSec = durationSec,
        hrSampleCount = 0,
    )

    private fun observation(
        marker: String,
        value: Double,
        measured: Instant,
        deleted: Instant? = null,
    ) = HealthObservation(
        id = "$marker-$value-${measured.epochSeconds}",
        ownerUID = "issuer:subject",
        marker = marker,
        group = MarkerGroup.ACTIVITY,
        value = value,
        unit = "count",
        measuredUtc = measured,
        provenance = Provenance.MEASURED_IMPORTED,
        method = RecordingMethod.SOURCE_SYNC,
        deletedUtc = deleted,
    )

    private fun summarize(
        workouts: List<WorkoutPayload> = emptyList(),
        gymVisits: List<GymVisit> = emptyList(),
        fasts: List<FastingSession> = emptyList(),
        watchWorkouts: List<WatchWorkoutPayload> = emptyList(),
        observations: List<HealthObservation> = emptyList(),
        today: LocalDate = this.today,
    ) = ProgressSummary.summarize(
        workouts = workouts,
        gymVisits = gymVisits,
        fasts = fasts,
        watchWorkouts = watchWorkouts,
        observations = observations,
        today = today,
        zone = zone,
    )

    @Test
    fun `week runs Monday to Sunday and last week ends the day before`() {
        val report = summarize()
        assertEquals(LocalDate(2024, 6, 10), report.weekStart)
        assertEquals(LocalDate(2024, 6, 16), report.weekEnd)
        // A Monday stays the start of its own week.
        assertEquals(
            LocalDate(2024, 6, 10),
            summarize(today = LocalDate(2024, 6, 10)).weekStart,
        )
    }

    @Test
    fun `workouts count on their end day and split across the week boundary`() {
        val report = summarize(
            workouts = listOf(
                payload("last", ended = "2024-06-09T23:30:00Z"),   // Sunday, last week
                payload(
                    "this",
                    ended = "2024-06-10T00:30:00Z",                 // Monday, this week
                    sets = listOf(set("Squat", 5, 100.0), set("Squat", 5, 100.0)),
                ),
                payload("discarded", ended = "2024-06-12T10:00:00Z",
                    state = WorkoutState.DISCARDED),
                payload("deleted", ended = "2024-06-12T10:00:00Z",
                    deleted = Instant.parse("2024-06-13T00:00:00Z")),
            ),
        )
        assertEquals(1, report.thisWeek.workouts)
        assertEquals(2, report.thisWeek.sets)
        assertEquals(1, report.lastWeek.workouts)
        assertEquals(0, report.lastWeek.sets)
    }

    @Test
    fun `completed fasts count on their end day`() {
        val report = summarize(
            fasts = listOf(
                fast(FastingSession.Status.COMPLETED,
                    start = "2024-06-09T20:00:00Z", end = "2024-06-10T12:00:00Z"),
                fast(FastingSession.Status.COMPLETED,
                    start = "2024-06-04T20:00:00Z", end = "2024-06-05T12:00:00Z"),
                fast(FastingSession.Status.CANCELLED,
                    start = "2024-06-11T20:00:00Z", end = "2024-06-12T08:00:00Z"),
                fast(FastingSession.Status.ACTIVE, start = "2024-06-15T20:00:00Z"),
            ),
        )
        assertEquals(1, report.thisWeek.completedFasts)
        assertEquals(1, report.lastWeek.completedFasts)
    }

    @Test
    fun `attendance dedupes visits by local date`() {
        val report = summarize(
            gymVisits = listOf(
                visit("2024-06-12"),
                visit("2024-06-12"),
                visit("2024-06-05"),
                visit("2024-06-13", deleted = Instant.parse("2024-06-14T00:00:00Z")),
            ),
        )
        assertEquals(1, report.thisWeek.gymDays)
        assertEquals(1, report.lastWeek.gymDays)
        assertEquals(2, report.totalGymDays)
    }

    @Test
    fun `steps total sums only cumulative-marker records inside the week`() {
        val report = summarize(
            observations = listOf(
                observation("steps", 1_000.0, Instant.parse("2024-06-10T08:00:00Z")),
                observation("steps", 500.0, Instant.parse("2024-06-15T18:00:00Z")),
                observation("steps", 9_999.0, Instant.parse("2024-06-09T18:00:00Z")),
                observation("weight", 80.0, Instant.parse("2024-06-12T08:00:00Z")),
                observation("steps", 777.0, Instant.parse("2024-06-14T08:00:00Z"),
                    deleted = Instant.parse("2024-06-14T09:00:00Z")),
            ),
        )
        assertEquals(1_500.0, report.thisWeek.steps!!, 0.0001)
        assertEquals(9_999.0, report.lastWeek.steps!!, 0.0001)
    }

    @Test
    fun `watch workouts count and sum duration inside the week`() {
        val report = summarize(
            watchWorkouts = listOf(
                watch("2024-06-11T07:00:00Z", 3_600),
                watch("2024-06-13T18:00:00Z", 1_800),
                watch("2024-06-08T10:00:00Z", 2_400),
            ),
        )
        assertEquals(2, report.thisWeek.watchWorkouts)
        assertEquals(5_400L, report.thisWeek.watchDurationSec)
        assertEquals(1, report.lastWeek.watchWorkouts)
        assertEquals(2_400L, report.lastWeek.watchDurationSec)
    }

    @Test
    fun `personal bests take the heaviest rep-counted set per exercise`() {
        val report = summarize(
            workouts = listOf(
                payload(
                    "w1",
                    sets = listOf(
                        set("Squat", reps = 5, loadKg = 100.0),
                        set("Squat", reps = 3, loadKg = 120.0),
                        set("Squat", reps = 0, loadKg = 200.0),     // no reps — ignored
                        set("Bench Press", reps = 8, loadKg = 60.0),
                        set("Plank", reps = null, loadKg = null),   // nothing to rank
                    ),
                ),
            ),
        )
        assertEquals(
            listOf(
                PersonalBest("Squat", 120.0, 3),
                PersonalBest("Bench Press", 60.0, 8),
            ),
            report.personalBests,
        )
    }

    @Test
    fun `personal bests ignore sets from non-completed sessions and cap at five`() {
        val report = summarize(
            workouts = listOf(
                payload("w1", sets = listOf(set("Squat", reps = 5, loadKg = 300.0)),
                    state = WorkoutState.ACTIVE),
                payload(
                    "w2",
                    sets = ('a'..'h').map { name ->
                        set("Ex-$name", reps = 5, loadKg = ('h' - name) * 10.0 + 10)
                    },
                ),
            ),
        )
        assertEquals(5, report.personalBests.size)
        assertTrue(report.personalBests.none { it.exerciseId == "Squat" })
        assertEquals(80.0, report.personalBests.first().loadKg, 0.0001)
    }

    @Test
    fun `empty inputs produce zeroed weeks and no bests`() {
        val report = summarize()
        assertEquals(WeekSummary(), report.thisWeek)
        assertEquals(WeekSummary(), report.lastWeek)
        assertNull(report.thisWeek.steps)
        assertTrue(report.personalBests.isEmpty())
        assertEquals(0, report.totalGymDays)
    }
}
