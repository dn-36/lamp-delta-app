package com.example.lampappdelta.detail_screen

import android.os.Build
import androidx.compose.material3.Switch
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import java.time.LocalTime
import java.time.format.DateTimeFormatter


private data class LedInterval(
    val start: LocalTime,
    val end: LocalTime,
    val brightness: Int
)

@RequiresApi(Build.VERSION_CODES.O)
private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@RequiresApi(Build.VERSION_CODES.O)
private fun LocalTime.formatHHmm(): String = format(timeFmt)

class DetailScreen(
    val idLamp: Int
) : Screen {

    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    override fun Content() {

        var d1 by remember { mutableIntStateOf(35) }
        var d2 by remember { mutableIntStateOf(70) }
        var d3 by remember { mutableIntStateOf(15) }
        var d4 by remember { mutableIntStateOf(90) }

        var led1Enabled by remember { mutableStateOf(true) }
        var led2Enabled by remember { mutableStateOf(true) }
        var led3Enabled by remember { mutableStateOf(true) }
        var led4Enabled by remember { mutableStateOf(true) }

        val schedule1 = remember {
            mutableStateListOf<LedInterval>(
                LedInterval(LocalTime.of(12, 0), LocalTime.of(13, 0), 15)
            )
        }
        val schedule2 = remember { mutableStateListOf<LedInterval>() }
        val schedule3 = remember { mutableStateListOf<LedInterval>() }
        val schedule4 = remember { mutableStateListOf<LedInterval>() }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Управление умной лампой",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Диоды и интервалы",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AssistChip(
                        onClick = { },
                        label = { Text("Лампа #$idLamp") }
                    )
                }
            }

            item {
                LedSection(
                    title = "Диод 1",
                    isEnabled = led1Enabled,
                    onEnabledChange = { led1Enabled = it },
                    valuePercent = d1,
                    onValueChange = { d1 = it },
                    schedule = schedule1
                )
            }

            item {
                LedSection(
                    title = "Диод 2",
                    isEnabled = led2Enabled,
                    onEnabledChange = { led2Enabled = it },
                    valuePercent = d2,
                    onValueChange = { d2 = it },
                    schedule = schedule2
                )
            }

            item {
                LedSection(
                    title = "Диод 3",
                    isEnabled = led3Enabled,
                    onEnabledChange = { led3Enabled = it },
                    valuePercent = d3,
                    onValueChange = { d3 = it },
                    schedule = schedule3
                )
            }

            item {
                LedSection(
                    title = "Диод 4",
                    isEnabled = led4Enabled,
                    onEnabledChange = { led4Enabled = it },
                    valuePercent = d4,
                    onValueChange = { d4 = it },
                    schedule = schedule4
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LedSection(
    title: String,
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    valuePercent: Int,
    onValueChange: (Int) -> Unit,
    schedule: SnapshotStateList<LedInterval>,
) {
    // для выбора времени
    var pickIndex by remember { mutableIntStateOf(-1) }
    var pickField by remember { mutableStateOf<PickField?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Заголовок диода + кнопка "+"
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )

                // 👇 подпись + статус
                Text(
                    text = if (isEnabled) "Вкл" else "Выкл",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(8.dp))

                // Вкл/Выкл
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onEnabledChange
                )

                Spacer(Modifier.width(6.dp))

                // маленькая кнопка "+"
                IconButton(
                    onClick = {
                        schedule.add(
                            LedInterval(
                                start = LocalTime.of(12, 0),
                                end = LocalTime.of(13, 0),
                                brightness = valuePercent
                            )
                        )
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить интервал")
                }
            }

            // Ручная яркость
            Text(
                text = "Яркость по умолчанию: $valuePercent%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = valuePercent.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = 0f..100f
            )

            // Интервалы (сразу под диодом)
            if (schedule.isEmpty()) {
                Text(
                    text = "Интервалы не заданы. Нажмите “+”, чтобы добавить.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    schedule.forEachIndexed { index, item ->
                        IntervalEditor(
                            item = item,
                            onChange = { schedule[index] = it },
                            onDelete = { schedule.removeAt(index) },
                            onPickStart = {
                                pickIndex = index
                                pickField = PickField.START
                            },
                            onPickEnd = {
                                pickIndex = index
                                pickField = PickField.END
                            }
                        )
                    }
                }
            }

            // простая валидация "end > start"
            val hasBad = schedule.any { it.end <= it.start }
            if (hasBad) {
                Text(
                    text = "⚠️ У некоторых интервалов время “до” должно быть позже времени “с”.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // TimePicker поверх экрана (как было в диалоге)
    if (pickIndex >= 0 && pickField != null) {
        val current = schedule.getOrNull(pickIndex) ?: return
        val initial = if (pickField == PickField.START) current.start else current.end

        TimePickerDialog(
            initial = initial,
            onDismiss = {
                pickIndex = -1
                pickField = null
            },
            onConfirm = { newTime ->
                val updated = when (pickField) {
                    PickField.START -> current.copy(start = newTime)
                    PickField.END -> current.copy(end = newTime)
                    null -> current
                }
                schedule[pickIndex] = updated
                pickIndex = -1
                pickField = null
            }
        )
    }
}

private enum class PickField { START, END }

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun IntervalEditor(
    item: LedInterval,
    onChange: (LedInterval) -> Unit,
    onDelete: () -> Unit,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = onPickStart,
                    label = { Text("С ${item.start.formatHHmm()}") }
                )
                Spacer(Modifier.width(8.dp))
                AssistChip(
                    onClick = onPickEnd,
                    label = { Text("До ${item.end.formatHHmm()}") }
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Удалить интервал")
                }
            }

            Text(
                text = "Яркость: ${item.brightness}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Slider(
                value = item.brightness.toFloat(),
                onValueChange = { onChange(item.copy(brightness = it.toInt())) },
                valueRange = 0f..100f
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите время") },
        text = { TimePicker(state = state) },
        confirmButton = {
            Button(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) {
                Text("Ок")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}