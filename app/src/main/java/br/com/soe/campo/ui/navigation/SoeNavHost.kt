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
    const val SYNC_QUEUE = "sincronismo/fila"

    // Logistica & Mobilidade
    const val MISSIONS = "logistica/missoes"
    const val TRIP = "logistica/missoes/{tripId}"
    const val TRIP_BOARDING = "logistica/missoes/{tripId}/embarque"
    const val VEHICLE_CHECK = "logistica/checklist/{checkId}"
    const val PARKING = "logistica/estacionamento"

    // Direitos Humanos & Sustentabilidade
    const val SUSTAINABILITY = "sustentabilidade"
    const val SUSTAINABILITY_TASK = "sustentabilidade/{taskId}"

    fun checklistRun(executionId: String) = "checklists/execucao/$executionId"

    fun trip(tripId: String) = "logistica/missoes/$tripId"

    fun boarding(tripId: String) = "logistica/missoes/$tripId/embarque"

    fun vehicleCheck(checkId: String) = "logistica/checklist/$checkId"

    fun sustainabilityTask(taskId: String) = "sustentabilidade/$taskId"
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
                onOpenMissions = { navController.navigate(Routes.MISSIONS) },
                onOpenSustainability = { navController.navigate(Routes.SUSTAINABILITY) },
                onOpenSyncQueue = { navController.navigate(Routes.SYNC_QUEUE) },
            )
        }

        composable(Routes.SYNC_QUEUE) {
            SyncQueueScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
        }

        // ------------------------------------------ Logistica & Mobilidade

        composable(Routes.MISSIONS) {
            MissionsScreen(
                viewModel = viewModel,
                onOpenTrip = { navController.navigate(Routes.trip(it)) },
                onOpenParking = { navController.navigate(Routes.PARKING) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TRIP,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            val tripId = entry.arguments?.getString("tripId").orEmpty()
            TripScreen(
                viewModel = viewModel,
                tripId = tripId,
                onOpenCheck = { navController.navigate(Routes.vehicleCheck(it)) },
                onOpenBoarding = { navController.navigate(Routes.boarding(tripId)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.TRIP_BOARDING,
            arguments = listOf(navArgument("tripId") { type = NavType.StringType }),
        ) { entry ->
            BoardingScreen(
                viewModel = viewModel,
                tripId = entry.arguments?.getString("tripId").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.VEHICLE_CHECK,
            arguments = listOf(navArgument("checkId") { type = NavType.StringType }),
        ) { entry ->
            VehicleCheckScreen(
                viewModel = viewModel,
                checkId = entry.arguments?.getString("checkId").orEmpty(),
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.PARKING) {
            ParkingScreen(
                viewModel = viewModel,
                onReportIncident = { navController.navigate(Routes.REPORT_INCIDENT) },
                onBack = { navController.popBackStack() },
            )
        }

        // -------------------------- Direitos Humanos & Sustentabilidade

        composable(Routes.SUSTAINABILITY) {
            SustainabilityScreen(
                viewModel = viewModel,
                onOpenTask = { navController.navigate(Routes.sustainabilityTask(it)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.SUSTAINABILITY_TASK,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { entry ->
            SustainabilityTaskScreen(
                viewModel = viewModel,
                taskId = entry.arguments?.getString("taskId").orEmpty(),
                onBack = { navController.popBackStack() },
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
