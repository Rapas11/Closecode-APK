package com.closecode.ui.ide

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackToEditorTest {
    @Test
    fun overlayPermissionIsAskedOnlyOnceWhenNotAlreadyGranted() {
        assertTrue(
            shouldAskForOverlayPermission(
                Build.VERSION_CODES.M,
                canDrawOverlay = false,
                permissionAskedBefore = false,
            ),
        )
        assertFalse(
            shouldAskForOverlayPermission(
                Build.VERSION_CODES.M,
                canDrawOverlay = false,
                permissionAskedBefore = true,
            ),
        )
        assertFalse(
            shouldAskForOverlayPermission(
                Build.VERSION_CODES.M,
                canDrawOverlay = true,
                permissionAskedBefore = false,
            ),
        )
        assertFalse(
            shouldAskForOverlayPermission(
                Build.VERSION_CODES.LOLLIPOP_MR1,
                canDrawOverlay = false,
                permissionAskedBefore = false,
            ),
        )
    }
}
