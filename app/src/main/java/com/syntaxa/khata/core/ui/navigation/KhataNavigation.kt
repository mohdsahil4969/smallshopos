package com.syntaxa.khata.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.syntaxa.khata.R

sealed class Screen(val route: String, val titleRes: Int, val icon: ImageVector) {
    data object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    data object Khata : Screen("khata", R.string.nav_khata, Icons.AutoMirrored.Filled.MenuBook)
    data object Records : Screen("records", R.string.nav_records, Icons.AutoMirrored.Filled.ReceiptLong)
    data object More : Screen("more", R.string.nav_more, Icons.Default.MoreHoriz)
}

val bottomNavScreens = listOf(
    Screen.Home,
    Screen.Khata,
    Screen.Records,
    Screen.More
)
