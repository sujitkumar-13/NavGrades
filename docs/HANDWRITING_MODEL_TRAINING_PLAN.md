# Custom On-Device Handwriting Model Training Plan (TFLite / LiteRT)

## 1. Executive Summary

This document outlines the complete dataset architecture, collection methodology, model design, and training strategy for NavGrades OMR Checker's on-device handwriting recognition system.

The system strictly operates **100% offline** on mobile devices without any cloud Vision AI or external APIs.

The handwriting recognition problem is formally split into two fundamentally distinct tasks:
1. **Track A: Boxed Fields** (First Name, Last Name, Phone, WhatsApp)
   - **Nature:** Spatially constrained, discrete, single-character cells.
   - **Approach:** Deterministic geometric cell slicing + 28×28 character-level classification.
2. **Track B: Free-Hand Fields** (City / Block / Village, School / College)
   - **Nature:** Unconstrained continuous handwriting along an underline.
   - **Approach:** Line ROI extraction + ruling suppression + Sequence Recognition (CRNN with CTC Loss).

---

## 2. Audit of Existing Training Assets & Infrastructure

### 2.1 Existing Models (`app/src/main/assets/models/`)
* **`omr_letter_baseline.tflite`**
  - **Input:** `[1, 28, 28, 1]` Float32.
  - **Output:** `[1, 26]` Float32 (Classes: `A`–`Z`).
  - **Status/Limitation:** 100% accurate on uppercase letters, but **fails on lowercase letters** (e.g., maps Writer W02 lowercase `R a m e s h` to `W T T T T`).
* **`omr_digit_baseline.tflite`**
  - **Input:** `[1, 28, 28, 1]` Float32.
  - **Output:** `[1, 10]` Float32 (Classes: `0`–`9`).
  - **Status:** 100% accurate on phone and WhatsApp digits.
* **`emnist.tflite`** (in `androidTest/assets/models/`)
  - **Input:** `[1, 28, 28, 1]` Float32.
  - **Output:** `[1, 47]` Float32 (EMNIST Balanced: 10 digits + 26 uppercase + 11 merged lowercase).
* **`omr_poc.tflite`** (in `androidTest/assets/models/`)
  - **Input:** `[1, 28, 28, 1]` Float32.
  - **Output:** `[1, 36]` Float32 (10 digits + 26 uppercase).

### 2.2 Ingestion Scripts & Tooling
* **`scripts/ingest_real_omr_writer.py`**:
  - Production-aligned Python script mirroring Kotlin's `HandwritingPreprocessor`.
  - Performs adaptive ruling suppression, 8-connected component border noise filtering, center-of-mass centering into 28×28, and emits visual contact sheets.
  - Supports writer isolation (`dataset/real_omr/<WRITER_ID>/`).
* **`scratch/ocr_benchmark.py` & `scratch/evaluate_vision_benchmark.py`**:
  - Benchmark evaluation harnesses computing exact match, Levenshtein character accuracy, and per-cell telemetry.

### 2.3 Current Physical Dataset Status (`dataset/real_omr/`)
* **Registry:** `dataset/real_omr/metadata/writer_registry.json`.
* **W01 (Sujit Kumar):** Ingested (66 cells: 10 letters, 20 digits, 36 empty boxes).
* **W02 (Ramesh Singh):** Ingested (66 cells: 11 letters, 20 digits, 35 empty boxes, middle-box skip).
* **Available Physical Samples Pending Ingestion:**
  - W03 Candidate: `Nisha Painkra` (Uppercase, 10 digits, `Farsabahar`, `G.H.S.S. Kersai`).
  - W04 Candidate: `Khaja Baig` (Uppercase, 10 digits, `Karwan, Hyderabad, Telangana`, `Grace Model School`).
  - W01 Variant: `Sujit Kumar` (Uppercase, 10 digits, `Jashpur`, `Grace Model School`).

---

## 3. Dataset Architecture & Directory Structure

To prevent model overfitting, datasets are strictly **writer-separated**. No student/writer may appear in more than one split (Train, Validation, or Test).

