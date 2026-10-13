package com.closecode.ui.project

import com.closecode.ui.model.ProjectConfig
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.model.TemplateTab
import org.junit.Assert.assertTrue
import org.junit.Test

class ScaffoldTest {

    @Test
    fun blankAndroidScaffoldHasMatchingThemeAndComposableBuildConfiguration() {
        val files = Scaffold.filesFor(
            projectName = "Blank App",
            selection = SelectedTemplate(TemplateTab.App, 0),
            config = ProjectConfig(packageName = "com.closecode.blankapp"),
        ).associate { it.path to it.content }

        val manifest = files.getValue("app/src/main/AndroidManifest.xml")
        val styles = files.getValue("app/src/main/res/values/themes.xml")
        val appGradle = files.getValue("app/build.gradle.kts")
        val activity = files.getValue("app/src/main/java/com/closecode/blankapp/MainActivity.kt")

        assertTrue(manifest.contains("android:theme=\"@style/Theme.App\""))
        assertTrue(styles.contains("<style name=\"Theme.App\""))
        assertTrue(appGradle.contains("buildFeatures {\n        compose = true"))
        assertTrue(appGradle.contains("kotlinCompilerExtensionVersion = \"1.5.14\""))
        assertTrue(activity.contains("import androidx.compose.material3.Text"))
    }
}
