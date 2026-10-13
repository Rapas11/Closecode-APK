package com.closecode

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.closecode.ui.RecentProjectsScreen
import com.closecode.ui.model.RecentProject
import com.closecode.ui.model.SelectedTemplate
import com.closecode.ui.project.ProjectRecord
import com.closecode.ui.project.ProjectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberProjectRepository(): ProjectRepository {
    val context: Context = LocalContext.current
    return remember(context) { ProjectRepository(context) }
}

fun ProjectRecord.toRecent(): RecentProject = RecentProject(
    slug = slug,
    name = name,
    selection = SelectedTemplate(tab, templateIndex),
    templateName = templateName,
    lastOpened = lastOpened,
)

class RecentListState(
    private val repository: ProjectRepository,
) {
    var projects by mutableStateOf<List<RecentProject>>(emptyList())
        private set
    private var loaded by mutableStateOf(false)

    fun refresh() {
        projects = repository.listProjects().map { it.toRecent() }
    }

    /** Reads disk only when the list is first built, and never from the composition path. */
    suspend fun refreshOnce() {
        if (loaded) return
        loaded = true
        projects = withContext(Dispatchers.IO) { repository.listProjects().map { it.toRecent() } }
    }

    fun delete(slug: String) {
        repository.delete(slug)
        refresh()
    }

    fun open(slug: String): ProjectRecord? {
        val record = repository.touch(slug)
        refresh()
        return record
    }
}

@Composable
fun RecentProjectsRoute(
    repository: ProjectRepository,
    onOpen: (ProjectRecord) -> Unit,
    onBack: () -> Unit,
) {
    val state = remember(repository) { RecentListState(repository) }
    LaunchedEffect(state) { state.refreshOnce() }

    RecentProjectsScreen(
        projects = state.projects,
        onOpen = { project -> state.open(project.slug)?.let(onOpen) },
        onDelete = { project -> state.delete(project.slug) },
        onBack = onBack,
    )
}

