package com.closecode.ui.github

import com.closecode.ui.git.GitService
import java.io.File
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubRemoteTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun commitPushUpdatesTrackingCountsAndKeepsTokenOutOfGitConfig() {
        val remoteDir = temporaryFolder.newFolder("remote.git")
        Git.init().setDirectory(remoteDir).setBare(true).call().close()
        val projectDir = temporaryFolder.newFolder("project")
        val git = GitService(projectDir)
        git.init().getOrThrow()
        write(projectDir, "Main.kt", "fun main() = Unit\n")
        git.stage(listOf("Main.kt")).getOrThrow()
        git.commit("Add entry point").getOrThrow()
        configureOrigin(projectDir, remoteDir)

        val remote = GitHubRemote(projectDir)
        assertTrue(remote.push("test-access-token").getOrThrow().startsWith("Pushed "))

        val afterFirstPush = git.snapshot().getOrThrow()
        assertEquals(0, afterFirstPush.ahead)
        assertEquals(0, afterFirstPush.behind)

        assertEquals("Nothing to push.", remote.push("test-access-token").getOrThrow())

        write(projectDir, "Main.kt", "fun main() = println(\"updated\")\n")
        git.stage(listOf("Main.kt")).getOrThrow()
        git.commit("Update entry point").getOrThrow()
        assertEquals(1, git.snapshot().getOrThrow().ahead)
        remote.push("test-access-token").getOrThrow()
        assertEquals(0, git.snapshot().getOrThrow().ahead)

        val gitConfig = File(projectDir, ".git/config").readText()
        assertFalse(gitConfig.contains("test-access-token"))
        Git.open(remoteDir).use { bare ->
            assertTrue(bare.repository.resolve("refs/heads/main") != null)
        }
    }

    @Test
    fun pushWithoutACommitReportsTheActionNeeded() {
        val projectDir = temporaryFolder.newFolder("empty-project")
        GitService(projectDir).init().getOrThrow()

        val result = GitHubRemote(projectDir).push("test-access-token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Make a commit first"))
    }

    @Test
    fun pullBringsRemoteCommitIntoProjectAndReportsAnUpToDatePullClearly() {
        val remoteDir = temporaryFolder.newFolder("pull-remote.git")
        Git.init().setDirectory(remoteDir).setBare(true).call().close()
        val seedDir = temporaryFolder.newFolder("seed")
        val seed = GitService(seedDir)
        seed.init().getOrThrow()
        write(seedDir, "Main.kt", "fun main() = Unit\n")
        seed.stage(listOf("Main.kt")).getOrThrow()
        seed.commit("Initial commit").getOrThrow()
        configureOrigin(seedDir, remoteDir)
        GitHubRemote(seedDir).push("test-access-token").getOrThrow()

        val projectDir = temporaryFolder.newFolder("pull-project")
        Git.cloneRepository()
            .setURI(remoteDir.absolutePath)
            .setDirectory(projectDir)
            .call()
            .close()
        write(seedDir, "Main.kt", "fun main() = println(\"new remote code\")\n")
        seed.stage(listOf("Main.kt")).getOrThrow()
        seed.commit("Remote update").getOrThrow()
        GitHubRemote(seedDir).push("test-access-token").getOrThrow()

        val remote = GitHubRemote(projectDir)
        assertEquals("Pulled remote changes.", remote.pull("test-access-token").getOrThrow())
        assertTrue(File(projectDir, "Main.kt").readText().contains("new remote code"))
        assertEquals(0, GitService(projectDir).snapshot().getOrThrow().behind)
        assertEquals("Already up to date.", remote.pull("test-access-token").getOrThrow())
    }

    private fun write(root: File, path: String, text: String) {
        File(root, path).writeText(text)
    }

    private fun configureOrigin(projectDir: File, remoteDir: File) {
        Git.open(projectDir).use { git ->
            val config = git.repository.config
            config.setString("remote", "origin", "url", remoteDir.absolutePath)
            config.setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*")
            config.save()
        }
    }
}
