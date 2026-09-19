# Offline Handwriting Dataset Status Report

**Last Updated:** 2026-09-19  
**Status:** Ingested Writers W01–W05 (Real Physical OMR Sheets)  
**Dataset Architecture:** Strict Writer Separation (Holdout Partitioning)

---

## 1. Executive Summary

As required by [HANDWRITING_MODEL_TRAINING_PLAN.md](file:///home/sujit/OMR-Checker/docs/HANDWRITING_MODEL_TRAINING_PLAN.md), the real handwriting dataset ingestion phase was executed across all available physical OMR sheets (**W01 through W05**).

* **Model Training Status:** **FROZEN** (No models trained; zero synthetic scaling at scale).
* **Production OMR Status:** **UNMODIFIED** (100% offline bubble detection, fiducial alignment, and scan engines intact).
* **Dataset Quality:** Verified via automated suite [`scripts/validate_dataset_integrity.py`](file:///home/sujit/OMR-Checker/scripts/validate_dataset_integrity.py) (100% GREEN).

---

## 2. Writer Partitioning & Status

| Writer ID | Author Name | Status | Split Assignment | Ink Type | Sheet Resolution | Total Cells | Freehand Fields |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **W01** | Sujit Kumar | **AVAILABLE** | **TRAIN** | Blue Ballpoint | 682×1024 (Rectified) | 66 (10 letters, 10 digits, 46 empty) | City: `Jashpur`<br>School: `Grace Model School` |
| **W02** | Ramesh Singh | **AVAILABLE** | **TRAIN** | Blue Ballpoint | 682×1024 (Rectified) | 66 (11 letters, 20 digits, 35 empty) | City: `Noida`<br>School: `National Institute Of Open Schooling ( NIOS )` |
| **W03** | Nisha Painkra | **AVAILABLE** | **TRAIN** | Light Ballpoint | 682×1024 (Rectified) | 66 (12 letters, 20 digits, 34 empty) | City: `Farsabahar`<br>School: `G.H.S.S Kersai` |
| **W04** | Khaja Baig | **AVAILABLE** | **TRAIN** | Blue Ballpoint | 682×1024 (Rectified) | 66 (9 letters, 10 digits, 47 empty) | City: `Karwan, Hyderabad, Telangana`<br>School: `Grace Model School` |
| **W05** | Sameer Singh | **AVAILABLE** | **TRAIN** | Blue Ballpoint | 682×1024 (Rectified) | 66 (11 letters, 20 digits, 35 empty) | City: `Patna, Bihar`<br>School: `Yash International Public School` |
| **W06–W10** | *Pending* | **PENDING** | Future Train | — | — | 0 | — |
| **W11–W12** | *Pending* | **PENDING** | **VALIDATION (Reserved)** | — | — | 0 | Reserved for Val split |
| **W13–W15** | *Pending* | **PENDING** | **TEST (Strict Holdout)** | — | — | 0 | Reserved for Test split |

### Split Distribution Summary:
* **Total Planned Writers:** 15
* **Ingested Writers:** 5 (W01, W02, W03, W04, W05)
* **Pending Writers:** 10 (W06–W15)
* **Train Split Writers:** 5 ingested / 10 planned
* **Validation Split Writers:** 0 ingested / 2 planned (Strictly preserved, zero leakage)
* **Test Split Writers:** 0 ingested / 3 planned (Strictly preserved, zero leakage)

---

## 3. Track A: Boxed Character Dataset Metrics (62 Classes)

Targeting isolated $28 \times 28$ center-of-mass normalized character classification for **First Name**, **Last Name**, **Phone**, and **WhatsApp**.

* **Total Extracted Cells:** 330
* **Filled Character Samples:** 133
* **Empty Box Samples:** 197
* **Overall Character Clipping Rate:** 18.05% (24 / 133)

### 3.1 Class Sample Distribution:

#### Digits (0–9) — 80 Real Physical Samples (100% Class Coverage)
| Class | Sample Count | Sources |
| :---: | :---: | :--- |
| `0` | 5 | W02, W04, W05 |
| `1` | 9 | W01, W02, W03, W04, W05 |
| `2` | 8 | W01, W02, W03, W05 |
| `3` | 19 | W01, W02, W03, W04, W05 |
| `4` | 5 | W01, W02, W04, W05 |
| `5` | 4 | W01, W02, W05 |
| `6` | 7 | W02, W03, W04, W05 |
| `7` | 4 | W01, W02, W04, W05 |
| `8` | 7 | W02, W03, W04, W05 |
| `9` | 12 | W01, W02, W03, W04, W05 |
| **Total Digits** | **80** | **All 10 digit classes represented** |

#### Uppercase Letters (A–Z) — 33 Real Physical Samples (14 Classes Present, 12 Missing)
| Class | Count | Class | Count | Class | Count | Class | Count |
| :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: |
| `A` | 7 | `H` | 2 | `N` | 2 | `T` | 1 |
| `B` | 1 | `I` | 4 | `P` | 1 | `U` | 2 |
| `G` | 1 | `J` | 2 | `R` | 3 | — | — |
| — | — | `K` | 3 | `S` | 3 | — | — |
| — | — | `M` | 1 | — | — | — | — |

* **Missing Uppercase Classes (12):** `C`, `D`, `E`, `F`, `L`, `O`, `Q`, `V`, `W`, `X`, `Y`, `Z`

#### Lowercase Letters (a–z) — 20 Real Physical Samples (9 Classes Present, 17 Missing)
| Class | Count | Class | Count | Class | Count |
| :---: | :---: | :---: | :---: | :---: | :---: |
| `a` | 2 | `h` | 3 | `n` | 2 |
| `e` | 3 | `i` | 2 | `r` | 1 |
| `g` | 2 | `m` | 2 | `s` | 3 |

* **Missing Lowercase Classes (17):** `b`, `c`, `d`, `f`, `j`, `k`, `l`, `o`, `p`, `q`, `t`, `u`, `v`, `w`, `x`, `y`, `z`

---

## 4. Track B: Free-Hand Continuous Lines Dataset Metrics

Targeting unconstrained line OCR for **City / Block / Village** and **School / College**. Stored as complete, unsegmented horizontal crops with verbatim ground-truth transcriptions.

* **Total Line Samples:** 10 (5 City lines, 5 School lines)
* **Vocabulary Size:** 23 unique tokens

### 4.1 Field Breakdown:
| Writer ID | Field Type | Exact Ground-Truth Transcription | Ruling Line Status | Printed Label Excluded |
| :--- | :--- | :--- | :---: | :---: |
| **W01** | CITY | `Jashpur` | Base ruling preserved | Yes |
| **W01** | SCHOOL | `Grace Model School` | Base ruling preserved | Yes |
| **W02** | CITY | `Noida` | Base ruling preserved | Yes |
| **W02** | SCHOOL | `National Institute Of Open Schooling ( NIOS )` | Base ruling preserved | Yes |
| **W03** | CITY | `Farsabahar` | Base ruling preserved | Yes |
| **W03** | SCHOOL | `G.H.S.S Kersai` | Base ruling preserved | Yes |
| **W04** | CITY | `Karwan, Hyderabad, Telangana` | Base ruling preserved | Yes |
| **W04** | SCHOOL | `Grace Model School` | Base ruling preserved | Yes |
| **W05** | CITY | `Patna, Bihar` | Base ruling preserved | Yes |
| **W05** | SCHOOL | `Yash International Public School` | Base ruling preserved | Yes |

---

## 5. Dataset Quality & Verification Audit

The dataset integrity validation script [`scripts/validate_dataset_integrity.py`](file:///home/sujit/OMR-Checker/scripts/validate_dataset_integrity.py) executed and passed all quality gates:

1. **Physical File Verification:** 100% of the 330 Track A raw crops and $28 \times 28$ normalized tensors, plus all 10 Track B line crops, exist on disk and are non-empty.
2. **62-Class Mapping:** Validated `dataset/boxed_characters/metadata/classes.json` containing classes 0–9 (digits), 10–35 (A–Z), and 36–61 (a–z).
3. **No Cross-Split Contamination:** W01–W05 are assigned strictly to the **TRAIN** partition. Validation and Test splits contain zero samples, preserving holdout integrity.
4. **Printed Label Exclusion:** Verified that `CITY_HANDWRITING_REGION` and `SCHOOL_HANDWRITING_REGION` start at $X=190$ ($0.278592 \times 682$), completely eliminating printed prompt labels (`City/Block/Village :`, `School / College :`).
5. **Bubble Artifact Exclusion:** Verified vertical boundaries: City crop ($Y=288..326$) sits cleanly between WhatsApp ($Y \le 285$) and Caste circles ($Y \ge 340$). School crop ($Y=442..477$) sits cleanly between Qualification circles ($Y \le 432$) and Set bubbles ($Y \ge 513$).
6. **Ruling Lines:** Baseline ruling lines are consistently located at the lower margin across all crops.
7. **Transcription Accuracy:** Verified 1:1 match with physical handwriting:
   - W02 School ground truth explicitly matches `National Institute Of Open Schooling ( NIOS )`.
   - Character case is preserved (`s,a,m,e,e,r` lowercase in W05 vs. `N,I,S,H,A` uppercase in W03).

---

## 6. Training Readiness Verdict

> [!WARNING]
> **NOT READY FOR SOLELY REAL-DATA MODEL TRAINING**
>
> While the dataset is clean, correctly partitioned, and schema-compliant, the real dataset size is currently insufficient for standalone deep neural network training:
> 1. **Zero Validation and Test Writers:** Zero real writers exist in the validation (W11–W12) and test (W13–W15) partitions. Training now would prevent evaluating generalization on unseen real writers.
> 2. **Missing Character Classes:** 12 uppercase classes and 17 lowercase classes currently have **0 real physical samples**.
> 3. **Underrepresented Free-Hand Lines:** Only 10 total line samples exist across 5 writers (vocabulary of 23 words). CTC-based sequence models require hundreds to thousands of sequence pairs.

### Recommended Next Actions (Before Model Training):
1. Ingest additional physical sheets for writers **W06–W10** (Train), **W11–W12** (Validation), and **W13–W15** (Test).
2. Prepare base pre-training corpora (EMNIST ByClass / MNIST for Track A; synthetic line generation for Track B) so domain fine-tuning can proceed once physical writer splits are sufficiently populated.
