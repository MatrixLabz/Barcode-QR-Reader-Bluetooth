package com.matrix.barcode.utils

import androidx.compose.runtime.Composable

@Composable
fun RequiresModuleInstallation(content: @Composable () -> Unit) {
    content()
}