package com.closecode.build

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStateTest {

    @Test
    fun successfulInstallRequiresTheSystemToNameTheInstalledPackage() {
        val current = InstallState.Installing(7)

        assertEquals(
            InstallState.Installed("com.example.app"),
            nextInstallState(
                current,
                InstallEvent.SystemStatus(PackageInstaller.STATUS_SUCCESS, "com.example.app", null),
            ),
        )
        val missingPackage = nextInstallState(
            current,
            InstallEvent.SystemStatus(PackageInstaller.STATUS_SUCCESS, null, null),
        )
        assertTrue(missingPackage is InstallState.Failed)
        assertTrue((missingPackage as InstallState.Failed).message.contains("did not identify"))
    }

    @Test
    fun pendingUserConfirmationLeavesInstallPendingForTheSystemPrompt() {
        val current = InstallState.Installing(8)

        val next = nextInstallState(
            current,
            InstallEvent.SystemStatus(
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                null,
                null,
            ),
        )

        assertSame(current, next)
    }

    @Test
    fun cancellationAndSignatureConflictAreExplainedInPlainWords() {
        val cancelled = nextInstallState(
            InstallState.Installing(9),
            InstallEvent.SystemStatus(PackageInstaller.STATUS_FAILURE_ABORTED, null, null),
        )
        val conflict = nextInstallState(
            InstallState.Installing(10),
            InstallEvent.SystemStatus(PackageInstaller.STATUS_FAILURE_CONFLICT, null, "conflict"),
        )

        assertEquals("Installation was cancelled.", (cancelled as InstallState.Failed).message)
        assertTrue((conflict as InstallState.Failed).message.contains("Uninstall it first"))
    }
}
