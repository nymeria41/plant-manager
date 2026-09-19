package com.example.plantmanager

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Photos des plantes, stockées dans le dossier privé de l'app (`filesDir/photos/`).
 *
 * - Chaque photo importée est réduite (1 024 px max), remise à l'endroit d'après ses données
 *   EXIF, puis enregistrée en JPEG : plus aucune dépendance à l'image d'origine.
 * - [Plant.photoPath] contient un chemin RELATIF (`photos/<uuid>.jpg`), jamais un chemin absolu :
 *   il reste valable après une réinstallation ou un changement de téléphone.
 * - Toutes les méthodes sont bloquantes : à appeler hors du thread principal.
 */
class PhotoStore private constructor(private val baseDir: File) {

    private val photosDir = File(baseDir, "photos")

    // Petites images déjà décodées, pour ne pas les relire à chaque défilement de la liste.
    private val cache = LruCache<String, Bitmap>(40)

    /**
     * Copie la photo choisie dans le stockage de l'app et renvoie son chemin relatif.
     * @throws IOException si l'image est illisible.
     */
    fun importFrom(resolver: ContentResolver, uri: Uri): String {
        // 1. Dimensions, sans décoder l'image entière.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Image illisible")

        // 2. Décodage en version réduite (économise la mémoire sur les grosses photos).
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, IMPORT_MAX_SIDE)
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Image illisible")

        // 3. Orientation demandée par l'appareil photo (les photos en portrait sont souvent « couchées »).
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val result = scaleDown(applyOrientation(decoded, orientation), IMPORT_MAX_SIDE)

        // 4. Enregistrement.
        photosDir.mkdirs()
        val relative = "photos/${UUID.randomUUID()}.jpg"
        val file = File(baseDir, relative)
        try {
            FileOutputStream(file).use { out ->
                if (!result.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IOException("Enregistrement de la photo impossible")
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        return relative
    }

    /** Version réduite d'une photo pour l'affichage, ou null si le fichier est absent ou invalide. */
    fun loadThumbnail(relativePath: String, maxSide: Int): Bitmap? {
        val key = "$relativePath@$maxSide"
        cache.get(key)?.let { return it }

        val file = resolve(relativePath) ?: return null
        if (!file.isFile) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val bitmap = BitmapFactory.decodeFile(file.path, options) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * Supprime les photos que plus aucune plante n'utilise (photo remplacée, plante supprimée,
     * formulaire abandonné). Les fichiers de moins de [minAgeMillis] sont gardés, car ils peuvent
     * appartenir à un formulaire encore ouvert.
     */
    fun deleteOrphans(referencedPaths: Collection<String?>, minAgeMillis: Long = ONE_HOUR_MILLIS) {
        val keep = referencedPaths
            .filterNotNull()
            .mapNotNull { resolve(it)?.canonicalPath }
            .toSet()
        val now = System.currentTimeMillis()

        photosDir.listFiles()?.forEach { file ->
            if (file.isFile && file.canonicalPath !in keep && now - file.lastModified() > minAgeMillis) {
                file.delete()
            }
        }
    }

    /**
     * Fichier correspondant à un chemin relatif, ou null s'il sort du dossier des photos
     * (par exemple `../plants.json` dans une sauvegarde importée).
     */
    private fun resolve(relativePath: String): File? {
        val file = File(baseDir, relativePath)
        val root = photosDir.canonicalPath + File.separator
        return if (file.canonicalPath.startsWith(root)) file else null
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2
        return sample
    }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    companion object {
        private const val IMPORT_MAX_SIDE = 1024
        private const val JPEG_QUALITY = 85
        private const val ONE_HOUR_MILLIS = 60L * 60L * 1000L

        @Volatile
        private var instance: PhotoStore? = null

        fun get(context: Context): PhotoStore =
            instance ?: synchronized(this) {
                instance ?: PhotoStore(context.applicationContext.filesDir).also { instance = it }
            }
    }
}