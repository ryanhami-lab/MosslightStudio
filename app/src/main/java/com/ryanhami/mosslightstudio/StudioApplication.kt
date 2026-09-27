package com.ryanhami.mosslightstudio

import android.app.Application
import com.ryanhami.mosslightstudio.data.StudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class StudioApplication : Application() {
    // One DataStore owner for the entire process; no Activity, View, renderer or media objects.
    val repository by lazy {
        StudioRepository(File(filesDir, "studio-layout.json"), CoroutineScope(SupervisorJob() + Dispatchers.IO))
    }
}

