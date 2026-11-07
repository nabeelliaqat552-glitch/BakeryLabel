package com.bakery.labelapp

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bakery.labelapp.ui.AppViewModel
import com.bakery.labelapp.ui.BakeryTheme
import com.bakery.labelapp.ui.screens.AdminScreen
import com.bakery.labelapp.ui.screens.BatchesScreen
import com.bakery.labelapp.ui.screens.LabelDesignScreen
import com.bakery.labelapp.ui.screens.PrintScreen
import com.bakery.labelapp.ui.screens.PrinterScreen
import com.bakery.labelapp.ui.screens.ProductsScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        setContent { BakeryTheme { AppRoot(vm) } }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize()) {
        NavHost(nav, startDestination = "print") {
            composable("print") {
                PrintScreen(
                    vm,
                    onAdmin = { nav.navigate("admin") },
                    onPrinter = { nav.navigate("printer") },
                    onBatches = { nav.navigate("batches") },
                )
            }
            composable("admin") {
                AdminScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onProducts = { nav.navigate("products") },
                    onBatches = { nav.navigate("batches") },
                    onDesign = { nav.navigate("design") },
                    onPrinter = { nav.navigate("printer") },
                )
            }
            composable("products") { ProductsScreen(vm) { nav.popBackStack() } }
            composable("batches") { BatchesScreen(vm) { nav.popBackStack() } }
            composable("design") { LabelDesignScreen(vm) { nav.popBackStack() } }
            composable("printer") { PrinterScreen(vm) { nav.popBackStack() } }
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp),
        )
    }
}
