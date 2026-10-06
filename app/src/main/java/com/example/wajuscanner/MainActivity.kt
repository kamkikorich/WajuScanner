package com.example.wajuscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.wajuscanner.ui.detail.DocumentDetailScreen
import com.example.wajuscanner.ui.editor.EditorScreen
import com.example.wajuscanner.ui.home.HomeScreen
import com.example.wajuscanner.ui.home.HomeViewModel
import com.example.wajuscanner.ui.navigation.SmartScannerDestinations
import com.example.wajuscanner.ui.ocr.OcrResultScreen
import com.example.wajuscanner.ui.qr.QrScannerScreen
import com.example.wajuscanner.ui.scanner.ScannerScreen
import com.example.wajuscanner.ui.settings.SettingsScreen
import com.example.wajuscanner.ui.theme.WajuScannerTheme
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WajuScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(
                        navController = navController,
                        startDestination = SmartScannerDestinations.Home
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
                                }
                            )
                        }

                        composable<SmartScannerDestinations.Scanner> {
                            ScannerScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onDocumentCreated = { documentId ->
                                    navController.navigate(SmartScannerDestinations.Detail(documentId))
                                }
                            )
                        }

                        composable<SmartScannerDestinations.QrScanner> {
                            QrScannerScreen(
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
                                }
                            )
                        }

                        composable<SmartScannerDestinations.Editor> { backStackEntry ->
                            val destination = backStackEntry.toRoute<SmartScannerDestinations.Editor>()
                            EditorScreen(
                                pageId = destination.pageId,
                                onNavigateBack = { navController.popBackStack() },
                                onSaveComplete = { navController.popBackStack() }
                            )
                        }

                        composable<SmartScannerDestinations.Settings> {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable<SmartScannerDestinations.OcrResult> { backStackEntry ->
                            val destination = backStackEntry.toRoute<SmartScannerDestinations.OcrResult>()
                            OcrResultScreen(
                                pageId = destination.pageId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
