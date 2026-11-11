package com.foodlabel.studio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.foodlabel.studio.AppContainer
import com.foodlabel.studio.data.db.BusinessEntity
import com.foodlabel.studio.printing.BatchItem
import com.foodlabel.studio.ui.screens.BackupScreen
import com.foodlabel.studio.ui.screens.BatchScreen
import com.foodlabel.studio.ui.screens.BusinessEditScreen
import com.foodlabel.studio.ui.screens.BusinessesScreen
import com.foodlabel.studio.ui.screens.CategoriesScreen
import com.foodlabel.studio.ui.screens.DesignerScreen
import com.foodlabel.studio.ui.screens.HistoryScreen
import com.foodlabel.studio.ui.screens.HomeScreen
import com.foodlabel.studio.ui.screens.LabelSizesScreen
import com.foodlabel.studio.ui.screens.OnboardingScreen
import com.foodlabel.studio.ui.screens.PrinterEditScreen
import com.foodlabel.studio.ui.screens.PrintersScreen
import com.foodlabel.studio.ui.screens.ProductEditScreen
import com.foodlabel.studio.ui.screens.ProductsScreen
import com.foodlabel.studio.ui.screens.QuickPrintScreen
import com.foodlabel.studio.ui.screens.SavedLabelsScreen
import com.foodlabel.studio.ui.screens.SettingsScreen
import com.foodlabel.studio.ui.screens.TemplatesScreen
import com.foodlabel.studio.ui.theme.Fls

val LocalContainer = staticCompositionLocalOf<AppContainer> { error("No container") }
val LocalBusiness = staticCompositionLocalOf<BusinessEntity> { error("No business") }

/** Labels waiting in the batch, shared by Quick Print and the Batch screen. */
object BatchCart {
    val items = mutableStateListOf<BatchItem>()
}

object Routes {
    const val HOME = "home"
    const val QUICK = "quick?productId={productId}"
    fun quick(productId: Long? = null) = "quick?productId=${productId ?: -1}"
    const val PRODUCTS = "products?categoryId={categoryId}"
    fun products(categoryId: Long? = null) = "products?categoryId=${categoryId ?: -1}"
    const val PRODUCT = "product/{id}"
    fun product(id: Long) = "product/$id"
    const val CATEGORIES = "categories"
    const val DESIGN = "design"
    const val DESIGNER = "designer?designId={designId}&templateId={templateId}&productId={productId}"
    fun designer(designId: Long? = null, templateId: Long? = null, productId: Long? = null) =
        "designer?designId=${designId ?: -1}&templateId=${templateId ?: -1}&productId=${productId ?: -1}"
    const val TEMPLATES = "templates?pickFor={pickFor}"
    fun templates(pickForProduct: Long? = null) = "templates?pickFor=${pickForProduct ?: -1}"
    const val PRINTERS = "printers"
    const val PRINTER = "printer/{id}"
    fun printer(id: Long) = "printer/$id"
    const val BUSINESSES = "businesses"
    const val BUSINESS = "business/{id}"
    fun business(id: Long) = "business/$id"
    const val SETTINGS = "settings"
    const val SIZES = "sizes"
    const val HISTORY = "history"
    const val BACKUP = "backup"
    const val BATCH = "batch"
}

@Composable
fun AppRoot(container: AppContainer) {
    val activeId by container.repo.activeBusinessId.collectAsStateWithLifecycle(initialValue = -2L)
    val business by container.repo.activeBusiness.collectAsStateWithLifecycle(initialValue = null)

    CompositionLocalProvider(LocalContainer provides container) {
        Box(Modifier.fillMaxSize().background(Fls.Ivory)) {
            val b = business
            when {
                activeId == -2L -> Unit
                activeId == null -> OnboardingScreen()
                b == null -> Unit
                else -> CompositionLocalProvider(LocalBusiness provides b) { MainNav() }
            }
        }
    }
}

private data class Tab(val route: String, val nav: String, val label: String, val icon: ImageVector)

@Composable
private fun MainNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = listOf(
        Tab(Routes.HOME, Routes.HOME, "Home", Icons.Outlined.Home),
        Tab(Routes.PRODUCTS, Routes.products(), "Products", Icons.Outlined.Inventory2),
        Tab(Routes.QUICK, Routes.quick(), "Print", Icons.Outlined.Print),
        Tab(Routes.DESIGN, Routes.DESIGN, "Design", Icons.Outlined.Edit),
        Tab(Routes.CATEGORIES, Routes.CATEGORIES, "Categories", Icons.Outlined.Category),
    )
    val showBar = route in setOf(Routes.HOME, Routes.PRODUCTS, Routes.DESIGN, Routes.CATEGORIES)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.weight(1f)) { Graph(nav) }
        if (showBar) BottomBar(tabs, route) { t ->
            nav.navigate(t.nav) {
                popUpTo(Routes.HOME) { saveState = t.route != Routes.QUICK }
                launchSingleTop = true
                restoreState = t.route != Routes.QUICK
            }
        }
    }
}

