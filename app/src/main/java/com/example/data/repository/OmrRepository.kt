package com.example.data.repository

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.model.AnswerKeyItemEntity
import com.example.data.model.AnswerKeySetEntity
import com.example.data.model.EvaluationStatus
import com.example.data.model.QuestionEvaluation
import com.example.data.model.QuizEntity
import com.example.data.model.ScannedPaperEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class OmrRepository(
  private val database: AppDatabase,
  private val scope: CoroutineScope
) {
  private val quizDao = database.quizDao()
  private val answerKeySetDao = database.answerKeySetDao()
  private val answerKeyDao = database.answerKeyDao()
  private val scannedPaperDao = database.scannedPaperDao()

  val allQuizzes: Flow<List<QuizEntity>> = quizDao.getAllQuizzes()
  val allAnswerKeys: Flow<List<AnswerKeySetEntity>> = answerKeySetDao.getAllAnswerKeySets()

  fun observeQuiz(id: String): Flow<QuizEntity?> = quizDao.observeQuizById(id)

  suspend fun getQuizDirect(id: String): QuizEntity? = withContext(Dispatchers.IO) {
    quizDao.getQuizById(id)
  }

  suspend fun createQuiz(
    name: String,
    date: String,
    numQuestions: Int = 16,
    answerKeyId: String? = null,
    templateType: String = "standard_16",
    defaultMarks: Float = 1.0f
  ): String = withContext(Dispatchers.IO) {
    var finalKeyId = answerKeyId

    // If no answer key is selected, look for an existing answer key with matching question count or create one
    if (finalKeyId == null) {
      val existingKeys = answerKeySetDao.getAllAnswerKeySets().firstOrNull() ?: emptyList()
      val matchedKey = existingKeys.firstOrNull { it.numQuestions == numQuestions } ?: existingKeys.firstOrNull()
      if (matchedKey != null) {
        finalKeyId = matchedKey.id
      } else {
        // Create a default named answer key
        val newKeyName = "${name.trim()} Key Set (${numQuestions} Qs)"
        finalKeyId = createAnswerKeySetInternal(newKeyName, numQuestions, defaultMarks)
      }
    }

    val quizId = UUID.randomUUID().toString()
    val quiz = QuizEntity(
      id = quizId,
      name = name.trim(),
      date = date.trim(),
      numQuestions = numQuestions,
      answerKeyId = finalKeyId,
      templateType = templateType,
      defaultMarks = defaultMarks,
      createdAt = System.currentTimeMillis()
    )
    quizDao.insertQuiz(quiz)
    quizId
  }

  suspend fun updateQuiz(quiz: QuizEntity) = withContext(Dispatchers.IO) {
    quizDao.updateQuiz(quiz)
    // Re-grade papers if answer key was changed
    regradeQuiz(quiz.id)
  }

  suspend fun deleteQuiz(quizId: String) = withContext(Dispatchers.IO) {
    quizDao.deleteQuizById(quizId)
    scannedPaperDao.deletePapersForQuiz(quizId)
  }

  // Answer Key Set Management (Global named keys)
  fun observeAnswerKeySet(keyId: String): Flow<AnswerKeySetEntity?> =
    answerKeySetDao.observeAnswerKeySetById(keyId)

  suspend fun getAnswerKeySetDirect(keyId: String): AnswerKeySetEntity? = withContext(Dispatchers.IO) {
    answerKeySetDao.getAnswerKeySetById(keyId)
  }

  fun getItemsForKey(keyId: String): Flow<List<AnswerKeyItemEntity>> =
    answerKeyDao.getItemsForKey(keyId)

  suspend fun getItemsForKeyDirect(keyId: String): List<AnswerKeyItemEntity> = withContext(Dispatchers.IO) {
    answerKeyDao.getItemsForKeyDirect(keyId)
  }

  private suspend fun createAnswerKeySetInternal(
    name: String,
    numQuestions: Int,
    defaultMarks: Float,
    keyMap: Map<Int, String>? = null
  ): String {
    val keyId = UUID.randomUUID().toString()
    val set = AnswerKeySetEntity(
      id = keyId,
      name = name.trim(),
      numQuestions = numQuestions,
      defaultMarks = defaultMarks,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis()
    )
    answerKeySetDao.insertAnswerKeySet(set)

    val optionsPattern = listOf("A", "B", "C", "D")
    val items = (1..numQuestions).map { qIndex ->
      val opt = keyMap?.get(qIndex) ?: optionsPattern[(qIndex - 1) % 4]
      AnswerKeyItemEntity(
        keyId = keyId,
        questionIndex = qIndex,
        correctOption = opt,
        marks = defaultMarks
      )
    }
    answerKeyDao.insertItems(items)
    return keyId
  }

  suspend fun createAnswerKeySet(
    name: String,
    numQuestions: Int = 16,
    defaultMarks: Float = 1.0f,
    keyMap: Map<Int, String>? = null
  ): String = withContext(Dispatchers.IO) {
    createAnswerKeySetInternal(name, numQuestions, defaultMarks, keyMap)
  }

  suspend fun updateAnswerKeySet(
    keyId: String,
    name: String,
    numQuestions: Int,
    items: List<AnswerKeyItemEntity>
  ) = withContext(Dispatchers.IO) {
    val existing = answerKeySetDao.getAnswerKeySetById(keyId)
    val updated = (existing ?: AnswerKeySetEntity(id = keyId, name = name, numQuestions = numQuestions)).copy(
      name = name.trim(),
      numQuestions = numQuestions,
      updatedAt = System.currentTimeMillis()
    )
    answerKeySetDao.insertAnswerKeySet(updated)

    answerKeyDao.deleteItemsForKey(keyId)
    answerKeyDao.insertItems(items)

    // Re-grade all quizzes linked to this answer key!
    val linkedQuizzes = quizDao.getQuizzesByAnswerKeyId(keyId)
    linkedQuizzes.forEach { q ->
      regradeQuiz(q.id)
    }
  }

  suspend fun duplicateAnswerKeySet(keyId: String, newName: String): String = withContext(Dispatchers.IO) {
    val original = answerKeySetDao.getAnswerKeySetById(keyId) ?: return@withContext ""
    val originalItems = answerKeyDao.getItemsForKeyDirect(keyId)

    val newKeyId = UUID.randomUUID().toString()
    val newSet = AnswerKeySetEntity(
      id = newKeyId,
      name = newName.trim(),
      numQuestions = original.numQuestions,
      defaultMarks = original.defaultMarks,
      createdAt = System.currentTimeMillis(),
      updatedAt = System.currentTimeMillis()
    )
    answerKeySetDao.insertAnswerKeySet(newSet)

    val newItems = originalItems.map { it.copy(keyId = newKeyId) }
    answerKeyDao.insertItems(newItems)
    newKeyId
  }

  suspend fun deleteAnswerKeySet(keyId: String) = withContext(Dispatchers.IO) {
    answerKeySetDao.deleteAnswerKeySetById(keyId)
    answerKeyDao.deleteItemsForKey(keyId)
  }

  suspend fun getAnswerKeysForQuizDirect(quizId: String): List<AnswerKeyItemEntity> = withContext(Dispatchers.IO) {
    val quiz = quizDao.getQuizById(quizId) ?: return@withContext emptyList()
    val keyId = quiz.answerKeyId
    if (keyId != null) {
      val items = answerKeyDao.getItemsForKeyDirect(keyId)
      if (items.isNotEmpty()) return@withContext items
    }
    // Fallback: Check first available key set or generate fallback items
    val firstKeySet = answerKeySetDao.getAllAnswerKeySets().firstOrNull()?.firstOrNull()
    if (firstKeySet != null) {
      val items = answerKeyDao.getItemsForKeyDirect(firstKeySet.id)
      if (items.isNotEmpty()) return@withContext items
    }

    // Default 16-question fallback items
    (1..quiz.numQuestions).map { q ->
      AnswerKeyItemEntity(
        keyId = "fallback",
        questionIndex = q,
        correctOption = listOf("A", "B", "C", "D")[(q - 1) % 4],
        marks = quiz.defaultMarks
      )
    }
  }

  suspend fun regradeQuiz(quizId: String) = withContext(Dispatchers.IO) {
    val keys = getAnswerKeysForQuizDirect(quizId)
    if (keys.isEmpty()) return@withContext

    val existingPapers = scannedPaperDao.getPapersForQuiz(quizId).firstOrNull() ?: emptyList()
    val keysMap = keys.associateBy { it.questionIndex }
    val totalPossibleMarks = keys.sumOf { it.marks.toDouble() }.toFloat().coerceAtLeast(1.0f)

    existingPapers.forEach { paper ->
      val answersJson = JSONObject(paper.answersJson)
      var newScore = 0f
      var correct = 0
      var wrong = 0
      var blank = 0
      var multiple = 0
      var reviewReq = 0

      for (qIndex in 1..keys.size) {
        val studentAns = answersJson.optString(qIndex.toString(), "BLANK")
        val correctKey = keysMap[qIndex]
        val expectedAns = correctKey?.correctOption ?: ""
        val questionMarks = correctKey?.marks ?: 1.0f

        when (studentAns) {
          expectedAns -> {
            newScore += questionMarks
            correct++
          }
          "BLANK" -> blank++
          "MULTIPLE" -> {
            multiple++
            wrong++
          }
          "REVIEW" -> {
            reviewReq++
            wrong++
          }
          else -> wrong++
        }
      }

      val percentage = if (totalPossibleMarks > 0) (newScore / totalPossibleMarks) * 100f else 0f
      val updatedPaper = paper.copy(
        score = newScore,
        totalPossibleMarks = totalPossibleMarks,
        percentage = percentage,
        correctCount = correct,
        wrongCount = wrong,
        blankCount = blank,
        multipleCount = multiple,
        reviewRequiredCount = reviewReq
      )
      scannedPaperDao.updatePaper(updatedPaper)
    }
  }

  // Scanned Papers Operations
  fun getPapersForQuiz(quizId: String): Flow<List<ScannedPaperEntity>> =
    scannedPaperDao.getPapersForQuiz(quizId)

  fun observePaper(paperId: String): Flow<ScannedPaperEntity?> =
    scannedPaperDao.observePaperById(paperId)

  suspend fun getPaperDirect(paperId: String): ScannedPaperEntity? = withContext(Dispatchers.IO) {
    scannedPaperDao.getPaperById(paperId)
  }

  suspend fun deletePaper(paperId: String) = withContext(Dispatchers.IO) {
    scannedPaperDao.deletePaperById(paperId)
  }

  suspend fun evaluateAndSavePaper(
    quizId: String,
    studentId: String,
    studentName: String,
    rawAnswers: Map<Int, String>,
    imagePath: String?,
    whatsappNumber: String = "",
    block: String = "",
    cast: String = "",
    gender: String = "",
    qualification: String = "",
    questionSetName: String = ""
  ): ScannedPaperEntity = withContext(Dispatchers.IO) {
    val answerKeys = getAnswerKeysForQuizDirect(quizId).associateBy { it.questionIndex }
    val quiz = quizDao.getQuizById(quizId)
    val numQuestions = quiz?.numQuestions ?: rawAnswers.size

    val answersJson = JSONObject()
    var totalScore = 0f
    var correctCount = 0
    var wrongCount = 0
    var blankCount = 0
    var multipleCount = 0
    var reviewReqCount = 0
    var totalPossibleMarks = 0f

    for (qIndex in 1..numQuestions) {
      val studentAns = rawAnswers[qIndex] ?: "BLANK"
      answersJson.put(qIndex.toString(), studentAns)

      val key = answerKeys[qIndex]
      val expectedAns = key?.correctOption ?: "A"
      val markValue = key?.marks ?: 1.0f
      totalPossibleMarks += markValue

      when (studentAns) {
        expectedAns -> {
          totalScore += markValue
          correctCount++
        }
        "BLANK" -> {
          blankCount++
        }
        "MULTIPLE" -> {
          multipleCount++
          wrongCount++
        }
        "REVIEW" -> {
          reviewReqCount++
          wrongCount++
        }
        else -> {
          wrongCount++
        }
      }
    }

    if (totalPossibleMarks <= 0f) totalPossibleMarks = numQuestions.toFloat()
    val percentage = (totalScore / totalPossibleMarks) * 100f

    val paper = ScannedPaperEntity(
      id = UUID.randomUUID().toString(),
      quizId = quizId,
      studentId = studentId.ifBlank { "NG-" + (10000 + (1..8999).random()) },
      studentName = studentName.ifBlank { "Student" },
      answersJson = answersJson.toString(),
      score = totalScore,
      totalPossibleMarks = totalPossibleMarks,
      percentage = percentage,
      correctCount = correctCount,
      wrongCount = wrongCount,
      blankCount = blankCount,
      multipleCount = multipleCount,
      reviewRequiredCount = reviewReqCount,
      imagePath = imagePath,
      whatsappNumber = whatsappNumber,
      block = block,
      cast = cast,
      gender = gender,
      qualification = qualification,
      questionSetName = questionSetName,
      scannedAt = System.currentTimeMillis()
    )

    scannedPaperDao.insertPaper(paper)
    paper
  }

  suspend fun updateStudentAnswer(
    paperId: String,
    questionNumber: Int,
    newAnswer: String
  ): ScannedPaperEntity? = withContext(Dispatchers.IO) {
    val paper = scannedPaperDao.getPaperById(paperId) ?: return@withContext null
    val answerKeys = getAnswerKeysForQuizDirect(paper.quizId).associateBy { it.questionIndex }
    val quiz = quizDao.getQuizById(paper.quizId)
    val numQuestions = quiz?.numQuestions ?: 16

    val answersJson = JSONObject(paper.answersJson)
    answersJson.put(questionNumber.toString(), newAnswer)

    var totalScore = 0f
    var correctCount = 0
    var wrongCount = 0
    var blankCount = 0
    var multipleCount = 0
    var reviewReqCount = 0
    var totalPossibleMarks = 0f

    for (qIndex in 1..numQuestions) {
      val studentAns = answersJson.optString(qIndex.toString(), "BLANK")
      val key = answerKeys[qIndex]
      val expectedAns = key?.correctOption ?: "A"
      val markValue = key?.marks ?: 1.0f
      totalPossibleMarks += markValue

      when (studentAns) {
        expectedAns -> {
          totalScore += markValue
          correctCount++
        }
        "BLANK" -> {
          blankCount++
        }
        "MULTIPLE" -> {
          multipleCount++
          wrongCount++
        }
        "REVIEW" -> {
          reviewReqCount++
          wrongCount++
        }
        else -> {
          wrongCount++
        }
      }
    }

    if (totalPossibleMarks <= 0f) totalPossibleMarks = numQuestions.toFloat()
    val percentage = (totalScore / totalPossibleMarks) * 100f

    val updatedPaper = paper.copy(
      answersJson = answersJson.toString(),
      score = totalScore,
      totalPossibleMarks = totalPossibleMarks,
      percentage = percentage,
      correctCount = correctCount,
      wrongCount = wrongCount,
      blankCount = blankCount,
      multipleCount = multipleCount,
      reviewRequiredCount = reviewReqCount
    )

    scannedPaperDao.updatePaper(updatedPaper)
    updatedPaper
  }

  suspend fun seedSamplePapers(quizId: String) = withContext(Dispatchers.IO) {
    val quiz = quizDao.getQuizById(quizId) ?: return@withContext
    val answerKeys = getAnswerKeysForQuizDirect(quizId).associateBy { it.questionIndex }
    val numQuestions = quiz.numQuestions

    val sampleStudents = listOf(
      Triple("Pooja Yadav", "9123456780", "Sukma") to Triple("OBC", "Female", "12th Pass"),
      Triple("Rahul Kumar", "9876543210", "Sukma") to Triple("General", "Male", "12th Pass"),
      Triple("Priya Sharma", "9340386750", "Beicha") to Triple("OBC", "Female", "B.A III"),
      Triple("Amit Netam", "8877665544", "Konta") to Triple("ST", "Male", "10th Pass"),
      Triple("Kavita Markam", "9826123456", "Dornapal") to Triple("ST", "Female", "B.Sc 1st Year"),
      Triple("Suresh Kashyap", "7000192837", "Sukma") to Triple("SC", "Male", "Graduate"),
      Triple("Sunita Sahu", "9425234567", "Chhindgarh") to Triple("OBC", "Female", "12th Pass")
    )

    sampleStudents.forEachIndexed { index, (basic, extra) ->
      val (name, phone, block) = basic
      val (caste, gender, qual) = extra
      val setName = if (index % 2 == 0) "Set - A" else "Set - B"

      val answersJson = JSONObject()
      var correctCount = 0
      var wrongCount = 0
      var score = 0.0f
      var totalPossible = 0.0f

      for (q in 1..numQuestions) {
        val key = answerKeys[q]
        val expected = key?.correctOption ?: "A"
        val marks = key?.marks ?: 1.0f
        totalPossible += marks

        // vary correctness per student
        val isCorrect = when (index) {
          0 -> true // 100%
          1 -> q !in listOf(3, 7, 11) // 13/16
          2 -> q !in listOf(6) // 15/16
          3 -> q !in listOf(2, 5, 9, 14) // 12/16
          4 -> q !in listOf(1, 4, 8) // 13/16
          5 -> q !in listOf(3, 6, 9, 12, 15) // 11/16
          else -> q !in listOf(4, 10) // 14/16
        }

        if (isCorrect) {
          answersJson.put(q.toString(), expected)
          correctCount++
          score += marks
        } else {
          val wrongOpt = when (expected) {
            "A" -> "B"
            "B" -> "C"
            "C" -> "D"
            else -> "A"
          }
          answersJson.put(q.toString(), wrongOpt)
          wrongCount++
        }
      }

      val percentage = if (totalPossible > 0) (score / totalPossible) * 100f else 0.0f

      scannedPaperDao.insertPaper(
        ScannedPaperEntity(
          id = "seed-${quizId}-${index + 1}-${System.currentTimeMillis() % 100000}",
          quizId = quizId,
          studentId = "NG${phone.takeLast(5)}",
          studentName = name,
          answersJson = answersJson.toString(),
          score = score,
          totalPossibleMarks = totalPossible,
          percentage = percentage,
          correctCount = correctCount,
          wrongCount = wrongCount,
          blankCount = 0,
          multipleCount = 0,
          reviewRequiredCount = 0,
          whatsappNumber = phone,
          block = block,
          cast = caste,
          gender = gender,
          qualification = qual,
          questionSetName = setName,
          scannedAt = System.currentTimeMillis() - ((index + 1) * 900000L)
        )
      )
    }
  }

  suspend fun getEvaluations(paper: ScannedPaperEntity): List<QuestionEvaluation> = withContext(Dispatchers.IO) {
    val answerKeys = getAnswerKeysForQuizDirect(paper.quizId).associateBy { it.questionIndex }
    val quiz = quizDao.getQuizById(paper.quizId)
    val numQuestions = quiz?.numQuestions ?: 16
    val answersJson = JSONObject(paper.answersJson)

    (1..numQuestions).map { qIndex ->
      val studentAns = answersJson.optString(qIndex.toString(), "BLANK")
      val key = answerKeys[qIndex]
      val expectedAns = key?.correctOption ?: "A"
      val maxMarks = key?.marks ?: 1.0f

      val status = when (studentAns) {
        expectedAns -> EvaluationStatus.CORRECT
        "BLANK" -> EvaluationStatus.BLANK
        "MULTIPLE" -> EvaluationStatus.MULTIPLE
        "REVIEW" -> EvaluationStatus.REVIEW_REQUIRED
        else -> EvaluationStatus.WRONG
      }

      val marksAwarded = if (status == EvaluationStatus.CORRECT) maxMarks else 0.0f

      QuestionEvaluation(
        questionNumber = qIndex,
        studentAnswer = studentAns,
        correctAnswer = expectedAns,
        marksAwarded = marksAwarded,
        maxMarks = maxMarks,
        status = status
      )
    }
  }

  suspend fun getUnsyncedPapers(): List<ScannedPaperEntity> = withContext(Dispatchers.IO) {
    scannedPaperDao.getUnsyncedPapers()
  }

  suspend fun markPaperSynced(id: String, syncedAt: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
    scannedPaperDao.markPaperSynced(id, syncedAt)
  }

  companion object {
    @Volatile
    private var INSTANCE: OmrRepository? = null

    fun getInstance(context: Context, scope: CoroutineScope): OmrRepository {
      return INSTANCE ?: synchronized(this) {
        val database = AppDatabase.getDatabase(context, scope)
        val instance = OmrRepository(database, scope)
        INSTANCE = instance
        instance
      }
    }
  }
}
