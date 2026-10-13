package com.closecode.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closecode.ui.components.ArrowGlyph
import com.closecode.ui.model.RecentProject
import com.closecode.ui.model.relativeTime
import com.closecode.ui.theme.LocalPageColors
import com.closecode.ui.theme.Sans

@Composable
fun RecentProjectsScreen(
    projects: List<RecentProject>,
    onOpen: (RecentProject) -> Unit,
    onDelete: (RecentProject) -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalPageColors.current
    var pendingDelete by remember { mutableStateOf<RecentProject?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.cd)
                    .border(1.dp, c.ln, RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "‹", color = c.mu, fontSize = 26.sp, fontFamily = Sans)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    text = "Recent projects",
                    color = c.tx,
                    fontSize = 22.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 26.sp,
                )
                Text(
                    text = when (projects.size) {
                        0 -> "Nothing here yet."
                        1 -> "1 project on this device."
                        else -> "${projects.size} projects on this device."
                    },
                    color = c.mu,
                    fontSize = 14.5.sp,
                    fontFamily = Sans,
                    lineHeight = 21.sp,
                )
            }
        }

        if (projects.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Projects you create will show up here.",
                    color = c.mu,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            }
        } else {
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 18.dp),
            ) {
                items(projects, key = { it.slug }) { project ->
                    RecentRow(
                        project = project,
                        onClick = { onOpen(project) },
                        onDelete = { pendingDelete = project },
                    )
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    text = "Delete ${target.name}?",
                    color = c.tx,
                    fontSize = 17.sp,
                    fontFamily = Sans,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = "This removes the project folder and everything in it. It cannot be undone.",
                    color = c.mu,
                    fontSize = 14.sp,
                    fontFamily = Sans,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    pendingDelete = null
                }) {
                    Text(
                        text = "Delete",
                        color = c.red,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(
                        text = "Cancel",
                        color = c.mu,
                        fontSize = 15.sp,
                        fontFamily = Sans,
                    )
                }
            },
            containerColor = c.cd,
        )
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentRow(
    project: RecentProject,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 5.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.cd)
            .border(1.dp, c.ln, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.ac.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = project.name.firstOrNull()?.uppercase() ?: "?",
                color = c.ac,
                fontSize = 17.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .padding(start = 13.dp)
        ) {
            Text(
                text = project.name,
                color = c.tx,
                fontSize = 16.sp,
                fontFamily = Sans,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${project.templateName} · ${relativeTime(project.lastOpened)}",
                color = c.mu,
                fontSize = 13.sp,
                fontFamily = Sans,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ArrowGlyph(color = c.mu)
    }
}
