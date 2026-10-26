package com.spartan.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import com.spartan.domain.engine.RecoveryZone
import com.spartan.ui.theme.CalmDarkColors
import com.spartan.ui.theme.CalmHero
import com.spartan.ui.theme.CalmLightColors
import com.spartan.ui.theme.DarkColors
import com.spartan.ui.theme.HeroColors
import com.spartan.ui.theme.LightColors
import com.spartan.ui.theme.MetricAccents
import com.spartan.ui.theme.SpartanBands
import com.spartan.ui.theme.SpartanZones
import com.spartan.ui.theme.StandardHero
import com.spartan.ui.theme.zoneOnHeroColor
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Every text and mark pair the redesign relies on, by the WCAG 2.x formula: text 4.5:1, non-text
 * (rings, chart lines, the check, control outlines) 3:1. Tints are alpha-composited over their
 * surface first, as they are drawn. All four schemes — light, dark, calm light, calm dark — and
 * both heroes.
 */
class TokenContrastTest {

    private fun channel(c: Int): Double {
        val s = c / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double {
        val argb = color.toArgb()
        return 0.2126 * channel(argb shr 16 and 0xFF) + 0.7152 * channel(argb shr 8 and 0xFF) + 0.0722 * channel(argb and 0xFF)
    }

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun tint(color: Color, alpha: Float, over: Color) = color.copy(alpha = alpha).compositeOver(over)

    private val failures = mutableListOf<String>()

    private fun check(label: String, fg: Color, bg: Color, need: Double) {
        val ratio = contrast(fg, bg)
        if (ratio < need) failures += "%s: %.2f:1, needs %.1f:1".format(label, ratio, need)
    }

    private fun text(label: String, fg: Color, bg: Color) = check(label, fg, bg, 4.5)
    private fun mark(label: String, fg: Color, bg: Color) = check(label, fg, bg, 3.0)

    private fun assertNoFailures() = assertTrue(failures.joinToString("\n"), failures.isEmpty())

    private val schemes = mapOf(
        "light" to LightColors,
        "dark" to DarkColors,
        "calm light" to CalmLightColors,
        "calm dark" to CalmDarkColors,
    )

    private fun ColorScheme.isDark() = luminance(surface) < 0.2

    @Test
    fun everySchemesTextRoles_meet4point5() {
        schemes.forEach { (name, s) ->
            for ((bgName, bg) in listOf("surface" to s.surface, "background" to s.background, "surfaceVariant" to s.surfaceVariant, "surfaceContainerHigh" to s.surfaceContainerHigh)) {
                text("$name onSurface on $bgName", s.onSurface, bg)
                text("$name onSurfaceVariant on $bgName", s.onSurfaceVariant, bg)
                text("$name primary on $bgName", s.primary, bg)
            }
            text("$name onPrimary on primary", s.onPrimary, s.primary)
            text("$name onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer)
            text("$name onSecondary on secondary", s.onSecondary, s.secondary)
            text("$name onSecondaryContainer on secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
            text("$name tertiary on surface", s.tertiary, s.surface)
            text("$name onTertiary on tertiary", s.onTertiary, s.tertiary)
            text("$name onTertiaryContainer on tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
            text("$name inversePrimary on inverseSurface", s.inversePrimary, s.inverseSurface)
            text("$name inverseOnSurface on inverseSurface", s.inverseOnSurface, s.inverseSurface)
            // StatusPill INFO and the urgency chip draw primary on its own tint.
            text("$name primary on its 14% tint", s.primary, tint(s.primary, 0.14f, s.surface))
            text("$name primary on its 12% tint", s.primary, tint(s.primary, 0.12f, s.surface))
            // NEUTRAL status pills draw onSurfaceVariant on its own tint.
            text("$name onSurfaceVariant on its 14% tint", s.onSurfaceVariant, tint(s.onSurfaceVariant, 0.14f, s.surface))
            mark("$name primary on primaryContainer", s.primary, s.primaryContainer)
            mark("$name primary on surfaceContainerHighest (track)", s.primary, s.surfaceContainerHighest)
            // The outline is the only boundary of an unchecked switch and an outlined field.
            mark("$name outline on surface", s.outline, s.surface)
            mark("$name outline on background", s.outline, s.background)
            mark("$name outline (switch thumb) on surfaceContainerHighest (switch track)", s.outline, s.surfaceContainerHighest)
        }
        assertNoFailures()
    }

    @Test
    fun secondaryText_isTheDarkenedSpecGrey() {
        // The spec's #46564F, 7.77:1 on white: well clear of 4.5:1, not the old lighter grey.
        assertTrue(contrast(LightColors.onSurfaceVariant, Color.White) > 7.5)
        assertTrue(contrast(DarkColors.onSurfaceVariant, DarkColors.surface) > 7.0)
    }

    @Test
    fun statusColours_readAsText_onSurfacesAndOnTheirOwnTints() {
        val light = listOf("green" to SpartanBands.primedLight, "amber" to SpartanBands.easyLight, "red" to SpartanBands.restLight)
        val dark = listOf("green" to SpartanBands.primedDark, "amber" to SpartanBands.easyDark, "red" to SpartanBands.restDark)
        for ((name, scheme) in schemes) {
            val palette = if (scheme.isDark()) dark else light
            for ((zone, color) in palette) {
                text("$name $zone on surface", color, scheme.surface)
                text("$name $zone on background", color, scheme.background)
                text("$name $zone on surfaceVariant", color, scheme.surfaceVariant)
                text("$name $zone on its 14% tint", color, tint(color, 0.14f, scheme.surface))
                text("$name $zone on its 18% tint", color, tint(color, 0.18f, scheme.surface))
            }
        }
        assertNoFailures()
    }

    @Test
    fun zoneMarks_ringsAndLines_meet3_onLightSurfaces() {
        for (color in listOf(SpartanZones.greenMarkLight, SpartanZones.amberMarkLight, SpartanZones.redMarkLight)) {
            mark("zone mark $color on white", color, LightColors.surface)
            mark("zone mark $color on background", color, LightColors.background)
        }
        assertNoFailures()
    }

    /** A chart line over its own 14% band with the 18% area fill on top — the worst spot it is drawn on. */
    private fun bandUnder(color: Color, surface: Color) = tint(color, 0.18f, tint(color, 0.14f, surface))

    @Test
    fun everyChartLine_meets3_onItsOwnBandAndArea() {
        val light = listOf(SpartanZones.greenMarkLight, SpartanZones.amberMarkLight, SpartanZones.redMarkLight) +
            listOf(MetricAccents.hrvLight, MetricAccents.restingHrLight, MetricAccents.sleepLight, MetricAccents.respiratoryLight, MetricAccents.strainLight)
        val dark = listOf(SpartanBands.primedDark, SpartanBands.easyDark, SpartanBands.restDark) +
            listOf(MetricAccents.hrvDark, MetricAccents.restingHrDark, MetricAccents.sleepDark, MetricAccents.respiratoryDark, MetricAccents.strainDark)
        light.forEach { mark("light line $it on its band", it, bandUnder(it, LightColors.surface)) }
        dark.forEach { mark("dark line $it on its band", it, bandUnder(it, DarkColors.surface)) }
        assertNoFailures()
    }

    @Test
    fun theHero_carriesWhiteText_andEveryZoneColour() {
        for ((name, hero) in listOf<Pair<String, HeroColors>>("standard" to StandardHero, "calm" to CalmHero)) {
            text("$name hero content", hero.content, hero.container)
            text("$name hero contentVariant", hero.contentVariant, hero.container)
            // The inverted hero button (PrimaryButton(onHero = true)) and a status pill on the hero.
            text("$name hero button label", hero.container, hero.content)
            text("$name hero pill", hero.content, tint(hero.content, 0.14f, hero.container))
            for (zone in RecoveryZone.entries) {
                val color = zoneOnHeroColor(zone)
                text("$name hero $zone", color, hero.container)
                mark("$name hero $zone arc on the track", color, hero.track)
            }
            // The Progress goal ring draws its arc in the hero's content colour.
            mark("$name hero goal arc on the track", hero.content, hero.track)
        }
        assertNoFailures()
    }

    @Test
    fun metricAccents_areDistinctMarks_inBothThemes() {
        val light = listOf(MetricAccents.hrvLight, MetricAccents.restingHrLight, MetricAccents.sleepLight, MetricAccents.respiratoryLight, MetricAccents.strainLight)
        val dark = listOf(MetricAccents.hrvDark, MetricAccents.restingHrDark, MetricAccents.sleepDark, MetricAccents.respiratoryDark, MetricAccents.strainDark)
        light.forEach { mark("light accent $it", it, LightColors.surface) }
        dark.forEach {
            mark("dark accent $it on surface", it, DarkColors.surface)
            mark("dark accent $it on surfaceContainerHigh", it, DarkColors.surfaceContainerHigh)
        }
        assertTrue("each signal has its own colour", light.toSet().size == light.size && dark.toSet().size == dark.size)
        assertNoFailures()
    }

    @Test
    fun theCompletionCheck_andADoneRow_stayReadable() {
        val lightGreen = SpartanZones.greenMarkLight
        val darkGreen = SpartanBands.primedDark
        mark("light check circle on surface", lightGreen, LightColors.surface)
        mark("light check mark on the circle", Color.White, lightGreen)
        mark("dark check circle on surface", darkGreen, DarkColors.surface)
        mark("dark check mark on the circle", Color(0xFF04211D), darkGreen)
        mark("unchecked outline, light", LightColors.onSurfaceVariant, LightColors.surface)
        mark("unchecked outline, dark", DarkColors.onSurfaceVariant, DarkColors.surface)
        // A done row keeps its normal text colours on the faint green tint.
        val lightDone = tint(lightGreen, 0.08f, LightColors.surface)
        val darkDone = tint(darkGreen, 0.12f, DarkColors.surface)
        text("light done title", LightColors.onSurface, lightDone)
        text("light done meta", LightColors.onSurfaceVariant, lightDone)
        text("dark done title", DarkColors.onSurface, darkDone)
        text("dark done meta", DarkColors.onSurfaceVariant, darkDone)
        assertNoFailures()
    }
}
