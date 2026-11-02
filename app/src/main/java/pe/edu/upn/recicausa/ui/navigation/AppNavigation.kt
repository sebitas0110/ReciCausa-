package pe.edu.upn.recicausa.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pe.edu.upn.recicausa.AppContainer
import pe.edu.upn.recicausa.ui.screens.ChallengesScreen
import pe.edu.upn.recicausa.ui.screens.GuideScreen
import pe.edu.upn.recicausa.ui.screens.HistoryScreen
import pe.edu.upn.recicausa.ui.screens.HomeScreen
import pe.edu.upn.recicausa.ui.screens.LoginScreen
import pe.edu.upn.recicausa.ui.screens.MyPointsScreen
import pe.edu.upn.recicausa.ui.screens.PuntosScreen
import pe.edu.upn.recicausa.ui.screens.SuggestPointScreen
import pe.edu.upn.recicausa.ui.screens.RegisterScreen
import pe.edu.upn.recicausa.ui.screens.SettingsScreen
import pe.edu.upn.recicausa.ui.screens.SignupScreen
import pe.edu.upn.recicausa.ui.theme.ReciColors
import pe.edu.upn.recicausa.ui.viewmodel.ChallengesViewModel
import pe.edu.upn.recicausa.ui.viewmodel.GuideViewModel
import pe.edu.upn.recicausa.ui.viewmodel.HistoryViewModel
import pe.edu.upn.recicausa.ui.viewmodel.HomeViewModel
import pe.edu.upn.recicausa.ui.viewmodel.LoginViewModel
import pe.edu.upn.recicausa.ui.viewmodel.MyPointsViewModel
import pe.edu.upn.recicausa.ui.viewmodel.PuntosViewModel
import pe.edu.upn.recicausa.ui.viewmodel.SuggestPointViewModel
import pe.edu.upn.recicausa.ui.viewmodel.RegisterViewModel
import pe.edu.upn.recicausa.ui.viewmodel.SessionState
import pe.edu.upn.recicausa.ui.viewmodel.SessionViewModel
import pe.edu.upn.recicausa.ui.viewmodel.SettingsViewModel
import pe.edu.upn.recicausa.ui.viewmodel.SignupViewModel
import pe.edu.upn.recicausa.ui.viewmodel.ViewModelFactory

// Dos grafos de navegación según la sesión:
//  - sin sesión: Iniciar sesión / Crear cuenta
//  - con sesión: Inicio, Guía, Registrar (centro), Retos y Avisos

private const val ROUTE_LOGIN = "login"
private const val ROUTE_SIGNUP = "signup"
private const val ROUTE_HISTORY = "historial"
private const val ROUTE_EDIT = "editar"
private const val ROUTE_SUGGEST = "sugerir"
private const val ROUTE_MY_POINTS = "mis_sugerencias"

enum class Destination(val route: String, val label: String, val icon: ImageVector) {
    Home("inicio", "Inicio", Icons.Filled.Home),
    Guide("guia", "Guía", Icons.Filled.Info),
    Register("registrar", "Registrar", Icons.Filled.Add),
    Points("puntos", "Puntos", Icons.Filled.Place),
    Challenges("retos", "Retos", Icons.Filled.Star),
    Settings("recordatorios", "Avisos", Icons.Filled.Settings)
}

@Composable
fun AppNavigation(container: AppContainer) {
    val factory = remember(container) { ViewModelFactory(container) }
    val sessionViewModel: SessionViewModel = viewModel(factory = factory)
    val session by sessionViewModel.state.collectAsState()

    when (session) {
        // Mientras DataStore lee la sesión no se muestra ninguna pantalla (evita ver el login un instante).
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(ReciColors.Ground))
        SessionState.LoggedOut -> AuthNavigation(factory)
        is SessionState.LoggedIn -> MainNavigation(factory, onLogout = sessionViewModel::logout)
    }
}

