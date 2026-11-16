/**
 * Copyright (c) 2025 tas33n
 *
 * Licensed under the MIT License
 * See LICENSE file or https://opensource.org/licenses/MIT
 *
 * @author tas33n
 * @see <a href="https://github.com/tas33n/droidwright">GitHub</a>
 */
package com.tas33n.droidwright.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tas33n.droidwright.MainViewModel
import com.tas33n.droidwright.data.models.ScriptValidationResult
import com.tas33n.droidwright.domain.ScriptMetadataParser
import com.tas33n.droidwright.domain.ScriptTemplates
import com.tas33n.droidwright.ui.components.SoraCodeEditor
import com.tas33n.droidwright.ui.components.rememberSoraEditorController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptEditorScreen(
    draft: MainViewModel.ScriptDraft,
    onSave: (MainViewModel.ScriptDraft) -> Unit,
    onValidate: (String) -> ScriptValidationResult,
    onDiscard: () -> Unit
) {
    var code by remember { mutableStateOf(draft.code) }
    var validationResult by remember { mutableStateOf<ScriptValidationResult?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var showSaveSnackbar by remember { mutableStateOf(false) }
    val originalCode = remember(draft.id, draft.code) { draft.code }
    val editorController = rememberSoraEditorController()
    val snackbarHostState = remember { SnackbarHostState() }

    val hasUnsavedChanges = code != originalCode

    LaunchedEffect(Unit) {
        if (draft.id == null && code.isBlank()) {
            code = ScriptTemplates.blankTemplate()
        }
    }
    
    LaunchedEffect(draft.code) {
        if (draft.code != code) {
            code = draft.code
        }
    }

    LaunchedEffect(code) {
        validationResult = null
    }

    LaunchedEffect(showSaveSnackbar) {
        if (showSaveSnackbar) {
            snackbarHostState.showSnackbar(
                message = "Script saved successfully",
                duration = SnackbarDuration.Short
            )
            showSaveSnackbar = false
        }
    }

    val metadata = remember(code) { ScriptMetadataParser.parse(code) }
    val scriptTitle = metadata.name?.takeIf { it.isNotBlank() }
        ?: if (draft.id == null) "New Script" else "Unnamed Script"

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to discard them?") },
            confirmButton = {
                TextButton(
                    onClick = onDiscard,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = scriptTitle,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (hasUnsavedChanges) {
                            Text(
                                text = "Unsaved changes",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showDiscardDialog = true
                        } else {
                            onDiscard()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Validate Button
                    IconButton(
                        onClick = { validationResult = onValidate(code) }
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            "Validate",
                            tint = if (validationResult?.isValid == true) {
                                Color(0xFF4CAF50)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    // Save Button
                    IconButton(
                        onClick = {
                            onSave(
                                draft.copy(
                                    code = code,
                                    lastValidatedAt = if (validationResult?.isValid == true) {
                                        System.currentTimeMillis()
                                    } else {
                                        draft.lastValidatedAt
                                    }
                                )
                            )
                            showSaveSnackbar = true
                        },
                        enabled = hasUnsavedChanges
                    ) {
                        Icon(
                            Icons.Default.Save,
                            "Save",
                            tint = if (hasUnsavedChanges) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Editor Toolbar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canUndo by editorController.canUndo
                    val canRedo by editorController.canRedo
                    
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                    )
                    
                    IconButton(
                        onClick = { editorController.undo() },
                        enabled = canUndo
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            }
                        )
                    }
                    
                    IconButton(
                        onClick = { editorController.redo() },
                        enabled = canRedo
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                            }
                        )
                    }
                    
                    Spacer(Modifier.weight(1f))
                    
                    // Character count
                    Text(
                        text = "${code.length} chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Code Editor
            SoraCodeEditor(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.weight(1f),
                controller = editorController
            )

            // Validation Result
            validationResult?.let { result ->
                ValidationCard(result)
            }
        }
    }
}

@Composable
private fun ValidationCard(validation: ScriptValidationResult) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (validation.isValid) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (validation.isValid) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.Error
                    },
                    contentDescription = null,
                    tint = if (validation.isValid) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = if (validation.isValid) {
                        "Validation Successful"
                    } else {
                        "Validation Failed"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (validation.isValid) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    }
                )
            }

            if (validation.messages.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (validation.isValid) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            },
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    validation.messages.forEach { message ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "[${message.level}]",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (validation.isValid) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                            Text(
                                text = message.message,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (validation.isValid) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}