package dev.sircedric.bottomsheet.playground

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.sircedric.bottomsheet.playground.prototypes.DetentLayoutPrototype

class PlaygroundActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DetentLayoutPrototype()
        }
    }
}
