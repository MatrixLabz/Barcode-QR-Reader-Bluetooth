package com.matrix.barcode

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.TorchState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.matrix.barcode.ui.*
import com.matrix.barcode.ui.theme.Neutral95
import com.matrix.barcode.utils.*
import com.matrix.barcode.utils.Constants.SCANNER_BANNER_AD_ID

/**
 * Scanner screen with camera preview.
 *
 * @param currentDevice the device that is currently connected, can be null if no device is connected
 * @param sendText callback to send text to the current device
 */
@Composable
fun Scanner(
    currentDevice: BluetoothDevice?,
    sendText: (String) -> Unit
) {
    var currentBarcode by rememberSaveable { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val fullScreen by rememberPreference(PreferenceStore.SCANNER_FULL_SCREEN)

    Scaffold(
        topBar = {
            ScannerAppBar(camera, currentDevice, fullScreen)
        },
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            currentDevice?.let {
                SendToDeviceFAB(currentBarcode, sendText)
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            Modifier
                .padding(if (fullScreen) PaddingValues(0.dp) else padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            RequiresCameraPermission {
                CameraPreviewArea(
                    onCameraReady = { camera = it }
                ) { value, send ->
                    currentBarcode = value
                    if (send) {
                        sendText(value)
                    }
                }
                BarcodeValue(currentBarcode)
            }
            //DeviceInfoCard(currentDevice)
            camera?.let {
                ZoomStateInfo(it)
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f), // This weight ensures that the AdMob banner takes up the remaining space
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AdmobBanner(Modifier.fillMaxWidth(), SCANNER_BANNER_AD_ID)
                }
            }
        }
    }
}

/**
 * Area for the camera preview.
 *
 * @param onCameraReady callback to be called when the camera is ready
 * @param onBarcodeDetected callback to be called when a barcode is detected
 */
@Composable
private fun CameraPreviewArea(
    onCameraReady: (Camera) -> Unit,
    onBarcodeDetected: (String, Boolean) -> Unit,
) {
    val context = LocalContext.current

    val vibrator = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    val playSound by rememberPreferenceDefault(PreferenceStore.PLAY_SOUND)

    val toneGenerator = remember {
        runCatching {
            ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)
        }.onFailure {
            Log.e("Scanner", "Error initializing tone generator", it)
            Toast.makeText(context, "Error initializing tone generator", Toast.LENGTH_SHORT).show()
        }.getOrNull()
    }

    // Clean up tone generator after use
    DisposableEffect(toneGenerator) {
        onDispose {
            toneGenerator?.release()
        }
    }

    val autoSend by rememberPreferenceDefault(PreferenceStore.AUTO_SEND)
    val vibrate by rememberPreferenceDefault(PreferenceStore.VIBRATE)

    CameraArea(onCameraReady) {
        onBarcodeDetected(it, autoSend)

        if (playSound) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 75)
        }

        if (vibrate && vibrator.hasVibrator()) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(75, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        }
    }
}

/**
 * Text showing the current barcode value. If the value is null, a generic message is shown instead.
 *
 * @param currentBarcode the current barcode value
 */
