package com.feelvision.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.feelvision.domain.model.Person
import com.feelvision.ui.theme.FVColors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    viewModel: PeopleViewModel = hiltViewModel(),
    onEnroll: (Long) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = FVColors.SkyBlue,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("People", style = MaterialTheme.typography.titleMedium.copy(
                    color = FVColors.DarkNavy, fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = FVColors.DeepBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { onEnroll(-1) }) {
                        Icon(Icons.Default.PersonAdd, "Add Person", tint = FVColors.LeafGreen)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = FVColors.SkyBlue)
            )
        }
    ) { padding ->
        if (state.people.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.People, null,
                        modifier = Modifier.size(64.dp),
                        tint = FVColors.DeepBlue.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("No people enrolled", style = MaterialTheme.typography.bodyLarge.copy(
                        color = FVColors.DarkNavy.copy(alpha = 0.5f)))
                    Text("Tap + to add someone", style = MaterialTheme.typography.labelMedium.copy(
                        color = FVColors.DeepBlue.copy(alpha = 0.5f)))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.people, key = { it.id }) { person ->
                    PersonCard(
                        person = person,
                        onClick = { onEnroll(person.id) },
                        onDelete = { viewModel.deletePerson(person.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonCard(person: Person, onClick: () -> Unit, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(FVColors.Surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with initials
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(FVColors.DeepBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = person.name.take(2).uppercase(),
                style = MaterialTheme.typography.titleSmall.copy(
                    color = FVColors.DeepBlue,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.bodyLarge.copy(
                color = FVColors.DarkNavy, fontWeight = FontWeight.SemiBold))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(person.relation, style = MaterialTheme.typography.labelSmall.copy(
                    color = FVColors.DeepBlue))
                Spacer(Modifier.width(8.dp))
                Text("•", color = FVColors.DarkNavy.copy(alpha = 0.3f))
                Spacer(Modifier.width(8.dp))
                Text("${person.photoCount} photos", style = MaterialTheme.typography.labelSmall.copy(
                    color = FVColors.DarkNavy.copy(alpha = 0.5f)))
            }
        }

        IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.DeleteOutline, "Delete",
                tint = FVColors.DarkNavy.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp))
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove ${person.name}?") },
            text = { Text("Their face data will be deleted.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Remove", color = Color(0xFFE57373))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            containerColor = FVColors.Surface
        )
    }
}