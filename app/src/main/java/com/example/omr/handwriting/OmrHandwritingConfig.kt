package com.example.omr.handwriting

/**
 * Model mode selection for handwriting recognition.
 */
enum class HandwritingModelMode {
  /** Production default: uses ML Kit OCR */
  OLD_MODEL,

  /** Controlled test mode: uses omr_letter_baseline.tflite & omr_digit_baseline.tflite */
  NEW_BASELINE_MODEL
}

/**
 * Audit metrics for an individual character cell.
 */
data class CellAudit(
  val fieldName: String,
  val boxIndex: Int,
  val rawInkCount: Int,
  val isEmpty: Boolean,
  val recognizedChar: String,
  val confidence: Float,
  val top3Candidates: List<Pair<String, Float>> = emptyList(),
  // Input verification telemetry
  val inputShape: List<Int> = listOf(1, 28, 28, 1),
  val minVal: Float = 0.0f,
  val maxVal: Float = 1.0f,
  val fgPixelRatio: Float = 0.0f,
  val bboxWidth: Int = 0,
  val bboxHeight: Int = 0,
  val comOffsetX: Float = 0.0f,
  val comOffsetY: Float = 0.0f,
  val borderContamination: Float = 0.0f
)

/**
 * Audit result for a full field.
 */
data class FieldAudit(
  val fieldName: String,
  val totalBoxes: Int,
  val recognizedText: String,
  val emptyBoxCount: Int,
  val filledBoxCount: Int,
  val avgConfidence: Float,
  val cells: List<CellAudit>
)

/**
 * Overall audit summary for a scan under Test Mode.
 */
data class HandwritingRunAudit(
  val modelMode: HandwritingModelMode,
  val letterModelName: String,
  val digitModelName: String,
  val firstNameAudit: FieldAudit?,
  val lastNameAudit: FieldAudit?,
  val phoneAudit: FieldAudit?,
  val whatsappAudit: FieldAudit?,
  val totalInferenceTimeMs: Float = 0.0f
)

/**
 * Global configuration and feature flag for handwriting recognition.
 */
object OmrHandwritingConfig {
  /**
   * Active model mode.
   * PRODUCTION DEFAULT: NEW_BASELINE_MODEL (validated TFLite letter & digit baseline with adaptive preprocessing).
   * Rollback path: change to OLD_MODEL to restore ML Kit without app restart or code redesign.
   */
  @Volatile
  var currentMode: HandwritingModelMode = HandwritingModelMode.NEW_BASELINE_MODEL

  /**
   * Whether to record detailed input verification telemetry and per-character top-3.
   */
  @Volatile
  var isDebugAuditEnabled: Boolean = false

  /**
   * Whether to export debug image crops and preprocessed cells to disk.
   */
  @Volatile
  var isDebugExportEnabled: Boolean = false

  /**
   * Confidence threshold for classifying recognition results as HIGH_CONFIDENCE.
   */
  const val HIGH_CONFIDENCE_THRESHOLD = 0.70f

  /**
   * Confidence threshold below which recognition results are LOW_CONFIDENCE.
   */
  const val LOW_CONFIDENCE_THRESHOLD = 0.40f

  /**
   * Last recorded audit run from a scan (null if audit not enabled).
   */
  @Volatile
  var lastRunAudit: HandwritingRunAudit? = null

  /**
   * Empty box threshold: minimum number of dark pixels required to classify a cell as non-empty.
   */
  const val EMPTY_BOX_INK_THRESHOLD = 6

  /**
   * Grayscale darkness threshold below which a pixel is considered ink.
   */
  const val INK_GRAY_THRESHOLD = 138

  /**
   * Preprocessing algorithm mode for NEW_BASELINE_MODEL.
   */
  enum class PreprocessingMode {
    FIXED_INSET,
    ADAPTIVE_RULING_SUPPRESSION
  }

  /**
   * Active preprocessing mode for NEW_BASELINE_MODEL test mode.
   * Defaults to ADAPTIVE_RULING_SUPPRESSION to prevent destructive clipping.
   */
  @Volatile
  var preprocessingMode: PreprocessingMode = PreprocessingMode.ADAPTIVE_RULING_SUPPRESSION

  /**
   * Adaptive ruling line continuity threshold (ratio of row width).
   */
  const val RULING_HORIZONTAL_RATIO = 0.50f

  /**
   * Adaptive outer vertical ruling continuity threshold (ratio of col height).
   */
  const val RULING_VERTICAL_RATIO = 0.70f

  /**
   * Horizontal inset (px) from each side of the box cell (used if FIXED_INSET mode is selected).
   */
  const val INSET_X = 4

  /**
   * Vertical inset (px) from top and bottom (used if FIXED_INSET mode is selected).
   */
  const val INSET_Y = 5
}
