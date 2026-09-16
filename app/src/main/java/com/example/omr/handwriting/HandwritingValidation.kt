package com.example.omr.handwriting

/**
 * Validates and cleans raw character outputs for student information fields.
 */
object HandwritingValidation {

  /**
   * Validates raw recognized text based on the field type.
   */
  fun validate(rawText: String, fieldType: BoxedFieldType): String {
    return when (fieldType) {
      BoxedFieldType.FIRST_NAME, BoxedFieldType.LAST_NAME -> validateName(rawText)
      BoxedFieldType.PHONE, BoxedFieldType.WHATSAPP -> validatePhone(rawText)
    }
  }

  /**
   * Names: allow only uppercase A–Z.
   * Strips spaces caused by skipped empty middle boxes (e.g. R A [empty] M E S H -> RAMESH).
   * Strips non-alphabetic characters.
   */
  fun validateName(rawText: String): String {
    val clean = rawText
      .uppercase()
      .replace(Regex("""[^A-Z]"""), "")
      .trim()
    return clean
  }

  /**
   * Phone / WhatsApp: allow only digits 0–9.
   * Never invents missing digits.
   * Preserves exact recognized sequence.
   */
  fun validatePhone(rawText: String): String {
    val digits = rawText.replace(Regex("""\D"""), "")
    return if (digits.length > 10) {
      digits.take(10)
    } else {
      digits
    }
  }

  /**
   * Cleans freehand text (City, School):
   * Strips unwanted artifacts, OCR vertical bars, tildes, underscores, backticks.
   */
  fun cleanFreeText(rawText: String): String {
    return rawText
      .replace(Regex("""[|_~`]+"""), " ")
      .replace(Regex("""\s+"""), " ")
      .trim()
  }
}
