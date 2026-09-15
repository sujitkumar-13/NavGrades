package com.example.ui.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.auth.AuthState
import com.example.auth.AuthViewModel
import com.example.ui.screens.AnswerKeyScreen
import com.example.ui.screens.CreateQuizScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PaperDetailScreen
import com.example.ui.screens.PrintSheetScreen
import com.example.ui.screens.QuizDetailsScreen
import com.example.ui.screens.ReviewPapersScreen
import com.example.ui.screens.ScanPapersScreen
import com.example.ui.screens.admin.AdminPanelScreen
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.NoAccessScreen
import com.example.ui.theme.BackgroundLight
import com.example.ui.theme.NavPrimary
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.viewmodel.OmrViewModel

@Composable
fun OmrNavGraph(
  navController: NavHostController = rememberNavController(),
  viewModel: OmrViewModel = viewModel(),
  authViewModel: AuthViewModel = viewModel()
) {
  val authState by authViewModel.authState.collectAsState()

  AnimatedContent(
    targetState = authState,
    transitionSpec = { fadeIn() togetherWith fadeOut() },
    label = "AuthNavState"
  ) { state ->
    when (state) {
      is AuthState.Loading -> {
        Surface(modifier = Modifier.fillMaxSize(), color = BackgroundLight) {
          Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Image(
                painter = painterResource(id = R.drawable.ic_ng_logo),
                contentDescription = "NavGrades Logo",
                modifier = Modifier.size(54.dp, 36.dp)
              )
              Spacer(modifier = Modifier.height(16.dp))
              CircularProgressIndicator(color = NavPrimary, modifier = Modifier.size(28.dp))
            }
          }
        }
      }

      is AuthState.Unauthenticated -> {
        LoginScreen(authViewModel = authViewModel)
      }

      is AuthState.NoAccess -> {
        NoAccessScreen(
          userEmail = state.email,
          authViewModel = authViewModel
        )
      }

      is AuthState.Authenticated -> {
        val currentUser = state.user
        val isAdmin = currentUser.role.equals("admin", ignoreCase = true)

        NavHost(
          navController = navController,
          startDestination = Screen.MainHome.route
        ) {
          // 1. Home (Tabs: Quizzes & Named Answer Keys)
          composable(Screen.MainHome.route) {
            HomeScreen(
              viewModel = viewModel,
              onNavigateToCreateQuiz = {
                navController.navigate(Screen.CreateQuiz.route)
              },
              onNavigateToQuizDetails = { quizId ->
                navController.navigate(Screen.QuizDetails.createRoute(quizId))
              },
              onNavigateToCreateAnswerKey = {
                navController.navigate(Screen.CreateAnswerKey.route)
              },
              onNavigateToEditAnswerKey = { keyId ->
                navController.navigate(Screen.EditAnswerKey.createRoute(keyId))
              },
              onNavigateToAdminPanel = {
                navController.navigate(Screen.AdminPanel.route)
              },
              onSignOut = {
                authViewModel.signOut()
              },
              isAdmin = isAdmin,
              userEmail = currentUser.email,
              authViewModel = authViewModel
            )
          }

          // Admin Panel Screen
          composable(Screen.AdminPanel.route) {
            AdminPanelScreen(
              authViewModel = authViewModel,
              onNavigateBack = { navController.popBackStack() }
            )
          }

          // 2. Create Quiz (16 Qs Default)
          composable(Screen.CreateQuiz.route) {
            CreateQuizScreen(
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() },
              onQuizCreated = { newQuizId ->
                navController.navigate(Screen.QuizDetails.createRoute(newQuizId)) {
                  popUpTo(Screen.MainHome.route)
                }
              }
            )
          }

          // 3. Quiz Details Hub
          composable(
            route = Screen.QuizDetails.route,
            arguments = listOf(navArgument("quizId") { type = NavType.StringType })
          ) { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
            QuizDetailsScreen(
              quizId = quizId,
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() },
              onNavigateToAnswerKey = { keyId ->
                navController.navigate(Screen.EditAnswerKey.createRoute(keyId))
              },
              onNavigateToPrintSheet = { qid ->
                navController.navigate(Screen.PrintSheet.createRoute(qid))
              },
              onNavigateToScan = { qid ->
                navController.navigate(Screen.ScanPapers.createRoute(qid))
              },
              onNavigateToReviewPapers = { qid ->
                navController.navigate(Screen.ReviewPapersList.createRoute(qid))
              }
            )
          }

          // 4. Create Named Answer Key (16 Qs Default)
          composable(Screen.CreateAnswerKey.route) {
            AnswerKeyScreen(
              keyId = null,
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() }
            )
          }

          // 5. Edit Named Answer Key
          composable(
            route = Screen.EditAnswerKey.route,
            arguments = listOf(navArgument("keyId") { type = NavType.StringType })
          ) { backStackEntry ->
            val keyId = backStackEntry.arguments?.getString("keyId") ?: ""
            AnswerKeyScreen(
              keyId = keyId,
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() }
            )
          }

          // 6. Printable OMR Sheet (16 Qs)
          composable(
            route = Screen.PrintSheet.route,
            arguments = listOf(navArgument("quizId") { type = NavType.StringType })
          ) { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
            PrintSheetScreen(
              quizId = quizId,
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() }
            )
          }

          // 7. Scan Papers (Camera + Alignment Guide)
          composable(
            route = Screen.ScanPapers.route,
            arguments = listOf(navArgument("quizId") { type = NavType.StringType })
          ) { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
            ScanPapersScreen(
              quizId = quizId,
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() },
              onNavigateToPaperDetail = { paperId ->
                navController.navigate(Screen.PaperDetail.createRoute(paperId))
              }
            )
          }

          // 8. Review Papers List
          composable(
            route = Screen.ReviewPapersList.route,
            arguments = listOf(navArgument("quizId") { type = NavType.StringType })
          ) { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
            ReviewPapersScreen(
              quizId = quizId,
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() },
              onNavigateToScan = { qid ->
                navController.navigate(Screen.ScanPapers.createRoute(qid))
              },
              onNavigateToPaperDetail = { paperId ->
                navController.navigate(Screen.PaperDetail.createRoute(paperId))
              }
            )
          }

          // 9. Individual Paper Detail & Manual Correction
          composable(
            route = Screen.PaperDetail.route,
            arguments = listOf(navArgument("paperId") { type = NavType.StringType })
          ) { backStackEntry ->
            val paperId = backStackEntry.arguments?.getString("paperId") ?: ""
            PaperDetailScreen(
              paperId = paperId,
              viewModel = viewModel,
              onNavigateBack = { navController.popBackStack() }
            )
          }
        }
      }
    }
  }
}
