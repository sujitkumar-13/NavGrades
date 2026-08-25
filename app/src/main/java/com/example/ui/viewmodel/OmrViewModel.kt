package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AnswerKeyItemEntity
import com.example.data.model.AnswerKeySetEntity
import com.example.data.model.QuestionEvaluation
import com.example.data.model.QuizEntity
import com.example.data.model.ScannedPaperEntity
import com.example.data.repository.OmrRepository
import com.example.omr.OmrScanOutput
import com.example.omr.OmrScannerEngine
import com.example.omr.OmrSheetGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OmrViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = OmrRepository.getInstance(application, viewModelScope)

  val allQuizzes: StateFlow<List<QuizEntity>> = repository.allQuizzes
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val allAnswerKeys: StateFlow<List<AnswerKeySetEntity>> = repository.allAnswerKeys
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  private val _selectedQuiz = MutableStateFlow<QuizEntity?>(null)
  val selectedQuiz: StateFlow<QuizEntity?> = _selectedQuiz.asStateFlow()

  private val _selectedAnswerKeySet = MutableStateFlow<AnswerKeySetEntity?>(null)
  val selectedAnswerKeySet: StateFlow<AnswerKeySetEntity?> = _selectedAnswerKeySet.asStateFlow()

  private val _currentKeyItems = MutableStateFlow<List<AnswerKeyItemEntity>>(emptyList())
  val currentKeyItems: StateFlow<List<AnswerKeyItemEntity>> = _currentKeyItems.asStateFlow()

  private val _quizAnswerKeyItems = MutableStateFlow<List<AnswerKeyItemEntity>>(emptyList())
  val quizAnswerKeyItems: StateFlow<List<AnswerKeyItemEntity>> = _quizAnswerKeyItems.asStateFlow()

  private val _papers = MutableStateFlow<List<ScannedPaperEntity>>(emptyList())
  val papers: StateFlow<List<ScannedPaperEntity>> = _papers.asStateFlow()

  private val _selectedPaper = MutableStateFlow<ScannedPaperEntity?>(null)
  val selectedPaper: StateFlow<ScannedPaperEntity?> = _selectedPaper.asStateFlow()

  private val _paperEvaluations = MutableStateFlow<List<QuestionEvaluation>>(emptyList())
  val paperEvaluations: StateFlow<List<QuestionEvaluation>> = _paperEvaluations.asStateFlow()

  // Scanning State
  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val _lastScannedPaper = MutableStateFlow<ScannedPaperEntity?>(null)
  val lastScannedPaper: StateFlow<ScannedPaperEntity?> = _lastScannedPaper.asStateFlow()

  private val _scanError = MutableStateFlow<String?>(null)
  val scanError: StateFlow<String?> = _scanError.asStateFlow()

  fun loadQuiz(quizId: String) {
    viewModelScope.launch {
      val quiz = repository.getQuizDirect(quizId)
      _selectedQuiz.value = quiz
      if (quiz != null) {
        val keys = repository.getAnswerKeysForQuizDirect(quizId)
        _quizAnswerKeyItems.value = keys
        repository.getPapersForQuiz(quizId).collect { paperList ->
          _papers.value = paperList
        }
      }
    }
  }

  fun createQuiz(
    name: String,
    date: String,
    numQuestions: Int = 16,
    answerKeyId: String? = null,
    templateType: String = "standard_16",
    defaultMarks: Float = 1.0f,
    onSuccess: (String) -> Unit
  ) {
    viewModelScope.launch {
      val quizId = repository.createQuiz(name, date, numQuestions, answerKeyId, templateType, defaultMarks)
      loadQuiz(quizId)
      onSuccess(quizId)
    }
  }

  fun updateQuizAnswerKey(quizId: String, newAnswerKeyId: String, onUpdated: () -> Unit = {}) {
    viewModelScope.launch {
      val quiz = repository.getQuizDirect(quizId) ?: return@launch
      val answerKeySet = repository.getAnswerKeySetDirect(newAnswerKeyId)
      val updatedQuiz = quiz.copy(
        answerKeyId = newAnswerKeyId,
        numQuestions = answerKeySet?.numQuestions ?: quiz.numQuestions
      )
      repository.updateQuiz(updatedQuiz)
      loadQuiz(quizId)
      onUpdated()
    }
  }

  fun deleteQuiz(quizId: String, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      repository.deleteQuiz(quizId)
      if (_selectedQuiz.value?.id == quizId) {
        _selectedQuiz.value = null
      }
      onDeleted()
    }
  }

  fun seedSamplePapers(quizId: String, onDone: () -> Unit = {}) {
    viewModelScope.launch {
      repository.seedSamplePapers(quizId)
      loadQuiz(quizId)
      onDone()
    }
  }

  // Answer Key Management
  fun loadAnswerKeySet(keyId: String) {
    viewModelScope.launch {
      val keySet = repository.getAnswerKeySetDirect(keyId)
      _selectedAnswerKeySet.value = keySet
      if (keySet != null) {
        val items = repository.getItemsForKeyDirect(keyId)
        _currentKeyItems.value = items
      }
    }
  }

  fun createAnswerKeySet(
    name: String,
    numQuestions: Int = 16,
    defaultMarks: Float = 1.0f,
    keyMap: Map<Int, String>? = null,
    onSuccess: (String) -> Unit
  ) {
    viewModelScope.launch {
      val keyId = repository.createAnswerKeySet(name, numQuestions, defaultMarks, keyMap)
      loadAnswerKeySet(keyId)
      onSuccess(keyId)
    }
  }

  fun saveAnswerKeySet(
    keyId: String,
    name: String,
    numQuestions: Int,
    items: List<AnswerKeyItemEntity>,
    onSaved: () -> Unit
  ) {
    viewModelScope.launch {
      repository.updateAnswerKeySet(keyId, name, numQuestions, items)
      _currentKeyItems.value = items
      _selectedAnswerKeySet.value = repository.getAnswerKeySetDirect(keyId)
      onSaved()
    }
  }

  fun duplicateAnswerKeySet(keyId: String, newName: String, onComplete: (String) -> Unit = {}) {
    viewModelScope.launch {
      val newId = repository.duplicateAnswerKeySet(keyId, newName)
      onComplete(newId)
    }
  }

  fun deleteAnswerKeySet(keyId: String, onDeleted: () -> Unit = {}) {
    viewModelScope.launch {
      repository.deleteAnswerKeySet(keyId)
      if (_selectedAnswerKeySet.value?.id == keyId) {
        _selectedAnswerKeySet.value = null
        _currentKeyItems.value = emptyList()
      }
      onDeleted()
    }
  }

  fun loadPaper(paperId: String) {
    viewModelScope.launch {
      val paper = repository.getPaperDirect(paperId)
      _selectedPaper.value = paper
      if (paper != null) {
        val evals = repository.getEvaluations(paper)
        _paperEvaluations.value = evals
      }
    }
  }

  fun processScannedBitmap(
    bitmap: Bitmap,
    studentNameOverride: String? = null,
    onSuccess: (ScannedPaperEntity) -> Unit
  ) {
    val quiz = _selectedQuiz.value ?: return
    _isScanning.value = true
    _scanError.value = null

    viewModelScope.launch {
      try {
        val scanOutput: OmrScanOutput = OmrScannerEngine.processOmrImage(
          sourceBitmap = bitmap,
          numQuestions = quiz.numQuestions,
          defaultStudentName = studentNameOverride ?: "Student ${(_papers.value.size + 1)}"
        )

        val imagePath = scanOutput.annotatedBitmap?.let { annotatedBmp ->
          OmrSheetGenerator.saveBitmapToFile(
            context = getApplication(),
            bitmap = annotatedBmp,
            fileName = "scan_${quiz.id}_${System.currentTimeMillis()}"
          )
        }

        val savedPaper = repository.evaluateAndSavePaper(
          quizId = quiz.id,
          studentId = scanOutput.studentId,
          studentName = scanOutput.studentName,
          rawAnswers = scanOutput.detectedAnswers,
          imagePath = imagePath,
          whatsappNumber = scanOutput.whatsappNumber,
          block = scanOutput.block,
          cast = scanOutput.cast,
          gender = scanOutput.gender,
          qualification = scanOutput.qualification,
          questionSetName = scanOutput.questionSetName
        )

        _lastScannedPaper.value = savedPaper
        _isScanning.value = false
        onSuccess(savedPaper)
      } catch (e: Exception) {
        e.printStackTrace()
        _scanError.value = "Failed to process OMR sheet: ${e.localizedMessage}"
        _isScanning.value = false
      }
    }
  }

  fun processImageFromUri(uri: Uri, onSuccess: (ScannedPaperEntity) -> Unit) {
    viewModelScope.launch {
      try {
        val context = getApplication<Application>()
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        if (bitmap != null) {
          processScannedBitmap(bitmap, onSuccess = onSuccess)
        } else {
          _scanError.value = "Could not decode image from gallery."
        }
      } catch (e: Exception) {
        _scanError.value = "Error opening image: ${e.localizedMessage}"
      }
    }
  }

  fun simulateSampleScan(
    profileType: String,
    onSuccess: (ScannedPaperEntity) -> Unit
  ) {
    val quiz = _selectedQuiz.value ?: return
    viewModelScope.launch {
      val keys = repository.getAnswerKeysForQuizDirect(quiz.id)
      val numQuestions = quiz.numQuestions

      val studentName: String
      val studentId: String
      val filledAnswers = mutableMapOf<Int, String>()

      when (profileType) {
        "priya_top" -> {
          studentName = "Priya Sharma"
          studentId = "NG20261"
          for (q in 1..numQuestions) {
            val key = keys.find { it.questionIndex == q }?.correctOption ?: "A"
            if (q == 3) {
              filledAnswers[q] = if (key == "A") "B" else "A"
            } else {
              filledAnswers[q] = key
            }
          }
        }
        "amit_review" -> {
          studentName = "Amit Patel"
          studentId = "NG20263"
          for (q in 1..numQuestions) {
            val key = keys.find { it.questionIndex == q }?.correctOption ?: "A"
            when (q) {
              5 -> filledAnswers[q] = "MULTIPLE"
              8 -> filledAnswers[q] = "BLANK"
              12 -> filledAnswers[q] = if (key == "B") "C" else "B"
              else -> filledAnswers[q] = key
            }
          }
        }
        "vikram_average" -> {
          studentName = "Vikram Singh"
          studentId = "NG20264"
          for (q in 1..numQuestions) {
            val key = keys.find { it.questionIndex == q }?.correctOption ?: "A"
            if (q > (numQuestions - 3)) {
              filledAnswers[q] = "BLANK"
            } else if (q % 3 == 0) {
              filledAnswers[q] = if (key == "C") "D" else "C"
            } else {
              filledAnswers[q] = key
            }
          }
        }
        else -> {
          studentName = "Rahul Kumar"
          studentId = "NG12345"
          for (q in 1..numQuestions) {
            val key = keys.find { it.questionIndex == q }?.correctOption ?: "B"
            if (q in listOf(2, 6, 10, 14)) {
              filledAnswers[q] = if (key == "A") "D" else "A"
            } else {
              filledAnswers[q] = key
            }
          }
        }
      }

      val sampleBitmap = OmrSheetGenerator.generateFilledSampleSheetBitmap(
        quizName = quiz.name,
        date = quiz.date,
        numQuestions = numQuestions,
        studentId = studentId,
        studentName = studentName,
        filledAnswers = filledAnswers
      )

      processScannedBitmap(sampleBitmap, studentNameOverride = studentName, onSuccess = onSuccess)
    }
  }

  fun updateStudentAnswer(paperId: String, questionNumber: Int, newAnswer: String) {
    viewModelScope.launch {
      val updated = repository.updateStudentAnswer(paperId, questionNumber, newAnswer)
      if (updated != null) {
        _selectedPaper.value = updated
        _paperEvaluations.value = repository.getEvaluations(updated)
      }
    }
  }

  fun deletePaper(paperId: String, onDeleted: () -> Unit) {
    viewModelScope.launch {
      repository.deletePaper(paperId)
      if (_selectedPaper.value?.id == paperId) {
        _selectedPaper.value = null
      }
      onDeleted()
    }
  }

  fun clearScanResult() {
    _lastScannedPaper.value = null
    _scanError.value = null
  }
}
