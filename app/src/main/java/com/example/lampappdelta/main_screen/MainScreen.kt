package com.example.lampappdelta.main_screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import com.example.lampappdelta.Navigation
import com.example.lampappdelta.add_lamp.AddLampScreen
import com.example.lampappdelta.detail_screen.DetailScreen

class MainScreen(

    private val lampNumbers: List<Int> = listOf(1, 2, 3, 4, 5)

) : Screen {

    @Composable
    override fun Content() {

        if (Navigation.navigator == null) {

            val navigator = LocalNavigator.currentOrThrow

            Navigation.navigator = navigator

        }

        val rows: List<List<Int>> = lampNumbers.chunked(2)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

            Column(modifier = Modifier.fillMaxSize()) {

                Text(
                    text = "Управление умной лампой",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(top = 16.dp)
                )

                Spacer(Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp) // чтобы FAB не перекрывал элементы
                ) {
                    items(rows) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LampCard(
                                lampNumber = row[0],
                                modifier = Modifier
                                    .weight(1f)
                                    .height(120.dp)
                            )

                            if (row.size == 2) {
                                LampCard(
                                    lampNumber = row[1],
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(120.dp)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = {
                    Navigation.navigator?.push(AddLampScreen())
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить лампу")
            }
        }
    }
}

@Composable
private fun LampCard(
    lampNumber: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {

        Navigation.navigator?.push(DetailScreen(idLamp = lampNumber))

    }
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(124.dp), // делает карточки более "квадратными"
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Лампа",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "лампа $lampNumber",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AssistChip(
                    onClick = onClick,
                    label = { Text("#$lampNumber") }
                )
            }

            Text(
                text = "Нажмите для управления",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}