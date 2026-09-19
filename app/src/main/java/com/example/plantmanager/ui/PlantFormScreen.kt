@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.plantmanager.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.plantmanager.PhotoStore
import com.example.plantmanager.domain.Plant
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Formulaire d'ajout ([plant] = null) ou de modification d'une plante.
 * [onDelete] est null en mode ajout : le bouton Supprimer n'apparaît pas.
 */
@Composable
fun PlantFormScreen(
    plant: Plant?,
    onSave: (Plant) -> Unit,
    onDelete: (() -> Unit)?,
    onBack: () -> Unit,
) {
    val today = remember { LocalDate.now().toEpochDay() }

    // Les dates sont gardées en « jours depuis 1970 » (Long) : simple à sauvegarder à la rotation.
    var name by rememberSaveable { mutableStateOf(plant?.name ?: "") }
    var species by rememberSaveable { mutableStateOf(plant?.species ?: "") }
    var sunlight by rememberSaveable { mutableStateOf(plant?.sunlight ?: "") }
    var location by rememberSaveable { mutableStateOf(plant?.location ?: "") }
    var notes by rememberSaveable { mutableStateOf(plant?.notes ?: "") }
    var wateringText by rememberSaveable { mutableStateOf(plant?.wateringEveryDays?.toString() ?: "7") }
    var fertText by rememberSaveable { mutableStateOf(plant?.fertilizingEveryDays?.toString() ?: "") }
    var acquiredDay by rememberSaveable { mutableStateOf(plant?.acquiredOn?.toEpochDay() ?: today) }
    var lastWateredDay by rememberSaveable { mutableStateOf<Long?>(plant?.lastWateredOn?.toEpochDay()) }
    var lastFertDay by rememberSaveable { mutableStateOf<Long?>(plant?.lastFertilizedOn?.toEpochDay()) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    // Chemin relatif de la photo (déjà copiée dans le stockage de l'app dès qu'elle est choisie).
    var photoPath by rememberSaveable { mutableStateOf<String?>(plant?.photoPath) }
    var importing by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cameraCapturePath by rememberSaveable { mutableStateOf<String?>(null) }

    fun importPhoto(uri: Uri) {
        importing = true
        scope.launch {
            try {
                photoPath = withContext(Dispatchers.IO) {
                    PhotoStore.get(context).importFrom(context.contentResolver, uri)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Photo impossible à importer : ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                importing = false
            }
        }
    }

    // Sélecteur de photos du système : aucune permission à demander.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        uri?.let(::importPhoto)
    }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val capturePath = cameraCapturePath
        cameraCapturePath = null
        if (capturePath != null) {
            val captureFile = File(capturePath)
            if (success) {
                importPhoto(
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        captureFile,
                    ),
                )
            } else {
                captureFile.delete()
            }
        }
    }

    val nameError = name.isBlank()
    val wateringDays = wateringText.trim().toIntOrNull()
    val wateringError = wateringDays == null || wateringDays < 1
    val fertDays = fertText.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
    val fertError = fertText.isNotBlank() && (fertDays == null || fertDays < 1)
    val valid = !nameError && !wateringError && !fertError

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (plant == null) "Nouvelle plante" else "Modifier la plante") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlantPhoto(photoPath = photoPath, size = 160.dp)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        enabled = !importing,
                        onClick = {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Text(
                            when {
                                importing -> "Import en cours…"
                                else -> "Galerie"
                            },
                        )
                    }
                    OutlinedButton(
                        enabled = !importing,
                        onClick = {
                            val captureFile = File.createTempFile("plant_capture_", ".jpg", context.cacheDir)
                            cameraCapturePath = captureFile.absolutePath
                            takePhoto.launch(
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    captureFile,
                                ),
                            )
                        },
                    ) {
                        Text("Appareil photo")
                    }
                    if (photoPath != null && !importing) {
                        TextButton(onClick = { photoPath = null }) { Text("Retirer") }
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom") },
                singleLine = true,
                isError = showErrors && nameError,
                supportingText = { if (showErrors && nameError) Text("Le nom est obligatoire") },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = species,
                onValueChange = { species = it },
                label = { Text("Espèce") },
                placeholder = { Text("Ex. Monstera deliciosa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = sunlight,
                onValueChange = { sunlight = it },
                label = { Text("Ensoleillement") },
                placeholder = { Text("Ex. Lumière indirecte") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Emplacement") },
                placeholder = { Text("Ex. Salon, près de la fenêtre") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                placeholder = { Text("Ajoute tes observations...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = "Date d'acquisition",
                epochDay = acquiredDay,
                clearable = false,
                onChange = { day -> if (day != null) acquiredDay = day },
            )

            OutlinedTextField(
                value = wateringText,
                onValueChange = { value -> wateringText = value.filter { it.isDigit() }.take(3) },
                label = { Text("Arroser tous les … jours") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = showErrors && wateringError,
                supportingText = { if (showErrors && wateringError) Text("Indique un nombre de jours (1 ou plus)") },
                modifier = Modifier.fillMaxWidth(),
            )

            DateField(
                label = "Dernier arrosage",
                epochDay = lastWateredDay,
                clearable = true,
                onChange = { day -> lastWateredDay = day },
            )

            OutlinedTextField(
                value = fertText,
                onValueChange = { value -> fertText = value.filter { it.isDigit() }.take(3) },
                label = { Text("Engrais tous les … jours (facultatif)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = showErrors && fertError,
                supportingText = {
                    if (showErrors && fertError) Text("Laisse vide ou indique 1 jour ou plus")
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (fertText.isNotBlank()) {
                DateField(
                    label = "Dernier engrais",
                    epochDay = lastFertDay,
                    clearable = true,
                    onChange = { day -> lastFertDay = day },
                )
            }

            Text(
                text = "La prochaine échéance est la dernière date + la fréquence. " +
                        "Sans dernière date, on part de la date d'acquisition.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !importing,
                    onClick = {
                        if (!valid) {
                            showErrors = true
                        } else {
                            onSave(
                                Plant(
                                    id = plant?.id ?: 0L,
                                    name = name.trim(),
                                    species = species.trim(),
                                    sunlight = sunlight.trim(),
                                    location = location.trim(),
                                    notes = notes.trim(),
                                    photoPath = photoPath,
                                    acquiredOn = LocalDate.ofEpochDay(acquiredDay),
                                    wateringEveryDays = checkNotNull(wateringDays),
                                    fertilizingEveryDays = fertDays,
                                    lastWateredOn = lastWateredDay?.let { LocalDate.ofEpochDay(it) },
                                    lastFertilizedOn = lastFertDay?.let { LocalDate.ofEpochDay(it) },
                                ),
                            )
                        }
                    },
                ) { Text("Enregistrer") }

                if (onDelete != null) {
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Supprimer") }
                }
            }
        }
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette plante ?") },
            text = { Text("« ${plant?.name ?: name.trim()} » sera définitivement supprimée.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

/** Un libellé, la date choisie (ou « Non renseignée »), un sélecteur de date et, si [clearable], « Effacer ». */
@Composable
private fun DateField(
    label: String,
    epochDay: Long?,
    clearable: Boolean,
    onChange: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { open = true }) {
                Text(epochDay?.let { formatDate(LocalDate.ofEpochDay(it)) } ?: "Non renseignée")
            }
            if (clearable && epochDay != null) {
                TextButton(onClick = { onChange(null) }) { Text("Effacer") }
            }
        }
    }

    if (open) {
        // Le sélecteur de Material 3 travaille en millisecondes UTC à minuit : jours * 86 400 000.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (epochDay ?: LocalDate.now().toEpochDay()) * MILLIS_PER_DAY,
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onChange(Math.floorDiv(millis, MILLIS_PER_DAY))
                        }
                        open = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}