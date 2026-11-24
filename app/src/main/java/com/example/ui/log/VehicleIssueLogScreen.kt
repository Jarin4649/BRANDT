package com.example.ui.log

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.rounded.QuestionAnswer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.AttachmentKind
import com.example.data.model.VehicleDiagnosisLog
import com.example.data.settings.LocalAppLanguage
import com.example.data.settings.MotoraStrings
import com.example.data.settings.appStrings
import com.example.ui.components.LanguageSettingsActionButton
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleIssueLogScreen(
    viewModel: VehicleIssueLogViewModel,
    onNavigateBack: () -> Unit,
    onOpenAiChat: (initialTopic: String?) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val isMutating by viewModel.isMutating.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val strings = appStrings(LocalAppLanguage.current)

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = Color.White,
        contentWindowInsets = WindowInsets.navigationBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = strings.logTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val v = (uiState as? VehicleIssueLogUiState.Ready)?.vehicle
                        if (v != null) {
                            Text(
                                text = "${v.modelYear} ${v.manufacturer} ${v.model}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF374151),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.chatBackDesc,
                            tint = Color.Black
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onOpenAiChat(null) },
                        modifier = Modifier.testTag("log_switch_to_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SmartToy,
                            contentDescription = strings.logAskAiActionDesc,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            strings.logAskAiAction,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.Black
                        )
                    }
                    LanguageSettingsActionButton()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is VehicleIssueLogUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is VehicleIssueLogUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is VehicleIssueLogUiState.Ready -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Filter row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IssueLogFilter.entries.forEach { filter ->
                                FilterChip(
                                    selected = state.activeFilter == filter,
                                    onClick = { viewModel.setFilter(filter) },
                                    label = {
                                        Text(
                                            text = strings.filterLabel(filter.name),
                                            color = Color.Black
                                        )
                                    },
                                    modifier = Modifier.testTag("log_filter_${filter.name}")
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = strings.logCountLabel(state.filteredLogs.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.Black
                            )
                        }

                        if (state.filteredLogs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(24.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.2.dp, Color(0xFFD1D5DB)),
                                    modifier = Modifier
                                        .widthIn(max = 460.dp)
                                        .fillMaxWidth()
                                        .testTag("log_empty_state")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = Color.White,
                                            border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.FactCheck,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (state.allLogs.isEmpty()) {
                                                strings.logEmptyNoEntries
                                            } else {
                                                strings.logEmptyFiltered
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = strings.logEmptyDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF111827)
                                        )
                                        Button(
                                            onClick = { onOpenAiChat(null) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.testTag("log_empty_ask_ai_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.SmartToy,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(strings.logEmptyStartAi)
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("log_list"),
                                contentPadding = PaddingValues(top = 6.dp, bottom = 28.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.filteredLogs, key = { it.id }) { log ->
                                    IssueLogCard(
                                        log = log,
                                        strings = strings,
                                        isMutating = isMutating,
                                        onToggleResolved = { viewModel.toggleResolved(log) },
                                        onAskMore = { onOpenAiChat("${strings.logAskMoreTopicPrefix} ${log.userPrompt}") },
                                        onDelete = { viewModel.deleteLog(log.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IssueLogCard(
    log: VehicleDiagnosisLog,
    strings: MotoraStrings,
    isMutating: Boolean,
    onToggleResolved: () -> Unit,
    onAskMore: () -> Unit,
    onDelete: () -> Unit
) {
    val dateText = remember(log.createdAt, strings.language) {
        log.createdAt?.toDate()?.let {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
        } ?: strings.logSyncingDate
    }
    val attachmentKind = log.resolvedAttachmentKind()
    val kindLabel = strings.attachmentKindLabel(attachmentKind)

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        border = BorderStroke(
            1.2.dp,
            if (log.resolved) Color(0xFFE5E7EB) else Color(0xFFD1D5DB)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_card_${log.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (attachmentKind) {
                                AttachmentKind.PHOTO -> Icons.Filled.AddPhotoAlternate
                                AttachmentKind.FILE -> Icons.Filled.AttachFile
                                AttachmentKind.AUDIO -> Icons.Filled.GraphicEq
                                AttachmentKind.VIDEO -> Icons.Filled.Videocam
                                AttachmentKind.TEXT_ONLY -> Icons.Filled.ChatBubbleOutline
                            },
                            contentDescription = kindLabel,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (log.attachmentLabel.isNotBlank()) {
                            "$kindLabel • ${log.attachmentLabel}"
                        } else {
                            kindLabel
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF374151)
                    )
                }

                if (log.modelCommonality.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFEF3C7)
                    ) {
                        Text(
                            text = log.modelCommonality,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Black,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Text(
                text = "${strings.logQueryPrefix} ${log.userPrompt}",
                style = MaterialTheme.typography.titleSmall,
                color = Color.Black
            )

            Text(
                text = log.aiSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF111827)
            )

            if (log.followUpQuestion.isNotBlank()) {
                Text(
                    text = "${strings.logFollowUpPrefix} ${log.followUpQuestion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1D4ED8)
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onToggleResolved,
                    enabled = !isMutating,
                    border = BorderStroke(1.dp, Color(0xFFD1D5DB)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("log_toggle_resolved_${log.id}")
                ) {
                    Icon(
                        imageVector = if (log.resolved) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (log.resolved) Color(0xFF15803D) else Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (log.resolved) strings.logStatusResolved else strings.logStatusOpen,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black
                    )
                }

                TextButton(
                    onClick = onAskMore,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("log_ask_more_${log.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QuestionAnswer,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        strings.logAskMore,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = onDelete,
                    enabled = !isMutating,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("log_delete_${log.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = strings.logDeleteDesc,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