@Composable
private fun BottomBar(tabs: List<Tab>, current: String?, onSelect: (Tab) -> Unit) {
    Column(Modifier.background(Fls.Card).navigationBarsPadding()) {
        HorizontalDivider(color = Fls.Line)
        Row(Modifier.fillMaxWidth().height(72.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val selected = current == t.route
                if (t.route == Routes.QUICK) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Surface(
                            onClick = { onSelect(t) }, shape = CircleShape, color = Fls.Chocolate,
                            border = BorderStroke(3.dp, Fls.Card), shadowElevation = 4.dp,
                            modifier = Modifier.size(62.dp).offset(y = (-14).dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) { Icon(t.icon, "Quick Print", tint = Fls.GoldPale, modifier = Modifier.size(28.dp)) }
                        }
                    }
                } else {
                    Surface(onClick = { onSelect(t) }, color = Fls.Card, modifier = Modifier.weight(1f).height(72.dp)) {
                        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(t.icon, null, tint = if (selected) Fls.Ink else Fls.Muted)
                            Text(t.label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Fls.Ink else Fls.Muted, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Graph(nav: NavHostController) {
    val back: () -> Unit = { nav.popBackStack() }
    val longArg = { name: String -> navArgument(name) { type = NavType.LongType; defaultValue = -1L } }
    NavHost(nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(onNavigate = { nav.navigate(it) }) }
        composable(Routes.QUICK, listOf(longArg("productId"))) {
            QuickPrintScreen(it.arguments?.getLong("productId")?.takeIf { v -> v >= 0 }, onBack = back, onNavigate = { r -> nav.navigate(r) })
        }
        composable(Routes.PRODUCTS, listOf(longArg("categoryId"))) {
            ProductsScreen(it.arguments?.getLong("categoryId")?.takeIf { v -> v >= 0 }, onNavigate = { r -> nav.navigate(r) })
        }
        composable(Routes.PRODUCT, listOf(navArgument("id") { type = NavType.LongType })) {
            ProductEditScreen(it.arguments?.getLong("id") ?: 0L, onBack = back, onNavigate = { r -> nav.navigate(r) })
        }
        composable(Routes.CATEGORIES) { CategoriesScreen(onNavigate = { r -> nav.navigate(r) }) }
        composable(Routes.DESIGN) { SavedLabelsScreen(onNavigate = { r -> nav.navigate(r) }) }
        composable(Routes.DESIGNER, listOf(longArg("designId"), longArg("templateId"), longArg("productId"))) {
            val a = it.arguments
            DesignerScreen(
                designId = a?.getLong("designId")?.takeIf { v -> v >= 0 },
                templateId = a?.getLong("templateId")?.takeIf { v -> v >= 0 },
                productId = a?.getLong("productId")?.takeIf { v -> v >= 0 },
                onBack = back,
            )
        }
        composable(Routes.TEMPLATES, listOf(longArg("pickFor"))) {
            TemplatesScreen(it.arguments?.getLong("pickFor")?.takeIf { v -> v >= 0 }, onBack = back, onNavigate = { r -> nav.navigate(r) })
        }
        composable(Routes.PRINTERS) { PrintersScreen(onBack = back, onNavigate = { r -> nav.navigate(r) }) }
        composable(Routes.PRINTER, listOf(navArgument("id") { type = NavType.LongType })) {
            PrinterEditScreen(it.arguments?.getLong("id") ?: 0L, onBack = back)
        }
        composable(Routes.BUSINESSES) { BusinessesScreen(onBack = back, onNavigate = { r -> nav.navigate(r) }) }
        composable(Routes.BUSINESS, listOf(navArgument("id") { type = NavType.LongType })) {
            BusinessEditScreen(it.arguments?.getLong("id") ?: 0L, onBack = back)
        }
        composable(Routes.SETTINGS) { SettingsScreen(onBack = back, onNavigate = { r -> nav.navigate(r) }) }
        composable(Routes.SIZES) { LabelSizesScreen(onBack = back) }
        composable(Routes.HISTORY) { HistoryScreen(onBack = back, onNavigate = { r -> nav.navigate(r) }) }
        composable(Routes.BACKUP) { BackupScreen(onBack = back) }
        composable(Routes.BATCH) { BatchScreen(onBack = back, onNavigate = { r -> nav.navigate(r) }) }
    }
}
