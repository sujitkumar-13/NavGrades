# Training Pipeline Status Report

**Report Generated:** September 19, 2026  
**Pipeline Phase:** Base Dataset Preparation + Training Pipeline Setup  
**Execution Mode:** Baseline & Infrastructure Readiness (No Final Model Training)

---

## 1. Track A Base Dataset Status (EMNIST ByClass)

- **Source Corpus:** Official NIST EMNIST ByClass archive (`https://biometrics.nist.gov/cs_links/EMNIST/gzip.zip`).
- **Archive Status:** Successfully downloaded and verified at `~/.cache/emnist/emnist.zip` (535.7 MB).
- **Taxonomy Alignment:** Verified 1:1 mapping with `dataset/boxed_characters/metadata/classes.json` across all 62 classes.
- **Orientation & Coordinate Alignment:** The raw NIST transposed convention (`img.T`) has been rectified to standard upright orientation.
- **Preprocessing Parity with Production:** Images are formatted as `[1, 28, 28, 1]` Float32 in range `[0.0, 1.0]` with black background (`0.0`) and normalized white stroke ink (`1.0`), matching Android `HandwritingPreprocessor.preprocessCell()` center-of-mass alignment.
- **Verification Result:** 1,000-sample verification batch loaded cleanly; all 62 alphanumeric classes confirmed active.

---

## 2. Track A Real OMR Dataset Status

- **Ingested Physical Sheets:** 5 physical writers (W01, W02, W03, W04, W05).
- **Total Character Boxes Processed:** 330 boxes.
  - Filled character crops: 133 samples.
  - Empty boxes: 197 samples.
- **Split Assignment:** 100% assigned to `TRAIN`.
  - Digits (`0–9`): 80 samples across all 10 digit classes.
  - Uppercase letters (`A–Z`): 33 samples across 14 classes.
  - Lowercase letters (`a–z`): 20 samples across 9 classes.
- **File & Manifest Integrity:** Verified 100% on disk via `scripts/validate_dataset_integrity.py`.

---

## 3. Track B Synthetic Generator Status

- **Generator Script:** `scripts/generate_synthetic_handwriting_lines.py`.
- **Lexicons Supported:** 
  - Indian Cities/Districts/States (e.g., *Jashpur, Noida, Farsabahar, Karwan, Hyderabad, Patna, Bihar*).
  - Indian Schools/Colleges (e.g., *Grace Model School, Vidya & Child, National Institute Of Open Schooling ( NIOS ), G.H.S.S Kersai, Yash International Public School*).
- **Handwriting Simulation Features:**
  - Font selection across 9 installed system fonts (Liberation, DejaVu, FreeSans).
  - Procedural slant and shear ($\pm 14^\circ$).
  - Character kerning jitter and baseline wobble.
  - Faint ruling line / underline simulation ($\sim 140\text{--}175$ gray level) matching OMR answer sheets.
  - Ink stroke intensity variation and paper noise.
- **Small Validation Batch:**
  - Generated **30 validation samples** into `dataset/synthetic_lines_val/images/` (15 City, 15 School).
  - Paired transcriptions recorded in `dataset/synthetic_lines_val/manifest.tsv`.
  - Dimensions: $384 \times 32$ pixels ($W=384, H=32$), Grayscale.
  - Character vocabulary coverage: 50 unique characters in the 30-sample validation set.
- **Storage Constraint Adherence:** No large dataset committed (strict avoidance of 25,000 samples).

---

## 4. Track B Real Dataset Status

- **Source:** High-resolution 4-corner perspective-rectified $682 \times 1024$ sheets from physical writers W01–W05.
- **Available Line Samples:** 10 continuous unsegmented crops (5 City fields, 5 School fields).
- **Location:** `dataset/freehand_lines/train/city/` and `dataset/freehand_lines/train/school/`.
- **Transcriptions:** Verbatim physical ground truth cataloged in `dataset/freehand_lines/metadata/train_transcriptions.tsv`:
  - W01: City `Jashpur`, School `Grace Model School`
  - W02: City `Noida`, School `National Institute Of Open Schooling ( NIOS )`
  - W03: City `Farsabahar`, School `G.H.S.S Kersai`
  - W04: City `Karwan, Hyderabad, Telangana`, School `Grace Model School`
  - W05: City `Patna, Bihar`, School `Yash International Public School`
- **Role in Pipeline:** Reserved as real OMR validation/fine-tuning anchors. Not used for training from scratch.

---

## 5. Current Writer Distribution

