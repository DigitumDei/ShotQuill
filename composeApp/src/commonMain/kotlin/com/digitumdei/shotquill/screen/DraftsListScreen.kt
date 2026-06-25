package com.digitumdei.shotquill.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.digitumdei.shotquill.shared.domain.DraftSummary
import com.digitumdei.shotquill.shared.storage.ManualWorkflowRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

@Composable
fun DraftsListScreen(
    manualWorkflowRepository: ManualWorkflowRepository?,
    onNavigateToDraft: (String) -> Unit,
    onNavigateToNewPost: () -> Unit,
) {
    var drafts by remember { mutableStateOf<List<DraftSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (manualWorkflowRepository == null) {
            loadError = "Draft repository not available"
            loaded = true
            return@LaunchedEffect
        }
        val result = withContext(Dispatchers.IO) {
            manualWorkflowRepository.listPostDrafts()
        }
        drafts = result
        loaded = true
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My Drafts", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onNavigateToNewPost) {
                Text("New Post")
            }
        }

        if (!loaded) {
            Text("Loading drafts...", style = MaterialTheme.typography.bodyMedium)
        } else if (loadError != null) {
            Text(loadError!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = onNavigateToNewPost) {
                Text("Back")
            }
        } else if (drafts.isEmpty()) {
            Text("No drafts yet. Create a new post to get started.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(drafts) { draft ->
                    DraftListItem(
                        draft = draft,
                        onClick = { onNavigateToDraft(draft.id.value) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftListItem(
    draft: DraftSummary,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (draft.primaryMediaUri != null) {
            AsyncImage(
                model = draft.primaryMediaUri,
                contentDescription = "Draft photo",
                modifier = Modifier.size(64.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Spacer(modifier = Modifier.size(64.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(draft.status.wireValue, style = MaterialTheme.typography.titleSmall)
            draft.captionText?.let {
                Text(it.take(80), style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = Instant.fromEpochMilliseconds(draft.updatedAtEpochMillis).toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
