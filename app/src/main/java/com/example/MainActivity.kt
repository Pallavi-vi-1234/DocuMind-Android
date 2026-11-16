package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.DocuMindApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.DocuMindViewModel

class MainActivity : ComponentActivity() {

    private lateinit var docuMindViewModel: DocuMindViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        docuMindViewModel = ViewModelProvider(this)[DocuMindViewModel::class.java]
        if (savedInstanceState == null) {
            docuMindViewModel.handleDeepLinkIntent(intent)
        }
        setContent {
            MyApplicationTheme {
                DocuMindApp(viewModel = docuMindViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::docuMindViewModel.isInitialized) {
            docuMindViewModel.handleDeepLinkIntent(intent, forceReprocess = true)
        }
    }
}
