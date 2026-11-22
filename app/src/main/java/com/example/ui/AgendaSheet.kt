package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AgendaItem
import com.example.data.AppCategories
import com.example.data.Formatters
import com.example.data.TaskPriority
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AgendaEditorSheet(
    existingItem: AgendaItem?,
    defaultDateKey: String,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        notes: String,
        dateKey: String,
        startTime: String?,
        endTime: String?,
        priority: TaskPriority,
        category: String
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember(existingItem) { mutableStateOf(existingItem?.title.orEmpty()) }
    var notes by remember(existingItem) { mutableStateOf(existingItem?.notes.orEmpty()) }
    var dateKey by remember(existingItem, defaultDateKey) {
        mutableStateOf(existingItem?.dateKey ?: defaultDateKey)
    }
    var hasTime by remember(existingItem) { mutableStateOf(existingItem?.hasScheduleTime ?: false) }
    var startTime by remember(existingItem) { mutableStateOf(existingItem?.startTime ?: "09:00") }
    var endTime by remember(existingItem) { mutableStateOf(existingItem?.endTime ?: "10:00") }
    var priority by remember(existingItem) {
        mutableStateOf(existingItem?.taskPriority ?: TaskPriority.MEDIUM)
    }
    var category by remember(existingItem) {
        mutableStateOf(existingItem?.category ?: AppCategories.taskCategories.first())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (existingItem == null) "Tambah Tugas / Jadwal" else "Edit Agenda",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (existingItem != null && onDelete != null) {
                    IconButton(
                        onClick = onDelete,
                        enabled = !isSaving,
                        modifier = Modifier.testTag("delete_agenda_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it.take(120)
                    validationError = null
                },
                label = { Text("Judul Tugas atau Jadwal") },
                placeholder = { Text("Contoh: Bayar tagihan internet / Rapat klien") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agenda_title_input")
            )

            // Date selector
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        val cal = Formatters.parseDateKey(dateKey) ?: Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                dateKey = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                    .testTag("agenda_date_picker")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = Formatters.formatFullDateHeader(dateKey),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Toggle Schedule Time Marker
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Penanda Waktu Jadwal",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Aktifkan untuk menjadikannya jadwal berwaktu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = hasTime,
                            onCheckedChange = { hasTime = it },
                            modifier = Modifier.testTag("agenda_time_switch")
                        )
                    }

                    AnimatedVisibility(visible = hasTime) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TimePickerTile(
                                label = "Mulai",
                                value = startTime,
                                onClick = {
                                    val parts = startTime.split(":")
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            startTime = String.format(Locale.US, "%02d:%02d", h, m)
                                        },
                                        parts.getOrNull(0)?.toIntOrNull() ?: 9,
                                        parts.getOrNull(1)?.toIntOrNull() ?: 0,
                                        true
                                    ).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("agenda_start_time_picker")
                            )
                            TimePickerTile(
                                label = "Selesai",
                                value = endTime,
                                onClick = {
                                    val parts = endTime.split(":")
                                    TimePickerDialog(
                                        context,
                                        { _, h, m ->
                                            endTime = String.format(Locale.US, "%02d:%02d", h, m)
                                        },
                                        parts.getOrNull(0)?.toIntOrNull() ?: 10,
                                        parts.getOrNull(1)?.toIntOrNull() ?: 0,
                                        true
                                    ).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("agenda_end_time_picker")
                            )
                        }
                    }
                }
            }

            // Priority selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prioritas", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (p) {
                                    TaskPriority.HIGH -> MaterialTheme.colorScheme.errorContainer
                                    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
                                    TaskPriority.LOW -> MaterialTheme.colorScheme.primaryContainer
                                },
                                selectedLabelColor = when (p) {
                                    TaskPriority.HIGH -> MaterialTheme.colorScheme.onErrorContainer
                                    TaskPriority.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
                                    TaskPriority.LOW -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            ),
                            modifier = Modifier.testTag("agenda_pick_priority_${p.name.lowercase()}")
                        )
                    }
                }
            }

            // Category selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Kategori", style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppCategories.taskCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.take(400) },
                label = { Text("Catatan tambahan (opsional)") },
                maxLines = 3,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agenda_notes_input")
            )

            if (validationError != null) {
                Text(
                    text = validationError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isEmpty()) {
                        validationError = "Judul tugas atau jadwal wajib diisi."
                    } else {
                        onSave(
                            cleanTitle,
                            notes,
                            dateKey,
                            if (hasTime) startTime else null,
                            if (hasTime) endTime else null,
                            priority,
                            category
                        )
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("agenda_save_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (existingItem == null) "Simpan Agenda" else "Perbarui Agenda",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TimePickerTile(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
