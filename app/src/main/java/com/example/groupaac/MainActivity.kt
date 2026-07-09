package com.example.groupaac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.example.groupaac.ui.AppNavGraph
import com.example.groupaac.ui.theme.GroupAacTheme

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as GroupAacApplication).appContainer
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                GroupAacTheme {
                    AppNavGraph()
                }
            }
        }
    }
}
