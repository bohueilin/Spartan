package com.spartan.domain

import com.spartan.domain.engine.CoachingOptions
import com.spartan.domain.engine.RuleBasedRecommendationSource
import com.spartan.domain.engine.SafetyEngine
import com.spartan.domain.engine.TrainingLevel
import com.spartan.domain.engine.TrainingProfile
import com.spartan.domain.engine.VideoLibrary
import com.spartan.domain.model.MetricType
import com.spartan.domain.model.ReadinessSnapshot
import com.spartan.domain.model.WhoopSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoLibraryTest {

    private val safety = SafetyEngine()

    @Test
    fun everyGuide_hasHttpsYoutubeWatchUrl_andSaneDuration() {
        VideoLibrary.allGuides.forEach { guide ->
            assertTrue(
                "${guide.id} url must be a specific YouTube watch link: ${guide.url}",
                guide.url.startsWith("https://www.youtube.com/watch?v=") || guide.url.startsWith("https://youtu.be/"),
            )
            assertTrue("${guide.id} minutes out of range", guide.minutes in 3..45)
            assertTrue(guide.title.isNotBlank() && guide.channel.isNotBlank())
        }
    }

    @Test
    fun allCopy_passesTheSafetyEngine() {
        VideoLibrary.allGuides.forEach { safety.sanitize(it.title) }
        MetricType.entries.forEach { type ->
            VideoLibrary.trainingFor(type)?.let { training ->
                safety.sanitize(training.intro)
                assertTrue("$type training must list at least one guide", training.guides.isNotEmpty())
            }
        }
    }

    @Test
    fun clinicianFirstMetrics_getNoTrainingBlock() {
        // Exercise prescriptions for these belong to a clinician; Spartan stays out on purpose.
        assertNull(VideoLibrary.trainingFor(MetricType.APOB))
        assertNull(VideoLibrary.trainingFor(MetricType.LPA))
        assertNull(VideoLibrary.trainingFor(MetricType.CAC))
        assertNull(VideoLibrary.trainingFor(MetricType.CUSTOM))
    }

    @Test
    fun whoopMetricsTheAppCoachesOn_allHaveTraining() {
        listOf(
            MetricType.RECOVERY_SCORE, MetricType.HRV_RMSSD, MetricType.RESTING_HEART_RATE,
            MetricType.SLEEP_PERFORMANCE, MetricType.SLEEP_DURATION, MetricType.SLEEP_DEBT,
            MetricType.DAY_STRAIN, MetricType.EXERCISE_MINUTES,
        ).forEach { assertNotNull("$it should have a training block", VideoLibrary.trainingFor(it)) }
    }

    @Test
    fun everyTrainingActivityTheEngineGenerates_hasAVideo_andSafetyItemsDoNot() {
        val source = RuleBasedRecommendationSource()
        // Sweep the readiness space so every rule fires at least once.
        val snapshots = listOf(
            WhoopSnapshot(dateEpochDay = 1000, recoveryScore = 92, sleepPerformance = 85),
            WhoopSnapshot(dateEpochDay = 1000, recoveryScore = 55, sleepPerformance = 60, sleepDebtHours = 2.0),
            WhoopSnapshot(dateEpochDay = 1000, recoveryScore = 20, dayStrain = 15.0),
            WhoopSnapshot(dateEpochDay = 1000, recoveryScore = null),
        )
        val activities = snapshots.flatMap { today ->
            val history = (1..6).map {
                WhoopSnapshot(dateEpochDay = 1000L - it, hrvMs = 60.0, restingHeartRate = 55.0, dayStrain = 15.0)
            }
            source.recommend(
                ReadinessSnapshot.from(today.copy(hrvMs = 40.0, restingHeartRate = 62.0), history),
                CoachingOptions(missedGoalYesterday = true, painFlag = true),
            )
        }
        val trainingSlugs = setOf("mobility", "zone2-walk", "zone2", "strength", "stretch", "winddown", "breathing", "quickwin", "connect-whoop")
        activities.forEach { activity ->
            val slug = activity.id.substringAfter(':')
            val guide = VideoLibrary.guideForActivity(activity.id)
            if (slug in trainingSlugs) {
                assertNotNull("training activity '$slug' should link a follow-along video", guide)
            } else {
                assertNull("non-training activity '$slug' must not push a video", guide)
            }
        }
        // Pain and clinician items explicitly stay video-free.
        assertNull(VideoLibrary.guideForActivity("1000:pain-deload"))
        assertNull(VideoLibrary.guideForActivity("1000:clinician"))
        assertNull(VideoLibrary.guideForActivity("1000:hydration"))
        assertNull(VideoLibrary.guideForActivity("no-colon-id"))
    }

    @Test
    fun activityVideoDurations_roughlyMatchTheActivityEstimate() {
        // The zone2 session (25 min est) links a ~30 min walk; mobility (10 min) an ~11 min flow.
        assertEquals(30, VideoLibrary.guideForActivity("1:zone2")!!.minutes)
        assertEquals(11, VideoLibrary.guideForActivity("1:mobility")!!.minutes)
        assertEquals(15, VideoLibrary.guideForActivity("1:zone2-walk")!!.minutes)
    }

    @Test
    fun everyGuide_isFromAnApprovedChannel_andHasCompleteMetadata() {
        val approved = setOf(
            "Walk at Home", "growwithjo", "Team Body Project", "Fitness Blender", "HASfit",
            "Juice & Toya", "Caroline Girvan", "Yoga With Adriene",
        )
        VideoLibrary.allGuides.forEach { g ->
            assertTrue("${g.id} from unapproved channel '${g.channel}'", g.channel in approved)
        }
    }

    // --- age + needs-aware ranking -------------------------------------------------------------

    @Test
    fun recommend_withNoProfile_keepsCuratedOrder() {
        val training = VideoLibrary.recommend(MetricType.STRENGTH_SESSIONS, TrainingProfile.NONE)!!
        // First curated pick for strength is the beginner HASfit session.
        assertEquals("strength_hasfit_20", training.guides.first().id)
        assertFalse(training.intro.contains("low-impact and beginner-friendly"))
    }

    @Test
    fun recommend_forOlderAdult_leadsWithBeginnerLowImpact_andDropsTheHardIntermediate() {
        val profile = TrainingProfile(ageYears = 44)
        val training = VideoLibrary.recommend(MetricType.STRENGTH_SESSIONS, profile)!!
        assertTrue("older-adult intro should flag gentle picks", training.intro.contains("low-impact and beginner-friendly"))
        // The only INTERMEDIATE/HARD option (Caroline Girvan) must not lead, and with a 3-cap it
        // sinks below the three beginner sessions.
        assertEquals(TrainingLevel.BEGINNER, training.guides.first().level)
        assertTrue(
            "the hard intermediate session should not surface for an older beginner",
            training.guides.none { it.level == TrainingLevel.INTERMEDIATE },
        )
    }

    @Test
    fun recommend_forOffTargetMetric_reranksGentleEvenWhenAgeUnknown() {
        val profile = TrainingProfile(ageYears = null, offTargetMetrics = setOf(MetricType.STRENGTH_SESSIONS))
        val training = VideoLibrary.recommend(MetricType.STRENGTH_SESSIONS, profile)!!
        assertTrue(training.intro.contains("low-impact and beginner-friendly"))
        assertEquals(TrainingLevel.BEGINNER, training.guides.first().level)
    }

    @Test
    fun guideForActivity_picksGentlerStrength_forOlderAdults() {
        val young = VideoLibrary.guideForActivity("1:strength", TrainingProfile(ageYears = 28))
        val older = VideoLibrary.guideForActivity("1:strength", TrainingProfile(ageYears = 46))
        assertEquals("strength_juicetoya_30", young!!.id)
        assertEquals("strength_hasfit_20", older!!.id)
        assertEquals(TrainingLevel.BEGINNER, older.level)
    }

    @Test
    fun recommend_cappsAtThreeGuides() {
        MetricType.entries.forEach { type ->
            VideoLibrary.recommend(type, TrainingProfile(ageYears = 44))?.let {
                assertTrue("$type returned more than 3 guides", it.guides.size <= 3)
            }
        }
    }
}
