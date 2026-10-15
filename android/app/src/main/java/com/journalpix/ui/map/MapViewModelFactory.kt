package com.journalpix.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.journalpix.viewmodel.MapViewModel

@Composable
fun rememberMapViewModel(): MapViewModel {
    return remember { MapViewModel() }
}
