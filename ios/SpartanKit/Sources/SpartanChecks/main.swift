// SpartanChecks — runs the full SpartanKit test suite (including the 756-plan coaching eval
// harness) as a plain executable: `swift run SpartanChecks`. Exits non-zero on any failure.

import Foundation

func run(_ name: String, _ body: () throws -> Void) {
    shimCurrentTest = name
    do {
        try body()
    } catch is ShimUnwrapError {
        // failure already recorded by XCTUnwrap
    } catch {
        shimFailureCount += 1
        print("  ✗ ERROR [\(name)] threw \(error)")
    }
}

print("SpartanKit checks — mirrored from the Android JVM suite\n")
var testCount = 0

// --- SafetyEngineTests ---
let safety = SafetyEngineTests()
run("safety.rejectsBlockedMedicalClaims") { safety.testRejectsBlockedMedicalClaimsInManyForms() }; testCount += 1
run("safety.acceptsWellnessLanguage") { safety.testAcceptsLegitimateWellnessLanguage() }; testCount += 1
run("safety.sanitizeThrowsOnlyForBlocked") { safety.testSanitizeThrowsOnlyForBlockedCopy() }; testCount += 1

// --- CoachingEngineTests ---
let engine = CoachingEngineTests()
run("engine.bandThresholds") { engine.testBandThresholdsMatchSpec() }; testCount += 1
run("engine.lowRecoveryRequiredNoHard") { engine.testLowRecoveryProducesRequiredRecoveryAndNoHardTraining() }; testCount += 1
run("engine.primedGreenlight") { engine.testPrimedGreenlightsAQualityStrengthSession() }; testCount += 1
run("engine.poorSleepHygiene") { engine.testPoorSleepAddsSleepHygiene() }; testCount += 1
run("engine.elevatedRhrCheckIn") { engine.testElevatedRhrTrendAddsCheckIn() }; testCount += 1
run("engine.clinicianReferral") { try engine.testConcerningRespiratoryRateAddsNonDiagnosticClinicianReferral() }; testCount += 1
run("engine.maxActivitiesCap") { engine.testPlanRespectsMaxActivitiesButKeepsRequiredAndReferral() }; testCount += 1
run("engine.staleFallback") { engine.testStaleDataProducesSafeFallbackPlan() }; testCount += 1
run("engine.readinessTrends") { try engine.testReadinessSnapshotFromComputesTrendsAndBand() }; testCount += 1

// --- CoachingEvalTests (the invariant harness) ---
let eval = CoachingEvalTests()
run("eval.fullReadinessMatrix") { eval.testEvalInvariantsHoldAcrossFullReadinessMatrix() }; testCount += 1
run("eval.optionAndTrendVariations") { eval.testEvalInvariantsHoldWithOptionAndTrendVariations() }; testCount += 1
run("eval.concerningVitalsNeverHard") { eval.testEvalConcerningVitalsNeverGreenlightHardTraining() }; testCount += 1
run("eval.lowRecoveryAlwaysRequired") { eval.testEvalLowRecoveryAlwaysCarriesARequiredRecoveryAction() }; testCount += 1
run("eval.capRespected") { eval.testEvalMaxActivitiesRespectedWhenNoRequiredOverflow() }; testCount += 1

// --- WhoopAndAvailabilityTests ---
let data = WhoopAndAvailabilityTests()
run("data.mockLabeledSeries") { data.testMockWhoopClient_isLabeledSampleData_andReturnsSeries() }; testCount += 1
run("data.openWindowsMerge") { data.testOpenWindows_subtractsAndMergesBusyBlocks() }; testCount += 1
run("data.suggestSlotEarliestFit") { data.testSuggestSlot_returnsEarliestFittingGapTrimmedToLength() }; testCount += 1
run("data.suggestSlotNilWhenNothingFits") { data.testSuggestSlot_returnsNilWhenNothingFits() }; testCount += 1
run("data.fullyBusyDay") { data.testAvailability_fullyBusyDayHasNoOpenWindows() }; testCount += 1
run("data.zeroLengthDay") { data.testAvailability_zeroLengthDayIsEmpty() }; testCount += 1
run("data.exactFit") { data.testAvailability_exactFitSlotIsFound() }; testCount += 1
run("data.adjacentMerge") { data.testAvailability_adjacentBusyBlocksMergeAndDoNotLeaveSlivers() }; testCount += 1
run("data.minWindow") { data.testAvailability_respectsMinWindow() }; testCount += 1
run("data.planProgressMath") { data.testDailyPlan_progressAndTotals() }; testCount += 1