```
dataset/
├── boxed_characters/                     # Track A: Isolated Character Cells
│   ├── metadata/
│   │   ├── classes.json                  # Class index mapping (0-61)
│   │   └── dataset_manifest.json         # Master manifest of all samples & splits
│   ├── train/                            # Writers W01 - W10 + EMNIST/Synthetic base
│   │   ├── digits/                       # 0-9 subfolders
│   │   │   ├── 0/ [writer_id]_[field]_[box]_28x28.png
│   │   │   └── ...
│   │   ├── uppercase/                    # A-Z subfolders
│   │   │   ├── A/
│   │   │   └── ...
│   │   └── lowercase/                    # a-z subfolders
│   │       ├── a/
│   │       └── ...
│   ├── val/                              # Writers W11 - W12
│   │   ├── digits/
│   │   ├── uppercase/
│   │   └── lowercase/
│   └── test/                             # Writers W13 - W15 (Strict Blind Holdout)
│       ├── digits/
│       ├── uppercase/
│       └── lowercase/
│
├── freehand_lines/                       # Track B: Unconstrained Word & Line Crops
│   ├── metadata/
│   │   ├── vocabulary.txt                # Common words (cities, states, school terms)
│   │   ├── train_transcriptions.tsv      # image_path \t transcript \t writer_id
│   │   ├── val_transcriptions.tsv
│   │   └── test_transcriptions.tsv
│   ├── train/                            # Crops from Writers W01 - W10 + Synthetic
│   │   ├── city/
│   │   │   └── w01_city_crop.png
│   │   └── school/
│   │       └── w01_school_crop.png
│   ├── val/                              # Crops from Writers W11 - W12
│   │   ├── city/
│   │   └── school/
│   └── test/                             # Crops from Writers W13 - W15
│       ├── city/
│       └── school/
│
└── real_omr_sheets/                      # Raw physical scans & master coordinates
    ├── metadata/
    │   └── writer_registry.json
    ├── W01/ ... W15/
    │   ├── original/                     # 682x1024 rectified sheet photo
    │   ├── crops/                        # Raw un-preprocessed field crops
    │   └── metadata.json                 # Ground truth transcriptions
```

---

## 4. Track A: Boxed Character Classification Model

### 4.1 Problem Definition
* Input is a single character cell extracted from the calibrated box grids:
  - First Name: 23 cells
  - Last Name: 23 cells
  - Phone: 10 cells
  - WhatsApp: 10 cells
* An energy/ink gating check (`EMPTY_BOX_INK_THRESHOLD = 8`, `CORE_INK_THRESHOLD = 125`) discards blank boxes prior to model inference.

### 4.2 Expanded Class Taxonomy (62 Classes)
To solve the Writer W02 lowercase failure while maintaining digit precision:
* **Classes 0–9:** Digits `0` through `9` (10 classes).
* **Classes 10–35:** Uppercase letters `A` through `Z` (26 classes).
* **Classes 36–61:** Lowercase letters `a` through `z` (26 classes).
*(Post-inference logic maps lowercase letters to uppercase for name validation: e.g., `'a'` $\rightarrow$ `'A'`)*.

### 4.3 Input & Normalization Pipeline
* **Input Tensor:** `[1, 28, 28, 1]` Float32, native order.
* **Pixel Intensity:** $[0.0, 1.0]$, where $0.0$ is background (white paper mapped to black background like MNIST/EMNIST) and $1.0$ is dark ink stroke.
* **Geometry:** Bounding box scaled to fit within $20 \times 20$ pixels, centered by center-of-mass at coordinate $(13.5, 13.5)$.
* **Border Ruling Cleaning:** Outer ruling line suppression and 8-connected component filtering to remove box border artifacts.

### 4.4 Data Requirements & Composition
* **Base Pre-training Corpus:**
  - EMNIST ByClass (814,255 samples across 62 classes) or EMNIST Balanced (47 classes with case merging).
  - MNIST Handwritten Digits (70,000 samples).
* **Physical OMR Domain Adaptation:**
  - Target: 15–20 physical student writers.
  - Samples per character class: Minimum 100 samples per alphanumeric character.
  - Digits: Minimum 200 real physical samples (10 writers $\times$ 20 phone digits).
* **Augmentation Strategy:**
  - Random rotation ($\pm 10^\circ$).
  - Random stroke thickness (morphological dilation / erosion).
  - Random shear ($\pm 8^\circ$).
  - Random box border noise injection (faint vertical/horizontal ruling line fragments at margin).

### 4.5 Target Model Architecture
* **Backbone:** Compact Depthwise-Separable Convolutional Network (MobileNet-style or LeNet-5 enhanced with Residual bottlenecks).
* **Layers:**
  1. Conv2D $3 \times 3$, 32 filters, ReLU, BatchNorm.
  2. DepthwiseConv2D $3 \times 3$ + PointwiseConv2D 64 filters, ReLU, MaxPool $2 \times 2$.
  3. DepthwiseConv2D $3 \times 3$ + PointwiseConv2D 128 filters, ReLU, MaxPool $2 \times 2$.
  4. GlobalAveragePooling2D + Dropout(0.3).
  5. Dense(62) Softmax.
* **Target Size:** $< 1.2$ MB (FP16/INT8 quantized).
* **Inference Latency:** $< 2.0$ ms per cell on mobile CPU.

---

## 5. Track B: Free-Hand Word & Line Recognition Model

### 5.1 Problem Definition
* Continuous unconstrained handwriting written on an underline in the `City / Block / Village` and `School / College` fields.
* **Do NOT force a 28×28 character classifier to recognize continuous words.** Word-level handwriting requires sequence modeling with Connectionist Temporal Classification (CTC) to transcribe variable-length letter sequences without character segmentation.

