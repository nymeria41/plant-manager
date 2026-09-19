package com.example.plantmanager.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantmanager.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Photo carrée aux coins arrondis. Sans photo (ou si le fichier est introuvable),
 * affiche une pastille avec une petite pousse.
 */
@Composable
fun PlantPhoto(photoPath: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val maxSide = with(LocalDensity.current) { size.roundToPx() }

    val bitmap by produceState<ImageBitmap?>(null, photoPath, maxSide) {
        value = if (photoPath == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                PhotoStore.get(context).loadThumbnail(photoPath, maxSide)?.asImageBitmap()
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val image = bitmap

    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = "Photo de la plante",
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text("🌱", fontSize = (size.value / 2).sp)
        }
    }
}