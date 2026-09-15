package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AnswerKeyDao
import com.example.data.dao.AnswerKeySetDao
import com.example.data.dao.QuizDao
import com.example.data.dao.ScannedPaperDao
import com.example.data.model.AnswerKeyItemEntity
import com.example.data.model.AnswerKeySetEntity
import com.example.data.model.QuizEntity
import com.example.data.model.ScannedPaperEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@Database(
  entities = [
    QuizEntity::class,
    AnswerKeySetEntity::class,
    AnswerKeyItemEntity::class,
    ScannedPaperEntity::class
  ],
  version = 5,
  exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
  abstract fun quizDao(): QuizDao
  abstract fun answerKeySetDao(): AnswerKeySetDao
  abstract fun answerKeyDao(): AnswerKeyDao
  abstract fun scannedPaperDao(): ScannedPaperDao

  companion object {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          AppDatabase::class.java,
          "omr_database_v2.db"
        )
        .fallbackToDestructiveMigration(dropAllTables = true)
        .addCallback(DatabaseCallback(scope))
        .build()
        INSTANCE = instance
        instance
      }
    }

    private class DatabaseCallback(private val scope: CoroutineScope) : RoomDatabase.Callback() {
      override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        INSTANCE?.let { database ->
          scope.launch(Dispatchers.IO) {
            populateInitialData(database)
          }
        }
      }
    }

    private suspend fun populateInitialData(database: AppDatabase) {
      val quizDao = database.quizDao()
      val answerKeySetDao = database.answerKeySetDao()
      val answerKeyDao = database.answerKeyDao()
      val paperDao = database.scannedPaperDao()

      // 1. Create Global Named Answer Key: "Seminar Key Set A (16 Qs)"
      val key1Id = "key-seminar-set-a"
      val keySet1 = AnswerKeySetEntity(
        id = key1Id,
        name = "Seminar Key Set A",
        numQuestions = 16,
        defaultMarks = 1.0f,
        createdAt = System.currentTimeMillis() - 86400000L,
        updatedAt = System.currentTimeMillis() - 86400000L
      )
      answerKeySetDao.insertAnswerKeySet(keySet1)

      val key1Options = listOf("B", "D", "A", "C", "B", "A", "C", "D", "A", "C", "B", "D", "C", "B", "D", "A")
      val key1Items = (1..16).map { qIndex ->
        AnswerKeyItemEntity(
          keyId = key1Id,
          questionIndex = qIndex,
          correctOption = key1Options[qIndex - 1],
          marks = 1.0f
        )
      }
      answerKeyDao.insertItems(key1Items)

      // 2. Create Global Named Answer Key: "Seminar Key Set B (16 Qs)"
      val key2Id = "key-seminar-set-b"
      val keySet2 = AnswerKeySetEntity(
        id = key2Id,
        name = "Seminar Key Set B",
        numQuestions = 16,
        defaultMarks = 1.0f,
        createdAt = System.currentTimeMillis() - 43200000L,
        updatedAt = System.currentTimeMillis() - 43200000L
      )
      answerKeySetDao.insertAnswerKeySet(keySet2)

      val key2Options = listOf("A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D", "A", "B", "C", "D")
      val key2Items = (1..16).map { qIndex ->
        AnswerKeyItemEntity(
          keyId = key2Id,
          questionIndex = qIndex,
          correctOption = key2Options[qIndex - 1],
          marks = 1.0f
        )
      }
      answerKeyDao.insertItems(key2Items)

      // 3. Create Sample Quiz: "Sukma SOB 2026" (16 Questions, using Seminar Key Set A)
      val quiz1Id = "sukma-sob-2026"
      val quiz1 = QuizEntity(
        id = quiz1Id,
        name = "Sukma SOB 2026",
        date = "21 August 2026",
        numQuestions = 16,
        answerKeyId = key1Id,
        templateType = "standard_16",
        defaultMarks = 1.0f,
        createdAt = System.currentTimeMillis() - 86400000L
      )
      quizDao.insertQuiz(quiz1)

      // Sample Scanned Paper 1 for Quiz 1: Rahul Kumar (12/16 = 75%)
      val rahulAnswers = JSONObject()
      for (i in 1..16) {
        if (i in listOf(3, 7, 11, 15)) {
          rahulAnswers.put(i.toString(), if (key1Options[i - 1] == "A") "B" else "A")
        } else {
          rahulAnswers.put(i.toString(), key1Options[i - 1])
        }
      }
      paperDao.insertPaper(
        ScannedPaperEntity(
          id = "paper-rahul-001",
          quizId = quiz1Id,
          studentId = "NG12345",
          studentName = "Rahul Kumar",
          answersJson = rahulAnswers.toString(),
          score = 12.0f,
          totalPossibleMarks = 16.0f,
          percentage = 75.0f,
          correctCount = 12,
          wrongCount = 4,
          blankCount = 0,
          multipleCount = 0,
          reviewRequiredCount = 0,
          whatsappNumber = "9876543210",
          block = "Sukma",
          cast = "General",
          gender = "Male",
          qualification = "12th Pass",
          questionSetName = "Set - A",
          scannedAt = System.currentTimeMillis() - 3600000L
        )
      )

      // Sample Scanned Paper 2: Priya Sharma (15/16 = 93.75%)
      val priyaAnswers = JSONObject()
      for (i in 1..16) {
        if (i == 6) {
          priyaAnswers.put(i.toString(), if (key1Options[i - 1] == "A") "B" else "A")
        } else {
          priyaAnswers.put(i.toString(), key1Options[i - 1])
        }
      }
      paperDao.insertPaper(
        ScannedPaperEntity(
          id = "paper-priya-002",
          quizId = quiz1Id,
          studentId = "NG20261",
          studentName = "Priya Sharma",
          answersJson = priyaAnswers.toString(),
          score = 15.0f,
          totalPossibleMarks = 16.0f,
          percentage = 93.75f,
          correctCount = 15,
          wrongCount = 1,
          blankCount = 0,
          multipleCount = 0,
          reviewRequiredCount = 0,
          whatsappNumber = "9340386750",
          block = "Beicha",
          cast = "OBC",
          gender = "Female",
          qualification = "B.A III",
          questionSetName = "Set - A",
          scannedAt = System.currentTimeMillis() - 1800000L
        )
      )

      // Sample Scanned Paper 3: Pooja Yadav (16/16 = 100%)
      val poojaAnswers = JSONObject()
      for (i in 1..16) {
        poojaAnswers.put(i.toString(), key1Options[i - 1])
      }
      paperDao.insertPaper(
        ScannedPaperEntity(
          id = "paper-pooja-003",
          quizId = quiz1Id,
          studentId = "NG56780",
          studentName = "Pooja Yadav",
          answersJson = poojaAnswers.toString(),
          score = 16.0f,
          totalPossibleMarks = 16.0f,
          percentage = 100.0f,
          correctCount = 16,
          wrongCount = 0,
          blankCount = 0,
          multipleCount = 0,
          reviewRequiredCount = 0,
          whatsappNumber = "9123456780",
          block = "Sukma",
          cast = "OBC",
          gender = "Female",
          qualification = "12th Pass",
          questionSetName = "Set - A",
          scannedAt = System.currentTimeMillis() - 1200000L
        )
      )

      // Sample Scanned Paper 4: Amit Netam (14/16 = 87.5%)
      val amitAnswers = JSONObject()
      for (i in 1..16) {
        if (i in listOf(4, 9)) {
          amitAnswers.put(i.toString(), if (key1Options[i - 1] == "A") "C" else "A")
        } else {
          amitAnswers.put(i.toString(), key1Options[i - 1])
        }
      }
      paperDao.insertPaper(
        ScannedPaperEntity(
          id = "paper-amit-004",
          quizId = quiz1Id,
          studentId = "NG55544",
          studentName = "Amit Netam",
          answersJson = amitAnswers.toString(),
          score = 14.0f,
          totalPossibleMarks = 16.0f,
          percentage = 87.5f,
          correctCount = 14,
          wrongCount = 2,
          blankCount = 0,
          multipleCount = 0,
          reviewRequiredCount = 0,
          whatsappNumber = "8877665544",
          block = "Konta",
          cast = "ST",
          gender = "Male",
          qualification = "10th Pass",
          questionSetName = "Set - A",
          scannedAt = System.currentTimeMillis() - 900000L
        )
      )

      // Sample Scanned Paper 5: Kavita Markam (13/16 = 81.25%)
      val kavitaAnswers = JSONObject()
      for (i in 1..16) {
        if (i in listOf(2, 8, 12)) {
          kavitaAnswers.put(i.toString(), if (key1Options[i - 1] == "B") "D" else "B")
        } else {
          kavitaAnswers.put(i.toString(), key1Options[i - 1])
        }
      }
      paperDao.insertPaper(
        ScannedPaperEntity(
          id = "paper-kavita-005",
          quizId = quiz1Id,
          studentId = "NG23456",
          studentName = "Kavita Markam",
          answersJson = kavitaAnswers.toString(),
          score = 13.0f,
          totalPossibleMarks = 16.0f,
          percentage = 81.25f,
          correctCount = 13,
          wrongCount = 3,
          blankCount = 0,
          multipleCount = 0,
          reviewRequiredCount = 0,
          whatsappNumber = "9826123456",
          block = "Dornapal",
          cast = "ST",
          gender = "Female",
          qualification = "B.Sc 1st Year",
          questionSetName = "Set - B",
          scannedAt = System.currentTimeMillis() - 600000L
        )
      )

      // Sample Scanned Paper 6: Suresh Kashyap (11/16 = 68.75%)
      val sureshAnswers = JSONObject()
      for (i in 1..16) {
        if (i in listOf(1, 5, 7, 13, 16)) {
          sureshAnswers.put(i.toString(), if (key1Options[i - 1] == "A") "B" else "A")
        } else {
          sureshAnswers.put(i.toString(), key1Options[i - 1])
        }
      }
      paperDao.insertPaper(
        ScannedPaperEntity(
          id = "paper-suresh-006",
          quizId = quiz1Id,
          studentId = "NG92837",
          studentName = "Suresh Kashyap",
          answersJson = sureshAnswers.toString(),
          score = 11.0f,
          totalPossibleMarks = 16.0f,
          percentage = 68.75f,
          correctCount = 11,
          wrongCount = 5,
          blankCount = 0,
          multipleCount = 0,
          reviewRequiredCount = 0,
          whatsappNumber = "7000192837",
          block = "Sukma",
          cast = "SC",
          gender = "Male",
          qualification = "Graduate",
          questionSetName = "Set - B",
          scannedAt = System.currentTimeMillis() - 300000L
        )
      )

      // Sample Quiz 2: "Jashpur SOB 2026" (16 Questions, using Seminar Key Set B)
      val quiz2Id = "jashpur-sob-2026"
      val quiz2 = QuizEntity(
        id = quiz2Id,
        name = "Jashpur SOB 2026",
        date = "15 August 2026",
        numQuestions = 16,
        answerKeyId = key2Id,
        templateType = "standard_16",
        defaultMarks = 1.0f,
        createdAt = System.currentTimeMillis() - 172800000L
      )
      quizDao.insertQuiz(quiz2)
    }
  }
}
