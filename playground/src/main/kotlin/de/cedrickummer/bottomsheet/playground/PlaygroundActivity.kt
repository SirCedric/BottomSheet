package de.cedrickummer.bottomsheet.playground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.cedrickummer.bottomsheet.playground.prototypes.HeaderPrototype

class PlaygroundActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HeaderPrototype()
        }
    }
}