@Composable
private fun BoxScope.BarcodeValue(currentBarcode: String?) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Column(
        Modifier
            .fillMaxHeight(0.3f)
            .fillMaxWidth()
            .align(Alignment.TopStart),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val copiedString = stringResource(R.string.copied_to_clipboard)

        currentBarcode?.let {
            val text = AnnotatedString(it, SpanStyle(Neutral95), ParagraphStyle(TextAlign.Center))
            ClickableText(
                text,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            ) {
                clipboardManager.setText(text)
                Toast.makeText(context, copiedString, Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Text(
                stringResource(R.string.scan_code_to_start),
                color = Neutral95,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Floating action button to send the current barcode to the connected device.
 * If the currentBarcode is null, the button is hidden.
 *
 * @param currentBarcode the current barcode value
 * @param onClick callback to send text to the current device
 */
@Composable
private fun SendToDeviceFAB(
    currentBarcode: String?,
    onClick: (String) -> Unit
) {
    currentBarcode?.let {
        val controller = LocalController.current
        val disabled = controller.isSending

        val (containerColor, contentColor) = if (!disabled) {
            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) to
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.32f)
        }

        val noRippleTheme = remember {
            object : RippleTheme {
                @Composable
                override fun defaultColor() = Color.Unspecified

                @Composable
                override fun rippleAlpha(): RippleAlpha = RippleAlpha(0.0f, 0.0f, 0.0f, 0.0f)
            }
        }

        CompositionLocalProvider(
            LocalRippleTheme provides
                    if (!disabled) LocalRippleTheme.current else noRippleTheme
        ) {
            ExtendedFloatingActionButton(
                text = {
                    Text(stringResource(R.string.send_to_device))
                },
                icon = {
                    Icon(Icons.Filled.Send, "Send")
                },
                contentColor = contentColor,
                containerColor = containerColor,
                onClick = { if (!disabled) onClick(it) }
            )
        }
    }
}

/**
 * Scanner app bar with a toggle flash button and a disconnect button.
 *
 * @param camera the camera to toggle the flash on
 * @param onDisconnect callback to disconnect from the current device
 */
@SuppressLint("MissingPermission")
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ScannerAppBar(
    camera: Camera?,
    currentDevice: BluetoothDevice?,
    transparent: Boolean,
) {
    val navigation = LocalNavigation.current

    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.scanner))
                currentDevice?.let {
                    Text(
                        stringResource(R.string.connected_with, it.name ?: it.address),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        actions = {
            camera?.let {
                if (it.cameraInfo.hasFlashUnit()) {
                    ToggleFlashButton(it)
                }
            }
            IconButton(onClick = {
                navigation.navigate(Routes.History)
            }, Modifier.tooltip("History")) {
                Icon(Icons.Default.History, "History")
            }
//            IconButton(onDisconnect, Modifier.tooltip(stringResource(R.string.disconnect))) {
//                Icon(Icons.Default.BluetoothDisabled, "Disconnect")
//            }
            Dropdown()
        },
        colors = if (transparent) {
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            )
        } else {
            TopAppBarDefaults.topAppBarColors()
        },
    )
}

/**
 * Toggle flash button to toggle the flash on the camera.
 *
 * @param camera the camera to toggle the flash on
 */
@Composable
fun ToggleFlashButton(camera: Camera) {
    val torchState by camera.cameraInfo.torchState.observeAsState()

    IconButton(
        onClick = {
            camera.cameraControl.enableTorch(
                when (torchState) {
                    TorchState.OFF -> true
                    else -> false
                }
            )
        },
        modifier = Modifier.tooltip(stringResource(R.string.toggle_flash))
    ) {
        Icon(
            when (torchState) {
                TorchState.OFF -> Icons.Default.FlashOn
                else -> Icons.Default.FlashOff
            }, "Flash"
        )
    }
}

/**
 * Card showing the current device name and address. On click a dialog is shown with some
 * information about the device.
 * If the device is null, a generic message is shown instead.
 *
 * @param device the current device
 */
@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.DeviceInfoCard(device: BluetoothDevice?) {
    val dialogState = rememberDialogState()

    ElevatedCard(
        onClick = device?.let { { dialogState.open() } } ?: {},
        Modifier
            .padding(12.dp)
            .align(Alignment.TopCenter)
    ) {
        Row(
            Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            device?.let {
                Icon(Icons.Default.Info, "Info")
                Text(stringResource(R.string.connected_with, device.name ?: device.address))
            } ?: run {
                Icon(Icons.Rounded.Warning, "Warning")
                Text(stringResource(R.string.no_device))
            }
        }
    }

    device?.let {
        DeviceInfoDialog(dialogState, it)
    }
}

/**
 * Displays the current zoom-factor as a text in the top-start corner.
 * If the factor is equal to 1.0 the text is hidden.
 */
@Composable
fun BoxScope.ZoomStateInfo(camera: Camera) {
    val zoomState by camera.cameraInfo.zoomState.observeAsState()
    zoomState?.let {
        if (it.zoomRatio > 1.0f) {
            Text(
                "%.2fx".format(it.zoomRatio),
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            )
        }
    }
}

/**
 * Dialog showing information about the current device.
 */
@SuppressLint("MissingPermission")
@Composable
fun DeviceInfoDialog(
    dialogState: DialogState,
    device: BluetoothDevice
) {
    InfoDialog(dialogState, stringResource(R.string.info)) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.name) + ":", fontWeight = FontWeight.Bold)
                Text(device.name ?: "")
            }

            item {
                Text(stringResource(R.string.mac_address) + ":", fontWeight = FontWeight.Bold)
                Text(device.address)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                item {
                    Text(stringResource(R.string.alias) + ":", fontWeight = FontWeight.Bold)
                    Text(device.alias ?: "")
                }
            }

            item {
                Text(stringResource(R.string.type) + ":", fontWeight = FontWeight.Bold)
                Text(
                    when (device.type) {
                        BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual"
                        BluetoothDevice.DEVICE_TYPE_LE -> "LE"
                        BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                        BluetoothDevice.DEVICE_TYPE_UNKNOWN -> "Unknown"
                        else -> "?"
                    } + " (${device.type})"
                )
            }

            item {
                Text(stringResource(R.string.clazz) + ":", fontWeight = FontWeight.Bold)
                with(device.bluetoothClass.majorDeviceClass) {
                    val classString = remember(device) {
                        DeviceInfo.deviceClassString(this)
                    }
                    Text("$classString (${this})")
                }
            }

            item {
                Text(stringResource(R.string.services) + ":", fontWeight = FontWeight.Bold)
                val serviceInfo = remember(device) {
                    DeviceInfo.deviceServiceInfo(device.bluetoothClass)
                }
                serviceInfo.forEach {
                    Text(it)
                }
            }

//            item {
//                Text(stringResource(R.string.uuids) + ":", fontWeight = FontWeight.Bold)
//                device.uuids?.forEach {
//                    Text(it.toString())
//                }
//            }
        }
    }
}
