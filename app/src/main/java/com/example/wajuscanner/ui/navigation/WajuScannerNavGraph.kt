package com.example.wajuscanner.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.wajuscanner.ui.detail.DocumentDetailScreen
import com.example.wajuscanner.ui.editor.EditorScreen
import com.example.wajuscanner.ui.home.HomeScreen
import com.example.wajuscanner.ui.home.HomeViewModel
import com.example.wajuscanner.ui.ocr.OcrResultScreen
import com.example.wajuscanner.ui.idphoto.PassportPhotoScreen
import com.example.wajuscanner.ui.qr.QrScannerScreen
import com.example.wajuscanner.ui.resume.ResumeScanScreen
import com.example.wajuscanner.ui.scanner.ScannerScreen
import com.example.wajuscanner.ui.settings.SettingsScreen
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel

/**
 * The app's single NavHost. Keeps every composable destination and the routes
 * that connect them in one place so [com.example.wajuscanner.MainActivity]
 * stays focused on theme + edge-to-edge setup.
 *
 * Routes are type-safe via [SmartScannerDestinations] (kotlinx.serialization).
 * Each composable receives only the navigation callbacks it actually needs;
 * screen-specific ViewModels are created through `hiltViewModel()` so that
 * their state survives configuration changes and is scoped to the back-stack
 * entry.
 */
@Composable
fun WajuScannerNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = SmartScannerDestinations.Home,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
    ) {
        composable<SmartScannerDestinations.Home> {
            val viewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = viewModel,
                onScanClick = {
                    navController.navigate(SmartScannerDestinations.Scanner)
                },
                onDocumentClick = { documentId ->
                    navController.navigate(SmartScannerDestinations.Detail(documentId))
                },
                onSettingsClick = {
                    navController.navigate(SmartScannerDestinations.Settings)
                },
                onQrScanClick = {
                    navController.navigate(SmartScannerDestinations.QrScanner)
                },
                onIdPhotoClick = {
                    navController.navigate(SmartScannerDestinations.IdPhoto)
                },
                onResumeDraft = { documentId ->
                    navController.navigate(SmartScannerDestinations.ResumeDraft(documentId))
                },
            )
        }

        composable<SmartScannerDestinations.Scanner> {
            ScannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onDocumentCreated = { documentId ->
                    navController.navigate(SmartScannerDestinations.Detail(documentId))
                },
            )
        }

        composable<SmartScannerDestinations.QrScanner> {
            QrScannerScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<SmartScannerDestinations.IdPhoto> {
            PassportPhotoScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<SmartScannerDestinations.ResumeDraft> { backStackEntry ->
            val destination = backStackEntry.toRoute<SmartScannerDestinations.ResumeDraft>()
            ResumeScanScreen(
                documentId = destination.documentId,
                onResume = { docId ->
                    navController.navigate(SmartScannerDestinations.Editor(docId)) {
                        popUpTo(SmartScannerDestinations.Home) { inclusive = false }
                    }
                },
                onDiscard = { navController.navigate(SmartScannerDestinations.Scanner) },
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<SmartScannerDestinations.Detail> { backStackEntry ->
            val destination = backStackEntry.toRoute<SmartScannerDestinations.Detail>()
            DocumentDetailScreen(
                documentId = destination.documentId,
                onNavigateBack = { navController.popBackStack() },
                onEditPage = { pageId ->
                    navController.navigate(SmartScannerDestinations.Editor(pageId))
                },
                onAddPage = {
                    navController.navigate(SmartScannerDestinations.Scanner)
                },
                onOcrPage = { pageId ->
                    navController.navigate(SmartScannerDestinations.OcrResult(pageId))
                },
            )
        }

        composable<SmartScannerDestinations.Editor> { backStackEntry ->
            val destination = backStackEntry.toRoute<SmartScannerDestinations.Editor>()
            EditorScreen(
                pageId = destination.pageId,
                onNavigateBack = { navController.popBackStack() },
                onSaveComplete = { navController.popBackStack() },
            )
        }

        composable<SmartScannerDestinations.Settings> {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable<SmartScannerDestinations.OcrResult> { backStackEntry ->
            val destination = backStackEntry.toRoute<SmartScannerDestinations.OcrResult>()
            OcrResultScreen(
                pageId = destination.pageId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}