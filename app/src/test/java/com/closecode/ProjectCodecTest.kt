package com.closecode

import com.closecode.ui.model.BuildTypeOptions
import com.closecode.ui.model.GradleOptions
import com.closecode.ui.model.LanguageOptions
import com.closecode.ui.model.MinSdkOptions
import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SdkOptions
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import com.closecode.ui.model.defaultPackageName
import com.closecode.ui.model.relativeTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectCodecTest {

    private fun roundTrip(name: String, tab: TemplateTab, index: Int, template: String) {
        val project = Project(name, SelectedTemplate(tab, index), template)
        val decoded = ProjectCodec.decode(ProjectCodec.encode(project))
        assertEquals(name, decoded?.name)
        assertEquals(tab, decoded?.selection?.tab)
        assertEquals(index, decoded?.selection?.index)
        assertEquals(template, decoded?.templateName)
        assertEquals(Project.slugify(name), decoded?.slug)
    }

    @Test
    fun plainNameRoundTrips() {
        roundTrip("Notes", TemplateTab.App, 2, "Notes")
    }

    @Test
    fun nameWithSpacesRoundTrips() {
        roundTrip("My Notes", TemplateTab.App, 2, "To-do list")
    }

    @Test
    fun nameWithSeveralSpacesRoundTrips() {
        roundTrip("My  Big   App", TemplateTab.Web, 5, "Documentation")
    }

    @Test
    fun everyTemplateNameRoundTrips() {
        roundTrip("Project", TemplateTab.App, 0, "Blank app")
        roundTrip("Project", TemplateTab.App, 1, "To-do list")
        roundTrip("Project", TemplateTab.App, 5, "Tip calculator")
        roundTrip("Project", TemplateTab.Web, 0, "Landing page")
        roundTrip("Project", TemplateTab.Web, 3, "Online store")
    }

    @Test
    fun unicodeAndSymbolsRoundTrips() {
        roundTrip("Café Notes ✦", TemplateTab.App, 0, "Blank app")
        roundTrip("a|b", TemplateTab.Web, 1, "Portfolio")
        roundTrip("100% done!", TemplateTab.App, 3, "Weather")
    }

    @Test
    fun malformedInputReturnsNullInsteadOfThrowing() {
        assertNull(ProjectCodec.decode(""))
        assertNull(ProjectCodec.decode("   "))
        assertNull(ProjectCodec.decode("only-one-field"))
        assertNull(ProjectCodec.decode("a|b|c"))
        assertNull(ProjectCodec.decode("name|Bogus|0|Blank app|"))
        assertNull(ProjectCodec.decode("name|App|not-a-number|Blank app|"))
    }

    @Test
    fun configSurvivesTheTrip() {
        val config = ProjectConfig(
            packageName = "com.closecode.notes",
            language = "Java",
            gradleVersion = "8.9",
            minSdk = "24",
            compileSdk = "34",
            targetSdk = "36",
            buildType = "Release",
        )
        val project = Project("Notes", SelectedTemplate(TemplateTab.App, 2), "Notes", config)
        val decoded = ProjectCodec.decode(ProjectCodec.encode(project))
        assertEquals(config, decoded?.config)
    }

    @Test
    fun defaultConfigSurvivesTheTrip() {
        val project = Project("Notes", SelectedTemplate(TemplateTab.App, 2), "Notes")
        val decoded = ProjectCodec.decode(ProjectCodec.encode(project))
        assertEquals(ProjectConfig(), decoded?.config)
    }

    @Test
    fun everyConfigOptionValueRoundTrips() {
        LanguageOptions.forEach { language ->
            GradleOptions.forEach { gradle ->
                MinSdkOptions.forEach { min ->
                    SdkOptions.forEach { sdk ->
                        BuildTypeOptions.forEach { build ->
                            val config = ProjectConfig(
                                packageName = defaultPackageName("My App"),
                                language = language,
                                gradleVersion = gradle,
                                minSdk = min,
                                compileSdk = sdk,
                                targetSdk = sdk,
                                buildType = build,
                            )
                            val project =
                                Project("My App", SelectedTemplate(TemplateTab.App, 0), "Blank app", config)
                            assertEquals(
                                config,
                                ProjectCodec.decode(ProjectCodec.encode(project))?.config,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun configWithSeparatorCharacterSurvives() {
        val config = ProjectConfig(packageName = "com.closecode.a|b")
        val project = Project("X", SelectedTemplate(TemplateTab.App, 0), "Blank app", config)
        assertEquals(config, ProjectCodec.decode(ProjectCodec.encode(project))?.config)
    }

    @Test
    fun defaultPackageNameIsSlugified() {
        assertEquals("com.closecode.notes", defaultPackageName("Notes"))
        assertEquals("com.closecode.myapp", defaultPackageName("My App"))
        assertEquals("com.closecode.a1b2", defaultPackageName("a-1_B 2"))
        assertEquals("com.closecode.myproject", defaultPackageName("!!!"))
    }

    @Test
    fun relativeTimeReadsNaturally() {
        val now = 10_000_000_000L
        assertEquals("Just now", relativeTime(now, now))
        assertEquals("5 min ago", relativeTime(now - 5 * 60_000L, now))
        assertEquals("1 hour ago", relativeTime(now - 3_600_000L, now))
        assertEquals("3 hours ago", relativeTime(now - 3 * 3_600_000L, now))
        assertEquals("Yesterday", relativeTime(now - 86_400_000L, now))
        assertEquals("3 days ago", relativeTime(now - 3 * 86_400_000L, now))
        assertEquals("1 week ago", relativeTime(now - 7 * 86_400_000L, now))
        assertEquals("2 weeks ago", relativeTime(now - 14 * 86_400_000L, now))
    }

    @Test
    fun futureTimestampsClampToJustNow() {
        val now = 10_000_000_000L
        assertEquals("Just now", relativeTime(now + 60_000L, now))
    }

    @Test
    fun everyTemplateStillRoundTripsThroughItsSlug() {
        TemplateTab.entries.forEach { tab ->
            com.closecode.ui.model.Templates[tab].orEmpty().forEachIndexed { index, template ->
                val record = Project(
                    template.name,
                    SelectedTemplate(tab, index),
                    template.name,
                    ProjectConfig(),
                )
                val decoded = ProjectCodec.decode(ProjectCodec.encode(record))
                assertEquals(template.name, decoded?.name)
                assertEquals(index, decoded?.selection?.index)
                assertEquals(Project.slugify(template.name), decoded?.slug)
            }
        }
    }
}