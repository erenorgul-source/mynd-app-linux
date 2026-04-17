package de.teufel.openmynd

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.koin.android.ext.koin.androidContext
import de.teufel.openmynd.di.initKoin
import de.teufel.openmynd.modules.core.di.ConnectorBackend

class AndroidApp : Application() {
    companion object {
        lateinit var INSTANCE: AndroidApp
    }


    override fun onCreate() {
        super.onCreate()
        INSTANCE = this

        // TODO investigate moving in build.gradle as buildConfigField
        val backendEnv = System.getenv("OPENMYND_CONNECTOR")?.uppercase()
        val backend = when (backendEnv) {
            "KABLE" -> ConnectorBackend.KABLE
            "BLUE_FALCON" -> ConnectorBackend.BLUE_FALCON
            else -> null
        }
        initKoin(backend) {
            androidContext(this@AndroidApp)
        }
    }
}

class AppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}