### 5.2 Input & Normalization Pipeline
* **Input Tensor:** Fixed-height, variable-width or padded tensor: `[1, 64, 512, 1]` Float32.
* **Height:** 64 px (standard height for line OCR models).
* **Width:** 512 px (aspect-ratio preserved bilinear resize with right-padding).
* **Preprocessing:**
  - Extracted via calibrated `CITY_HANDWRITING_REGION` / `SCHOOL_HANDWRITING_REGION` (excluding printed labels $x < 0.278f$).
  - Underline suppression in the bottom 25% of the strip.
  - Foreground stroke normalization.

### 5.3 Target Vocabulary & Domain Lexicon
The model recognizes standard English Latin alphanumeric characters, spaces, and punctuation:
* `Alphabet:` `a-z`, `A-Z`, `0-9`, `,`, `.`, `-`, `/`, `&`, `(`, `)`, `' '`.
* **Domain Lexicon:**
  - States & Districts: *Jashpur, Raigarh, Bilaspur, Raipur, Delhi, Noida, Hyderabad, Telangana, Uttar Pradesh, etc.*
  - School Terms: *School, College, Vidya, Child, Public, Model, National, Institute, Open, NIOS, Govt, GHSS, HSS, etc.*

### 5.4 Data Requirements & Composition
* **Physical OMR Samples:**
  - Minimum 15–20 physical sheets (providing 30–40 authentic field crops).
* **Synthetic Line Generator:**
  - Because 30 physical lines are insufficient to train a sequence model from scratch, a synthetic line generator will generate 25,000 realistic synthetic lines using:
    1. Text dictionary derived from Indian cities, blocks, schools, and typical student entries.
    2. Over 80 diverse open-source cursive/print handwriting TTF fonts.
    3. Blended blue/black ink color variations.
    4. Superimposed realistic OMR underline baselines and paper grain noise.
* **Fine-Tuning:**
  - Pre-train CRNN on 25,000 synthetic lines.
  - Fine-tune on real physical OMR crops from training writers (W01–W10).
  - Evaluate on blind holdout writers (W13–W15).

### 5.5 Target Model Architecture: CRNN-CTC
* **Architecture Stages:**
  1. **Feature Extraction (CNN):** 4-stage residual or depthwise convolutional feature extractor downsampling height $64 \rightarrow 1$ and width $512 \rightarrow 128$ feature frames.
  2. **Sequence Modeling (RNN):** 2-layer Bidirectional GRU (128 hidden units per direction) capturing contextual letter transitions and cursive ligatures.
  3. **Transcription (CTC):** Linear projection to vocabulary size $+ 1$ (blank token) trained with Connectionist Temporal Classification (CTC) loss.
* **Decoding:** CTC Greedy Decoder or Prefix Beam Search with optional domain language model/lexicon scoring.
* **Target Size:** $< 3.5$ MB (quantized LiteRT FlatBuffer).
* **Inference Latency:** $< 40$ ms per line on mobile CPU.

---

## 6. Training & Validation Protocols

### 6.1 Writer-Separated Split Matrix

| Split | Writers | Purpose | Expected Physical Sheets |
| :--- | :--- | :--- | :--- |
| **Train** | `W01` through `W10` | Feature learning, weights optimization | 10–12 sheets ($\sim 660$ character cells, 20 line crops) |
| **Validation** | `W11` through `W12` | Hyperparameter tuning, early stopping | 2 sheets ($\sim 132$ character cells, 4 line crops) |
| **Test (Holdout)**| `W13` through `W15` | Final blind evaluation & accuracy reporting | 3 sheets ($\sim 198$ character cells, 6 line crops) |

### 6.2 Target Quality & Evaluation Metrics
* **Track A (Boxed Characters):**
  - Metric: **Top-1 Character Accuracy** ($\ge 98.0\%$ on clean cells, $\ge 95.0\%$ on noisy/clipped cells).
  - Metric: **Empty Box Precision** ($\ge 99.5\%$ — must never hallucinate characters in empty boxes).
* **Track B (Free-Hand Words):**
  - Metric: **Character Error Rate (CER)** $\le 5.0\%$.
  - Metric: **Word Error Rate (WER)** $\le 12.0\%$.
  - Metric: **Key Entity Match** (City/State exact match $\ge 90\%$).

---

## 7. Android Deployment & Integration Architecture

Once trained and validated, the models will be deployed directly to Android assets:
* `app/src/main/assets/models/omr_char_62class.tflite` (Replaces 26-class letter model; recognizes both uppercase and lowercase letters).
* `app/src/main/assets/models/omr_line_crnn.tflite` (Powers Track B unconstrained freehand recognition).

```
Rectified 682x1024 Sheet
  │
  ├── Boxed Fields ROI ──→ Box Slicing ──→ preprocessCell() ──→ omr_char_62class.tflite ──→ Character Validation
  │
  └── Freehand Fields ROI ─→ preprocessLine() ──────────────→ omr_line_crnn.tflite ──────→ CTC Greedy Decode ──→ Text Validation
```

* Zero network calls.
* Pure local inference using Google Play Services LiteRT / TensorFlow Lite 2.16 runtime.
