package com.matrix.barcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import com.matrix.barcode.ui.theme.BluetoothHIDTheme
import com.matrix.barcode.ui.tooltip
import com.matrix.barcode.utils.*

class SettingsActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            BluetoothHIDTheme {
                Surface(Modifier.fillMaxSize()) {
                    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

                    Scaffold(
                        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.settings)) },
                                navigationIcon = {
                                    IconButton(
                                        onClick = { finishAfterTransition() },
                                        Modifier.tooltip(stringResource(R.string.back))
                                    ) {
                                        Icon(Icons.Default.ArrowBack, "Back")
                                    }
                                },
                                scrollBehavior = scrollBehavior
                            )
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            SettingsContent()
                        }
                    }
                }
            }
        }
    }

}
