package com.example.plantmanager

import android.content.Context
import com.example.plantmanager. domain.PlantRepository
import java.io.File

/**
 * Une seule instance du dépôt pour toute l'app : l'écran, et plus tard le récepteur de
 * notifications, lisent et écrivent le même fichier `plants.json` dans le dossier privé de l'app.
 */
object PlantRepositoryProvider {

    @Volatile
    private var instance: PlantRepository? = null

    fun get(context: Context): PlantRepository =
        instance ?: synchronized(this) {
            instance ?: PlantRepository(File(context.applicationContext.filesDir, "plants.json"))
                .also { instance = it }
        }
}
