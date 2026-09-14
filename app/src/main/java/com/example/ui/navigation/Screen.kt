package com.example.ui.navigation

sealed class Screen(val route: String) {
  object MainHome : Screen("main_home")
  object CreateQuiz : Screen("create_quiz")
  object QuizDetails : Screen("quiz_details/{quizId}") {
    fun createRoute(quizId: String) = "quiz_details/$quizId"
  }
  object CreateAnswerKey : Screen("create_answer_key")
  object EditAnswerKey : Screen("edit_answer_key/{keyId}") {
    fun createRoute(keyId: String) = "edit_answer_key/$keyId"
  }
  object PrintSheet : Screen("print_sheet/{quizId}") {
    fun createRoute(quizId: String) = "print_sheet/$quizId"
  }
  object ScanPapers : Screen("scan_papers/{quizId}") {
    fun createRoute(quizId: String) = "scan_papers/$quizId"
  }
  object ReviewPapersList : Screen("review_papers_list/{quizId}") {
    fun createRoute(quizId: String) = "review_papers_list/$quizId"
  }
  object PaperDetail : Screen("paper_detail/{paperId}") {
    fun createRoute(paperId: String) = "paper_detail/$paperId"
  }
  object Login : Screen("login")
  object PendingApproval : Screen("pending_approval/{email}/{name}") {
    fun createRoute(email: String, name: String) = "pending_approval/$email/$name"
  }
  object AccessDenied : Screen("access_denied/{email}") {
    fun createRoute(email: String) = "access_denied/$email"
  }
  object AdminPanel : Screen("admin_panel")
}