// --- ProjectionAndExplainerChecks (expected-improvement + metric education) ---
let proj = ProjectionAndExplainerChecks()
run("proj.week0EqualsCurrent") { proj.testWeekZeroMatchesCurrentValueForEveryMetricAndTier() }; testCount += 1
run("proj.weeksZeroToEight") { proj.testWeeksRunZeroToEightInTwoWeekSteps() }; testCount += 1
run("proj.lowNeverExceedsHigh") { proj.testLowNeverExceedsHighAcrossAllTiersAndWeeks() }; testCount += 1
run("proj.rhrFloor") { proj.testRhrProjectionNeverGoesBelowFloor() }; testCount += 1
run("proj.hrvCap") { proj.testHrvProjectionNeverExceedsCap() }; testCount += 1
run("proj.recoveryCap90") { proj.testRecoveryProjectionCapsAtNinety() }; testCount += 1
run("proj.zeroConsistencyFlat") { proj.testZeroConsistencyHoldsFlat() }; testCount += 1
run("proj.consistencyMonotone") { proj.testMoreConsistencyMeansAtLeastAsMuchProjectedChange() }; testCount += 1
run("proj.deterministic") { proj.testProjectionIsDeterministic() }; testCount += 1
run("proj.onlyProvidedMetrics") { proj.testOnlyProvidedMetricsAreProjected() }; testCount += 1
run("explainers.coverAllNine") { proj.testExplainersCoverAllNineMetrics() }; testCount += 1
run("explainers.respClinicianNote") { try proj.testRespiratoryRateExplainerCarriesClinicianNote() }; testCount += 1
run("explainers.allCopySafe") { proj.testEveryUserFacingStringPassesSafetyCheck() }; testCount += 1

// --- CoachingGymTests (domain-specific reward eval over the scenario manifest) ---
let gym = CoachingGymTests()
run("gym.manifestLargeDeterministicCoversDifficulties") { gym.testManifestIsLargeDeterministicAndCoversEveryDifficulty() }; testCount += 1
run("gym.shippedEngineClearsTheBar") { gym.testShippedRulesEngineClearsTheBar() }; testCount += 1
run("gym.deterministic") { gym.testGymIsDeterministic() }; testCount += 1
run("gym.recklessPolicyCrushed") { gym.testRecklessPolicyIsCrushedByTheGraders() }; testCount += 1
run("gym.lazyPolicyLosesOnQuality") { gym.testLazyPolicyLosesOnQualityAndRedFlagsNotOnGenericSafety() }; testCount += 1
run("gym.alignmentPenalizesForbiddenHard") { try gym.testAlignmentGraderDirectlyPenalizesForbiddenHardTraining() }; testCount += 1
run("gym.overAlarmScoresPointSeven") { try gym.testSafetyGraderOverAlarmismScoresExactlyPointSevenThroughTheGrader() }; testCount += 1
run("gym.rewardMathHardGate") { gym.testRewardMathWeightsSumToOneAndSafetyIsAHardGate() }; testCount += 1

// --- WhoopCsvImportTests (WHOOP export parsing + per-day merge) ---
let csv = WhoopCsvImportTests()
run("csv.detectsAllFourKinds") { csv.testParseDetectsAllFourKinds() }; testCount += 1
run("csv.unknownCsvReturnsNil") { csv.testParseUnknownCsvReturnsNil() }; testCount += 1
run("csv.cyclesMapValuesMinutesToHours") { try csv.testCyclesMapValuesAndConvertMinutesToHours() }; testCount += 1
run("csv.noSleepBlanksParseAsNil") { try csv.testCyclesNoSleepCycleBlanksParseAsNil() }; testCount += 1
run("csv.dayIsWakeDate") { try csv.testCyclesDayIsTheDateTheUserWokeUp() }; testCount += 1
run("csv.inProgressCycleParses") { try csv.testCyclesInProgressCycleWithoutEndTimeParses() }; testCount += 1
run("csv.napColumnBothWays") { try csv.testSleepsNapColumnParsedBothWays() }; testCount += 1
run("csv.workoutsZonesAndHeartRates") { try csv.testWorkoutsParseDurationsZonesAndHeartRates() }; testCount += 1
run("csv.quotedJournalNote") { try csv.testJournalQuotedNoteWithCommaAndNewlineDoesNotBreakRows() }; testCount += 1
run("csv.mergerJoinsPerDay") { try csv.testMergerJoinsSleepJournalAndCyclesPerDay() }; testCount += 1
run("csv.mergerDedupesSameDay") { try csv.testMergerDedupesSameDayKeepingTheRicherRecord() }; testCount += 1
run("csv.mergerSleepsOnlyDays") { try csv.testMergerSleepsWithoutCyclesStillProduceSleepDays() }; testCount += 1
run("csv.mergerExerciseMinutes") { try csv.testMergerSumsExerciseMinutesPerDayAndDedupesWorkouts() }; testCount += 1

// --- VideoLibraryTests (follow-along video catalog) ---
let video = VideoLibraryTests()
run("video.httpsWatchUrlsSaneDurations") { video.testEveryGuideHasHttpsYoutubeWatchUrlAndSaneDuration() }; testCount += 1
run("video.copyPassesSafetyEngine") { try video.testAllCopyPassesTheSafetyEngine() }; testCount += 1
run("video.clinicianFirstNoTraining") { video.testClinicianFirstMetricsGetNoTrainingBlock() }; testCount += 1
run("video.coachedMetricsHaveTraining") { video.testWhoopMetricsTheAppCoachesOnAllHaveTraining() }; testCount += 1
run("video.trainingSlugsHaveVideos") { video.testEveryTrainingActivityTheEngineGeneratesHasAVideoAndSafetyItemsDoNot() }; testCount += 1
run("video.durationsMatchEstimates") { try video.testActivityVideoDurationsRoughlyMatchTheActivityEstimate() }; testCount += 1

print("\n\(testCount) tests, \(shimAssertionCount) assertions, \(shimFailureCount) failures")
if shimFailureCount > 0 {
    exit(1)
}
print("ALL CHECKS PASSED")