@Composable
private fun AuthNavigation(factory: ViewModelFactory) {
    val navController = rememberNavController()
    Scaffold { padding ->
        NavHost(navController, startDestination = ROUTE_LOGIN, modifier = Modifier.padding(padding)) {
            composable(ROUTE_LOGIN) {
                LoginScreen(
                    viewModel = viewModel<LoginViewModel>(factory = factory),
                    onGoSignup = { navController.navigate(ROUTE_SIGNUP) }
                )
            }
            composable(ROUTE_SIGNUP) {
                SignupScreen(
                    viewModel = viewModel<SignupViewModel>(factory = factory),
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainNavigation(factory: ViewModelFactory, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            // Registrar es una pantalla a pantalla completa (con cámara): sin barra inferior.
            // Lo mismo con el formulario de sugerencias (lleva un mapa).
            if (currentRoute != Destination.Register.route && currentRoute?.startsWith(ROUTE_EDIT) != true &&
                currentRoute?.startsWith(ROUTE_SUGGEST) != true
            ) {
                NavigationBar {
                    Destination.entries.forEach { destination ->
                        val isRegister = destination == Destination.Register
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = { navController.navigateTab(destination.route) },
                            icon = {
                                if (isRegister) {
                                    // Botón central destacado: la acción principal de la app.
                                    Box(
                                        Modifier
                                            .width(52.dp)
                                            .height(36.dp)
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp)),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(destination.icon, contentDescription = null, tint = Color.White) }
                                } else {
                                    Icon(destination.icon, contentDescription = null)
                                }
                            },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = Destination.Home.route, modifier = Modifier.padding(padding)) {
            composable(Destination.Home.route) {
                HomeScreen(
                    viewModel<HomeViewModel>(factory = factory),
                    onLogout = onLogout,
                    onSeeAll = { navController.navigate(ROUTE_HISTORY) { launchSingleTop = true } }
                )
            }
            composable(Destination.Guide.route) {
                GuideScreen(viewModel<GuideViewModel>(factory = factory))
            }
            composable(Destination.Register.route) {
                RegisterScreen(
                    viewModel = viewModel<RegisterViewModel>(factory = factory),
                    onClose = { navController.navigateTab(Destination.Home.route) },
                    onSaved = { navController.navigateTab(Destination.Home.route) }
                )
            }
            composable(ROUTE_HISTORY) {
                HistoryScreen(
                    viewModel = viewModel<HistoryViewModel>(factory = factory),
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("$ROUTE_EDIT/$id") }
                )
            }
            composable(
                route = "$ROUTE_EDIT/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                RegisterScreen(
                    viewModel = viewModel<RegisterViewModel>(factory = factory),
                    editingId = entry.arguments?.getLong("id"),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Destination.Points.route) {
                PuntosScreen(
                    viewModel = viewModel<PuntosViewModel>(factory = factory),
                    onSuggest = { navController.navigate(ROUTE_SUGGEST) { launchSingleTop = true } },
                    onMySuggestions = { navController.navigate(ROUTE_MY_POINTS) { launchSingleTop = true } }
                )
            }
            composable(ROUTE_SUGGEST) {
                SuggestPointScreen(
                    viewModel = viewModel<SuggestPointViewModel>(factory = factory),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(
                route = "$ROUTE_SUGGEST/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                SuggestPointScreen(
                    viewModel = viewModel<SuggestPointViewModel>(factory = factory),
                    editingId = entry.arguments?.getLong("id"),
                    onClose = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(ROUTE_MY_POINTS) {
                MyPointsScreen(
                    viewModel = viewModel<MyPointsViewModel>(factory = factory),
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate("$ROUTE_SUGGEST/$id") },
                    onNew = { navController.navigate(ROUTE_SUGGEST) }
                )
            }
            composable(Destination.Challenges.route) {
                ChallengesScreen(viewModel<ChallengesViewModel>(factory = factory))
            }
            composable(Destination.Settings.route) {
                SettingsScreen(viewModel<SettingsViewModel>(factory = factory))
            }
        }
    }
}