| Partition | Writer IDs | Status | Number of Writers | Policy / Constraint |
|:---|:---|:---|:---:|:---|
| **TRAIN** | **W01, W02, W03, W04, W05** | Ingested & Active | 5 (Target: 10) | Existing real writers; future W06–W10 will join TRAIN |
| **TRAIN (Pending)** | **W06, W07, W08, W09, W10** | Awaiting Ingestion | 0 (Planned: 5) | Future physical writer collections |
| **VALIDATION** | **W11, W12** | Reserved (Unpolluted) | 0 (Planned: 2) | Strictly zero training exposure |
| **TEST** | **W13, W14, W15** | Reserved (Blind) | 0 (Planned: 3) | Strictly held-out blind evaluation |

*Rule: Zero cross-split writer mixing. W01–W05 remain strictly in TRAIN.*

---

## 6. Missing Classes in Real Character Corpus (Track A)

While all 62 classes are fully supported by the EMNIST base corpus, the real OMR writer dataset currently has the following coverage:

- **Digits (0/10 missing):** All 10 digits (`0, 1, 2, 3, 4, 5, 6, 7, 8, 9`) are present.
- **Missing Uppercase Letters (12/26 missing):**  
  `C`, `D`, `E`, `F`, `L`, `O`, `Q`, `V`, `W`, `X`, `Y`, `Z`
- **Missing Lowercase Letters (17/26 missing):**  
  `b`, `c`, `d`, `f`, `j`, `k`, `l`, `o`, `p`, `q`, `t`, `u`, `v`, `w`, `x`, `y`, `z`
- **Mitigation Strategy:** EMNIST ByClass provides base representation for all 62 classes; fine-tuning focuses on real pen strokes and OMR paper textures. Future writers W06–W10 are planned to target missing classes.

---

## 7. Training Prerequisites

Before launching the first production training run:
1. **Base Pre-training Engine:** Tested and ready (`prepare_emnist_base_dataset.py`).
2. **Model Definition & Loss Pipeline:** Tested and ready (`train_boxed_character_model.py` and `train_freehand_crnn.py`).
3. **Synthetic Line Generator:** Tested and ready (`generate_synthetic_handwriting_lines.py`).
4. **Hardware Environment:** TensorFlow 2.21 CPU execution verified; GPU acceleration recommended for final convergence.
5. **Production Invariance:** Existing Android APK and production models (`omr_digit_baseline.tflite`, `omr_letter_baseline.tflite`) remain intact.

---

## 8. What is Ready

- **Track A Base Data:** EMNIST ByClass (535.7 MB) verified and cached.
- **Track A Model Architecture:** 62-class Depthwise-Separable CNN (54,846 params, 214 KB TFLite export verified via dry-run).
- **Track B Synthetic Generator:** Complete procedural rendering pipeline producing 384x32 images with ruling lines, slants, and noise.
- **Track B CRNN Architecture:** 4-stage CNN + 2-layer BiGRU + CTC head (770,663 params) with greedy/beam decoding verified via dry-run.
- **Real Datasets:** W01–W05 cataloged, writer-partitioned, and verified (100% GREEN integrity audit).
- **Non-Regression:** Gradle unit tests pass cleanly (`33 up-to-date, 0 failures`).

---

## 9. What is Still Missing

- **Real Writers W06–W10:** Required to fill in the 12 missing uppercase and 17 missing lowercase character classes in real OMR sheets.
- **Validation / Test Writers (W11–W15):** Physical collection needed for clean held-out validation and blind test benchmarks.
- **Full Synthetic Corpus Generation (Track B):** Full 25,000-sample generation is intentionally deferred until training execution approval.
- **Quantization Optimization:** Post-training INT8 / Float16 quantization for Android LiteRT.

---

## 10. Exact Next Step Before Final Model Training

The exact next steps before training the final production models are:
1. **Controlled Experiment 1 (Track A Base Pre-training):**
   - Run initial pre-training of the 62-class CNN on EMNIST ByClass (10–15 epochs) to establish a base checkpoint (`base_emnist_62class.keras`).
   - Evaluate baseline top-1 and top-3 accuracy on the EMNIST test split.
2. **Controlled Experiment 2 (Track B Synthetic Pre-training):**
   - Generate a medium training batch (e.g., 5,000 synthetic samples) and train the CRNN backbone with CTC loss.
   - Verify character error rate (CER) on the synthetic validation batch.
3. **Real Writer Collection:**
   - Ingest physical sheets for W06–W10 into TRAIN to complete alphabet coverage.
