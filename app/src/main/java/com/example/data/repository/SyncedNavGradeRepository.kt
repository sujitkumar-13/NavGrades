package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.AnswerKeyItemEntity
import com.example.data.model.AnswerKeySetEntity
import com.example.data.model.QuestionEvaluation
import com.example.data.model.QuizEntity
import com.example.data.model.ScannedPaperEntity
import com.example.data.remote.RemoteNavGradeRepository
import com.example.data.remote.SupabaseConfig
import com.example.data.remote.model.AnswerKeyItemRemote
import com.example.data.remote.model.AnswerKeySetRemote
import com.example.data.remote.model.QuizRemote
import com.example.data.remote.model.ScannedPaperRemote
import com.example.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SyncStatus {
  SYNCED,
  SYNCING,
  OFFLINE,
  ERROR
}

class SyncedNavGradeRepository(
  private val localRepo: OmrRepository,
  private val remoteRepo: RemoteNavGradeRepository = RemoteNavGradeRepository(),
  private val scope: CoroutineScope
) {
  private val tag = "SyncedNavGradeRepo"

  private val _syncStatus = MutableStateFlow(SyncStatus.SYNCED)
  val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

  val allQuizzes: Flow<List<QuizEntity>> = localRepo.allQuizzes
  val allAnswerKeys: Flow<List<AnswerKeySetEntity>> = localRepo.allAnswerKeys

  init {
    if (SupabaseConfig.isConfigured()) {
      triggerSync()
    }
  }

  fun triggerSync(context: Context? = null) {
    scope.launch(Dispatchers.IO) {
      if (!SupabaseConfig.isConfigured()) {
        _syncStatus.value = SyncStatus.SYNCED
        return@launch
      }
      _syncStatus.value = SyncStatus.SYNCING
      try {
        syncFromCloud()
        syncUnsyncedPapers()
        if (context != null) {
          SyncWorker.triggerImmediateSync(context)
        }
        _syncStatus.value = SyncStatus.SYNCED
      } catch (e: Exception) {
        Log.e(tag, "Sync failed: ${e.message}", e)
        _syncStatus.value = SyncStatus.ERROR
      }
    }
  }

  suspend fun syncFromCloud() = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured()) return@withContext
    try {
      // 1. Fetch remote quizzes
      val remoteQuizzes = remoteRepo.getAllQuizzes()
      for (rq in remoteQuizzes) {
        val existing = localRepo.getQuizDirect(rq.id)
        if (existing == null) {
          localRepo.createQuiz(
            name = rq.name,
            date = rq.date,
            numQuestions = rq.numQuestions,
            answerKeyId = rq.answerKeyId,
            templateType = rq.templateType,
            defaultMarks = rq.defaultMarks
          )
        }
      }

      // 2. Fetch remote answer key sets
      val remoteKeys = remoteRepo.getAllAnswerKeySets()
      for (rk in remoteKeys) {
        val existing = localRepo.getAnswerKeySetDirect(rk.id)
        if (existing == null) {
          val items = remoteRepo.getAnswerKeyItems(rk.id)
          val keyMap = items.associate { it.questionIndex to it.correctOption }
          localRepo.createAnswerKeySet(
            name = rk.name,
            numQuestions = rk.numQuestions,
            defaultMarks = rk.defaultMarks,
            keyMap = keyMap
          )
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Error syncing from cloud: ${e.message}", e)
    }
  }

  suspend fun syncUnsyncedPapers() = withContext(Dispatchers.IO) {
    if (!SupabaseConfig.isConfigured()) return@withContext
    try {
      val unsynced = localRepo.getUnsyncedPapers()
      for (paper in unsynced) {
        val remotePaper = ScannedPaperRemote(
          id = paper.id,
          quizId = paper.quizId,
          studentId = paper.studentId,
          studentName = paper.studentName,
          answers = paper.answersJson,
          score = paper.score,
          totalPossibleMarks = paper.totalPossibleMarks,
          percentage = paper.percentage,
          correctCount = paper.correctCount,
          wrongCount = paper.wrongCount,
          blankCount = paper.blankCount,
          multipleCount = paper.multipleCount,
          reviewRequiredCount = paper.reviewRequiredCount,
          imageUrl = paper.imagePath,
          whatsappNumber = paper.whatsappNumber,
          block = paper.block,
          caste = paper.cast,
          gender = paper.gender,
          qualification = paper.qualification,
          questionSetName = paper.questionSetName
        )
        if (remoteRepo.insertScannedPaper(remotePaper)) {
          localRepo.markPaperSynced(paper.id)
        }
      }
    } catch (e: Exception) {
      Log.e(tag, "Error syncing unsynced papers: ${e.message}", e)
    }
  }

  suspend fun regradeQuiz(quizId: String) = localRepo.regradeQuiz(quizId)

  // --- Quizzes ---
  fun observeQuiz(id: String): Flow<QuizEntity?> = localRepo.observeQuiz(id)

  suspend fun getQuizDirect(id: String): QuizEntity? = localRepo.getQuizDirect(id)

  suspend fun createQuiz(
    name: String,
    date: String,
    numQuestions: Int = 16,
    answerKeyId: String? = null,
    templateType: String = "standard_16",
    defaultMarks: Float = 1.0f
  ): String {
    val quizId = localRepo.createQuiz(name, date, numQuestions, answerKeyId, templateType, defaultMarks)
    if (SupabaseConfig.isConfigured()) {
      scope.launch(Dispatchers.IO) {
        remoteRepo.insertQuiz(
          QuizRemote(
            id = quizId,
            name = name,
            date = date,
            numQuestions = numQuestions,
            answerKeyId = answerKeyId,
            templateType = templateType,
            defaultMarks = defaultMarks
          )
        )
      }
    }
    return quizId
  }

  suspend fun updateQuiz(quiz: QuizEntity) = localRepo.updateQuiz(quiz)

  suspend fun deleteQuiz(quizId: String) {
    localRepo.deleteQuiz(quizId)
    if (SupabaseConfig.isConfigured()) {
      scope.launch(Dispatchers.IO) {
        remoteRepo.deleteQuiz(quizId)
      }
    }
  }

  // --- Answer Keys ---
  fun observeAnswerKeySet(keyId: String): Flow<AnswerKeySetEntity?> = localRepo.observeAnswerKeySet(keyId)

  suspend fun getAnswerKeySetDirect(keyId: String): AnswerKeySetEntity? = localRepo.getAnswerKeySetDirect(keyId)

  fun getItemsForKey(keyId: String): Flow<List<AnswerKeyItemEntity>> = localRepo.getItemsForKey(keyId)

  suspend fun getItemsForKeyDirect(keyId: String): List<AnswerKeyItemEntity> = localRepo.getItemsForKeyDirect(keyId)

  suspend fun getAnswerKeysForQuizDirect(quizId: String): List<AnswerKeyItemEntity> = localRepo.getAnswerKeysForQuizDirect(quizId)

  suspend fun createAnswerKeySet(
    name: String,
    numQuestions: Int = 16,
    defaultMarks: Float = 1.0f,
    keyMap: Map<Int, String>? = null
  ): String {
    val keyId = localRepo.createAnswerKeySet(name, numQuestions, defaultMarks, keyMap)
    if (SupabaseConfig.isConfigured()) {
      scope.launch(Dispatchers.IO) {
        remoteRepo.insertAnswerKeySet(
          AnswerKeySetRemote(
            id = keyId,
            name = name,
            numQuestions = numQuestions,
            defaultMarks = defaultMarks
          )
        )
        if (keyMap != null) {
          val items = keyMap.map { (index, opt) ->
            AnswerKeyItemRemote(
              keyId = keyId,
              questionIndex = index,
              correctOption = opt,
              marks = defaultMarks
            )
          }
          remoteRepo.insertAnswerKeyItems(items)
        }
      }
    }
    return keyId
  }

  suspend fun updateAnswerKeySet(
    keyId: String,
    name: String,
    numQuestions: Int,
    items: List<AnswerKeyItemEntity>
  ) {
    localRepo.updateAnswerKeySet(keyId, name, numQuestions, items)
    if (SupabaseConfig.isConfigured()) {
      scope.launch(Dispatchers.IO) {
        remoteRepo.insertAnswerKeyItems(items.map {
          AnswerKeyItemRemote(
            keyId = keyId,
            questionIndex = it.questionIndex,
            correctOption = it.correctOption,
            marks = it.marks
          )
        })
      }
    }
  }

  suspend fun duplicateAnswerKeySet(keyId: String, newName: String): String =
    localRepo.duplicateAnswerKeySet(keyId, newName)

  suspend fun deleteAnswerKeySet(keyId: String) = localRepo.deleteAnswerKeySet(keyId)

  // --- Scanned Papers ---
  fun getPapersForQuiz(quizId: String): Flow<List<ScannedPaperEntity>> = localRepo.getPapersForQuiz(quizId)

  fun observePaper(paperId: String): Flow<ScannedPaperEntity?> = localRepo.observePaper(paperId)

  suspend fun getPaperDirect(paperId: String): ScannedPaperEntity? = localRepo.getPaperDirect(paperId)

  suspend fun deletePaper(paperId: String) = localRepo.deletePaper(paperId)

  suspend fun evaluateAndSavePaper(
    quizId: String,
    studentId: String,
    studentName: String,
    rawAnswers: Map<Int, String>,
    imagePath: String?,
    firstName: String = "",
    lastName: String = "",
    phoneNumber: String = "",
    whatsappNumber: String = "",
    school: String = "",
    block: String = "",
    cast: String = "",
    gender: String = "",
    qualification: String = "",
    questionSetName: String = "",
    courseCode: String = ""
  ): ScannedPaperEntity {
    val paper = localRepo.evaluateAndSavePaper(
      quizId = quizId,
      studentId = studentId,
      studentName = studentName,
      rawAnswers = rawAnswers,
      imagePath = imagePath,
      firstName = firstName,
      lastName = lastName,
      phoneNumber = phoneNumber,
      whatsappNumber = whatsappNumber,
      school = school,
      block = block,
      cast = cast,
      gender = gender,
      qualification = qualification,
      questionSetName = questionSetName,
      courseCode = courseCode
    )
    if (SupabaseConfig.isConfigured()) {
      scope.launch(Dispatchers.IO) {
        remoteRepo.insertScannedPaper(
          ScannedPaperRemote(
            id = paper.id,
            quizId = paper.quizId,
            studentId = paper.studentId,
            studentName = paper.studentName,
            answers = paper.answersJson,
            score = paper.score,
            totalPossibleMarks = paper.totalPossibleMarks,
            percentage = paper.percentage,
            correctCount = paper.correctCount,
            wrongCount = paper.wrongCount,
            blankCount = paper.blankCount,
            multipleCount = paper.multipleCount,
            reviewRequiredCount = paper.reviewRequiredCount,
            imageUrl = paper.imagePath,
            whatsappNumber = paper.whatsappNumber,
            block = paper.block,
            caste = paper.cast,
            gender = paper.gender,
            qualification = paper.qualification,
            questionSetName = paper.questionSetName
          )
        )
      }
    }
    return paper
  }

  suspend fun updateStudentAnswer(
    paperId: String,
    questionNumber: Int,
    newAnswer: String
  ): ScannedPaperEntity? = localRepo.updateStudentAnswer(paperId, questionNumber, newAnswer)

  suspend fun seedSamplePapers(quizId: String) = localRepo.seedSamplePapers(quizId)

  suspend fun getEvaluations(paper: ScannedPaperEntity): List<QuestionEvaluation> =
    localRepo.getEvaluations(paper)

  companion object {
    @Volatile
    private var INSTANCE: SyncedNavGradeRepository? = null

    fun getInstance(context: Context, scope: CoroutineScope): SyncedNavGradeRepository {
      return INSTANCE ?: synchronized(this) {
        val local = OmrRepository.getInstance(context, scope)
        val instance = SyncedNavGradeRepository(local, RemoteNavGradeRepository(), scope)
        INSTANCE = instance
        instance
      }
    }
  }
}
