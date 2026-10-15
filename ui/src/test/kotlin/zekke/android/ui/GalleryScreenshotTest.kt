package zekke.android.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import zekke.android.ui.theme.ZekkeTheme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-xxhdpi")
class GalleryScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun light() {
        compose.setContent { ZekkeTheme(darkTheme = false) { DesignGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/gallery-light.png")
    }

    @Test
    fun dark() {
        compose.setContent { ZekkeTheme(darkTheme = true) { DesignGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/gallery-dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1300dp-xxhdpi")
    fun componentsLight() {
        compose.setContent { ZekkeTheme(darkTheme = false) { ComponentGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/components-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1300dp-xxhdpi")
    fun componentsDark() {
        compose.setContent { ZekkeTheme(darkTheme = true) { ComponentGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/components-dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1250dp-xxhdpi")
    fun phraseEntryLight() {
        compose.setContent { ZekkeTheme(darkTheme = false) { PhraseEntryGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/phrase-entry-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h1250dp-xxhdpi")
    fun phraseEntryDark() {
        compose.setContent { ZekkeTheme(darkTheme = true) { PhraseEntryGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/phrase-entry-dark.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h520dp-xxhdpi")
    fun scannerLight() {
        compose.setContent { ZekkeTheme(darkTheme = false) { ScannerGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/scanner-light.png")
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h520dp-xxhdpi")
    fun scannerDark() {
        compose.setContent { ZekkeTheme(darkTheme = true) { ScannerGallery() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/scanner-dark.png")
    }
}
