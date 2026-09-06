package br.com.soe.campo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import br.com.soe.campo.ui.SoeViewModel
import br.com.soe.campo.ui.screens.*

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val REPORT_INCIDENT = "incidente/novo"
    const val INCIDENTS = "incidentes"
    const val CHECKLISTS = "checklists"
    const val CHECKLIST_RUN = "checklists/execucao/{executionId}"
    const val ALERTS = "alertas"
    const val PROFILE = "perfil"

    fun checklistRun(executionId: String) = "checklists/execucao/$executionId"
}

@Composable
fun SoeNavHost(
    viewModel: SoeViewModel,
    startLoggedIn: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = if (startLoggedIn) Routes.HOME else Routes.LOGIN,
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                viewModel = viewModel,
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                viewModel = viewModel,
                onReportIncident = { navController.navigate(Routes.REPORT_INCIDENT) },
                onOpenIncidents = { navController.navigate(Routes.INCIDENTS) },
                onOpenChecklists = { navController.navigate(Routes.CHECKLISTS) },
                onOpenAlerts = { navController.navigate(Routes.ALERTS) },
                onOpenProfile = { navController.navigate(Routes.PROFILE) },
            )
        }

        composable(Routes.REPORT_INCIDENT) {
            ReportIncidentScreen(
                viewModel = viewModel,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.INCIDENTS) {
            IncidentsScreen(
                viewModel = viewModel,
                onReportIncident = { navController.navigate(Routes.REPORT_INCIDENT) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.CHECKLISTS) {
            ChecklistsScreen(
                viewModel = viewModel,
                onOpenExecution = { navController.navigate(Routes.checklistRun(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CHECKLIST_RUN,
            arguments = listOf(navArgument("executionId") { type = NavType.StringType }),
        ) { entry ->
            ChecklistRunScreen(
                viewModel = viewModel,
                executionId = entry.arguments?.getString("executionId").orEmpty(),
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.ALERTS) {
            AlertsScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }
    }
}
