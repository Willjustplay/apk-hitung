package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EventNote
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AgendaItem
import com.example.data.Formatters
import com.example.data.TaskPriority
import com.example.data.UiState
import com.example.ui.theme.MonoTimeStyle
import com.example.viewmodel.DayAgendaView
import com.example.viewmodel.TaskStatusFilter

@Composable
fun AgendaScreen(
    tasksState: UiState<List<AgendaItem>>,
    dayView: DayAgendaView,
    statusFilter: TaskStatusFilter,
    priorityFilter: TaskPriority?,
    onSelectDate: (String) -> Unit,
    onJumpToday: () -> Unit,
    onStatusFilterChange: (TaskStatusFilter) -> Unit,
    onPriorityFilterToggle: (TaskPriority) -> Unit,
    onAddAgenda: () -> Unit,
    onToggleCompleted: (AgendaItem) -> Unit,
    onEditAgenda: (AgendaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayKey = remember { Formatters.todayDateKey() }
    val dateStrip = remember {
        (-3..10).map { offset -> Formatters.dateKeyOffsetFromToday(offset) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tugas & Jadwal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = Formatters.formatFullDateHeader(dayView.selectedDateKey),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (dayView.selectedDateKey != todayKey) {
                    TextButton(
                        onClick = onJumpToday,
                        modifier = Modifier.testTag("agenda_jump_today_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Today,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hari Ini")
                    }
                }
            }
        }

        // Scrollable 14-day Date Strip
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(dateStrip, key = { it }) { dateKey ->
                    val isSelected = dateKey == dayView.selectedDateKey
                    val isToday = dateKey == todayKey
                    val hasItems = dateKey in dayView.datesWithItems
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .width(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectDate(dateKey) }
                            .testTag("date_strip_$dateKey")
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = Formatters.formatShortDayName(dateKey),
                                style = MaterialTheme.typography.labelSmall,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = Formatters.formatDayNumber(dateKey),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !hasItems -> androidx.compose.ui.graphics.Color.Transparent
                                            isSelected -> MaterialTheme.colorScheme.onPrimary
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }

        // Day Progress Banner
        if (dayView.totalDayItems > 0) {
            item {
                val ratio = dayView.completedDayItems.toFloat() / dayView.totalDayItems.toFloat()
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Progres Harian",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${dayView.completedDayItems}/${dayView.totalDayItems} selesai (${(ratio * 100).toInt()}%)",
                                style = MonoTimeStyle,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { ratio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(CircleShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }

        // Status & Priority Filters
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(TaskStatusFilter.entries) { filter ->
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = { onStatusFilterChange(filter) },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("agenda_status_${filter.name.lowercase()}")
                    )
                }
                items(TaskPriority.entries) { priority ->
                    FilterChip(
                        selected = priorityFilter == priority,
                        onClick = { onPriorityFilterToggle(priority) },
                        label = { Text("Prioritas ${priority.label}") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (priority) {
                                TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer
                                TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                                TaskPriority.LOW -> MaterialTheme.colorScheme.primaryContainer
                            },
                            selectedLabelColor = when (priority) {
                                TaskPriority.HIGH -> MaterialTheme.colorScheme.onErrorContainer
                                TaskPriority.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
                                TaskPriority.LOW -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        ),
                        modifier = Modifier.testTag("agenda_priority_${priority.name.lowercase()}")
                    )
                }
            }
        }

        // Content State
        when (tasksState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 36.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }
            is UiState.Error -> {
                item { ErrorBanner(tasksState.message) }
            }
            is UiState.Success -> {
                if (dayView.schedules.isEmpty() && dayView.tasks.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Rounded.EventNote,
                            title = "Jadwal & tugas masih kosong",
                            subtitle = "Atur jadwal berwaktu atau daftar tugas berprioritas untuk tanggal ini.",
                            actionLabel = "Tambah Agenda",
                            onAction = onAddAgenda,
                            testTag = "agenda_empty_state"
                        )
                    }
                } else {
                    // Timed Schedule Section
                    if (dayView.schedules.isNotEmpty()) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Jadwal Berwaktu (${dayView.schedules.size})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        items(dayView.schedules, key = { "sched_${it.id}" }) { item ->
                            ScheduleTimelineCard(
                                item = item,
                                onToggle = { onToggleCompleted(item) },
                                onClick = { onEditAgenda(item) }
                            )
                        }
                    }

                    // Untimed Tasks Section
                    if (dayView.tasks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Daftar Tugas (${dayView.tasks.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(top = if (dayView.schedules.isNotEmpty()) 6.dp else 0.dp)
                            )
                        }
                        items(dayView.tasks, key = { "task_${it.id}" }) { item ->
                            TaskItemCard(
                                item = item,
                                onToggle = { onToggleCompleted(item) },
                                onClick = { onEditAgenda(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleTimelineCard(
    item: AgendaItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Left Time Column
        Column(
            modifier = Modifier
                .width(58.dp)
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = item.startTime.orEmpty(),
                style = MonoTimeStyle.copy(fontWeight = FontWeight.Bold),
                color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
            )
            if (!item.endTime.isNullOrBlank()) {
                Text(
                    text = item.endTime,
                    style = MonoTimeStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Timeline Card
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (item.completed) MaterialTheme.colorScheme.outlineVariant
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            ),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .testTag("schedule_item_${item.id}")
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.completed) MaterialTheme.colorScheme.outline
                            else priorityTint(item.taskPriority)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.notes.isNotBlank()) {
                        Text(
                            text = item.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PriorityMiniBadge(priority = item.taskPriority)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .clickable(onClick = onToggle)
                        .testTag("toggle_schedule_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.cd_toggle_task),
                        tint = if (item.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskItemCard(
    item: AgendaItem,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("task_item_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(onClick = onToggle)
                    .testTag("toggle_task_${item.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = stringResource(R.string.cd_toggle_task),
                    tint = if (item.completed) MaterialTheme.colorScheme.primary else priorityTint(item.taskPriority),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        textDecoration = if (item.completed) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.notes.isNotBlank()) {
                    Text(
                        text = item.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PriorityMiniBadge(priority = item.taskPriority)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
