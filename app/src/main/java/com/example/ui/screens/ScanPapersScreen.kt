package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.data.model.ScannedPaperEntity
import com.example.omr.CornerAlignmentState
import com.example.omr.OmrScannerEngine
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.viewmodel.OmrViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanPapersScreen(
  quizId: String,
  viewModel: OmrViewModel,
  onNavigateBack: () -> Unit,
  onNavigateToPaperDetail: (String) -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val quiz by viewModel.selectedQuiz.collectAsState()
  val isScanning by viewModel.isScanning.collectAsState()
  val lastPaper by viewModel.lastScannedPaper.collectAsState()
  val scanError by viewModel.scanError.collectAsState()

  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    hasCameraPermission = isGranted
  }

  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri: Uri? ->
    uri?.let {
      viewModel.processImageFromUri(it) {
        // Handled via lastScannedPaper
      }
    }
  }

  var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
  var showDemoFeederDialog by remember { mutableStateOf(false) }
  var cornerAlignment by remember { mutableStateOf(CornerAlignmentState()) }
  var lastCaptureTimestamp by remember { mutableStateOf(0L) }
  var lastAnalysisTimestamp by remember { mutableStateOf(0L) }

  val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
  DisposableEffect(Unit) {
    onDispose {
      cameraExecutor.shutdown()
    }
  }

  LaunchedEffect(quizId) {
    viewModel.loadQuiz(quizId)
    viewModel.clearScanResult()
    cornerAlignment = CornerAlignmentState()
    if (!hasCameraPermission) {
      permissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            text = "SCANNING",
            style = MaterialTheme.typography.titleLarge.copy(
              letterSpacing = 1.5.sp,
              fontWeight = FontWeight.Bold
            ),
            color = Color.White
          )
        },
        navigationIcon = {
          IconButton(
            onClick = {
              viewModel.clearScanResult()
              onNavigateBack()
            },
            modifier = Modifier.testTag("back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
              tint = Color.White,
              modifier = Modifier.size(26.dp)
            )
          }
        },
        actions = {
          // Gallery Image icon on the top right
          IconButton(
            onClick = { galleryLauncher.launch("image/*") },
            modifier = Modifier.testTag("gallery_import_button")
          ) {
            Icon(
              imageVector = Icons.Default.PhotoLibrary,
              contentDescription = "Import Image",
              tint = Color.White,
              modifier = Modifier.size(24.dp)
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = Color(0xFF1E6827)
        )
      )
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      if (hasCameraPermission) {
        // Camera Viewfinder with Overlay
        Box(modifier = Modifier.fillMaxSize()) {
          AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
              val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                  ViewGroup.LayoutParams.MATCH_PARENT,
                  ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
              }

              val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
              cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                  it.surfaceProvider = previewView.surfaceProvider
                }

                val capture = ImageCapture.Builder()
                  .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                  .build()
                imageCapture = capture

                val imageAnalysis = ImageAnalysis.Builder()
                  .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                  .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                  try {
                    val now = System.currentTimeMillis()
                    if (!isScanning && lastPaper == null && (now - lastAnalysisTimestamp > 160L)) {
                      lastAnalysisTimestamp = now
                      val bmp = imageProxy.toBitmap()
                      if (bmp != null) {
                        val alignment = OmrScannerEngine.detectCornerAlignment(bmp)
                        cornerAlignment = alignment

                        if (alignment.allAligned && !isScanning && lastPaper == null && (now - lastCaptureTimestamp > 2400L)) {
                          lastCaptureTimestamp = now
                          viewModel.processScannedBitmap(bmp) {}
                        }
                      }
                    }
                  } catch (e: Exception) {
                    e.printStackTrace()
                  } finally {
                    imageProxy.close()
                  }
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                  cameraProvider.unbindAll()
                  cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    capture,
                    imageAnalysis
                  )
                } catch (e: Exception) {
                  e.printStackTrace()
                }
              }, ContextCompat.getMainExecutor(ctx))

              previewView
            }
          )

          // Viewfinder Cutout Overlay & 4 Corner Alignment Target Boxes
          Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            val sheetW = canvasW * 0.88f
            val sheetH = sheetW * 1.414f // A4 proportion
            val finalSheetH = if (sheetH > canvasH * 0.76f) canvasH * 0.76f else sheetH
            val finalSheetW = finalSheetH / 1.414f

            val left = (canvasW - finalSheetW) / 2f
            val top = (canvasH - finalSheetH) / 2.6f
            val right = left + finalSheetW
            val bottom = top + finalSheetH

            // 1. Semi-transparent scrim outside sheet area
            val scrimColor = Color(0x55000000)
            // Top scrim
            drawRect(color = scrimColor, topLeft = Offset(0f, 0f), size = Size(canvasW, top))
            // Bottom scrim
            drawRect(color = scrimColor, topLeft = Offset(0f, bottom), size = Size(canvasW, canvasH - bottom))
            // Left scrim
            drawRect(color = scrimColor, topLeft = Offset(0f, top), size = Size(left, finalSheetH))
            // Right scrim
            drawRect(color = scrimColor, topLeft = Offset(right, top), size = Size(canvasW - right, finalSheetH))

            // 2. 4 Corner Viewfinder shaded target boxes
            val cornerBoxW = finalSheetW * 0.20f
            val cornerBoxH = finalSheetW * 0.20f
            val cornerShade = Color(0x33FFFFFF) // Lighter shaded viewfinder region

            // Top-Left Viewfinder Box
            drawRect(color = cornerShade, topLeft = Offset(left, top), size = Size(cornerBoxW, cornerBoxH))
            // Top-Right Viewfinder Box
            drawRect(color = cornerShade, topLeft = Offset(right - cornerBoxW, top), size = Size(cornerBoxW, cornerBoxH))
            // Bottom-Left Viewfinder Box
            drawRect(color = cornerShade, topLeft = Offset(left, bottom - cornerBoxH), size = Size(cornerBoxW, cornerBoxH))
            // Bottom-Right Viewfinder Box
            drawRect(color = cornerShade, topLeft = Offset(right - cornerBoxW, bottom - cornerBoxH), size = Size(cornerBoxW, cornerBoxH))

            // 3. Highlighted Green Boxes on each individually aligned corner
            val cornerBoxSize = finalSheetW * 0.085f
            val halfBox = cornerBoxSize / 2f
            val innerBlackSize = cornerBoxSize * 0.55f
            val halfInner = innerBlackSize / 2f

            val tlCenterX = left + (finalSheetW * 0.065f)
            val tlCenterY = top + (finalSheetH * 0.055f)

            val trCenterX = right - (finalSheetW * 0.065f)
            val trCenterY = top + (finalSheetH * 0.055f)

            val blCenterX = left + (finalSheetW * 0.065f)
            val blCenterY = bottom - (finalSheetH * 0.055f)

            val brCenterX = right - (finalSheetW * 0.065f)
            val brCenterY = bottom - (finalSheetH * 0.055f)

            val vibrantGreen = Color(0xFF00E676)
            val greenBorder = Color(0xFF00C853)

            val cornersToHighlight = mutableListOf<Offset>()
            if (cornerAlignment.tl || lastPaper != null || isScanning) {
              cornersToHighlight.add(Offset(tlCenterX, tlCenterY))
            }
            if (cornerAlignment.tr || lastPaper != null || isScanning) {
              cornersToHighlight.add(Offset(trCenterX, trCenterY))
            }
            if (cornerAlignment.bl || lastPaper != null || isScanning) {
              cornersToHighlight.add(Offset(blCenterX, blCenterY))
            }
            if (cornerAlignment.br || lastPaper != null || isScanning) {
              cornersToHighlight.add(Offset(brCenterX, brCenterY))
            }

            cornersToHighlight.forEach { center ->
              // Outer vibrant green square box
              drawRect(
                color = vibrantGreen,
                topLeft = Offset(center.x - halfBox, center.y - halfBox),
                size = Size(cornerBoxSize, cornerBoxSize)
              )
              drawRect(
                color = greenBorder,
                topLeft = Offset(center.x - halfBox, center.y - halfBox),
                size = Size(cornerBoxSize, cornerBoxSize),
                style = Stroke(width = 2.5f)
              )
              // Inner black fiducial center
              drawRect(
                color = Color.Black,
                topLeft = Offset(center.x - halfInner, center.y - halfInner),
                size = Size(innerBlackSize, innerBlackSize)
              )
            }
          }

          // Camera Bottom Alignment Prompt Banner Pill (Matching WhatsApp Image reference)
          if (lastPaper == null) {
            Box(
              modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp),
              contentAlignment = Alignment.Center
            ) {
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xDD1E293B),
                shadowElevation = 6.dp
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                  // Center Alignment Status & Quiz Code
                  Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                  ) {
                    Text(
                      text = if (cornerAlignment.count == 0) "Align squares in viewfinders"
                             else if (cornerAlignment.count == 4) "All 4 aligned - Scanning..."
                             else "${cornerAlignment.count} of 4 corners locked",
                      color = if (cornerAlignment.count == 4) Color(0xFF00E676) else Color.White,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.SemiBold,
                      textAlign = TextAlign.Center
                    )
                    val quizCode = quiz?.id?.takeLast(4) ?: "4029"
                    Text(
                      text = "${quiz?.name ?: "Quiz"} ($quizCode)",
                      color = Color(0xFF94A3B8),
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Normal,
                      textAlign = TextAlign.Center
                    )
                  }

                  // Quick manual shutter fallback button
                  IconButton(
                    onClick = {
                      val capture = imageCapture
                      if (capture != null && !isScanning) {
                        val executor = Executors.newSingleThreadExecutor()
                        capture.takePicture(
                          executor,
                          object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                              val buffer = image.planes[0].buffer
                              val bytes = ByteArray(buffer.remaining())
                              buffer.get(bytes)
                              val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                              image.close()
                              if (bitmap != null) {
                                viewModel.processScannedBitmap(bitmap) {}
                              }
                            }

                            override fun onError(exception: ImageCaptureException) {
                              exception.printStackTrace()
                            }
                          }
                        )
                      }
                    },
                    modifier = Modifier
                      .size(42.dp)
                      .background(Color(0xFF1E6827), CircleShape)
                      .testTag("camera_shutter_button")
                  ) {
                    Icon(
                      imageVector = Icons.Default.CameraAlt,
                      contentDescription = "Scan Sheet",
                      tint = Color.White,
                      modifier = Modifier.size(22.dp)
                    )
                  }
                }
              }
            }
          }
        }
      } else {
        // Camera Permission Fallback Request Box
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp)
              )
              Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "The OMR scanner needs camera access to quickly detect alignment markers and read answer bubbles.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth()
              ) {
                Text("Grant Camera Permission")
              }
              OutlinedButton(
                onClick = { showDemoFeederDialog = true },
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Use Sample Test Sheets Instead")
              }
            }
          }
        }
      }

      // Scanning In-Progress Indicator Overlay
      if (isScanning) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
          contentAlignment = Alignment.Center
        ) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            modifier = Modifier.padding(24.dp)
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
              )
              Text(
                text = "Processing OMR Sheet...",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Detecting corner markers & evaluating bubbles...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
              )
            }
          }
        }
      }

      // Success Modal Bottom Card (as described in prompt #10)
      lastPaper?.let { paper ->
        Box(
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("scan_result_modal_card")
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(24.dp)
                  )
                  Text(
                    text = "Paper Scanned Successfully",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
                IconButton(
                  onClick = {
                    cornerAlignment = CornerAlignmentState()
                    lastCaptureTimestamp = System.currentTimeMillis() + 1800L
                    viewModel.clearScanResult()
                  },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.Close, contentDescription = "Close")
                }
              }

              // Details Grid
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(
                  modifier = Modifier.padding(14.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("Student Name:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(paper.studentName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("Score:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(
                      "${"%.1f".format(paper.score)} / ${"%.1f".format(paper.totalPossibleMarks)}",
                      fontWeight = FontWeight.Bold,
                      color = MaterialTheme.colorScheme.primary,
                      fontSize = 15.sp
                    )
                  }
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Text("Percentage:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(
                      "${"%.1f".format(paper.percentage)}%",
                      fontWeight = FontWeight.Bold,
                      color = if (paper.percentage >= 60f) SuccessGreen else WarningAmber,
                      fontSize = 15.sp
                    )
                  }

                  if (paper.reviewRequiredCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                      color = WarningAmberContainer,
                      shape = RoundedCornerShape(8.dp)
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                      ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
                        Text(
                          text = "${paper.reviewRequiredCount} question(s) need manual review",
                          color = WarningAmber,
                          fontSize = 12.sp,
                          fontWeight = FontWeight.Bold
                        )
                      }
                    }
                  }
                }
              }

              // Action Buttons
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                OutlinedButton(
                  onClick = {
                    cornerAlignment = CornerAlignmentState()
                    lastCaptureTimestamp = System.currentTimeMillis() + 1800L
                    viewModel.clearScanResult()
                  },
                  shape = RoundedCornerShape(12.dp),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("scan_next_paper_button")
                ) {
                  Text("Scan Next Paper")
                }
                Button(
                  onClick = {
                    val pid = paper.id
                    cornerAlignment = CornerAlignmentState()
                    lastCaptureTimestamp = System.currentTimeMillis() + 1800L
                    viewModel.clearScanResult()
                    onNavigateToPaperDetail(pid)
                  },
                  shape = RoundedCornerShape(12.dp),
                  colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                  modifier = Modifier
                    .weight(1f)
                    .testTag("review_paper_modal_button")
                ) {
                  Text("Review Paper", fontWeight = FontWeight.Bold)
                }
              }

              // Back to Quiz / Home Button
              TextButton(
                onClick = {
                  cornerAlignment = CornerAlignmentState()
                  viewModel.clearScanResult()
                  onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth().testTag("back_to_quiz_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = null,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Back to Quiz Hub")
              }
            }
          }
        }
      }
    }
  }

  // Demo Feeder Dialog (Immediate testing of realistic papers)
  if (showDemoFeederDialog) {
    AlertDialog(
      onDismissRequest = { showDemoFeederDialog = false },
      title = {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
          Text("Test Sample Sheets")
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Generate and feed realistic filled OMR sheets into the scanner to test end-to-end grading immediately:",
            style = MaterialTheme.typography.bodyMedium
          )

          val testProfiles = listOf(
            Triple("rahul", "Rahul Kumar", "NG12345 (Score ~75%)"),
            Triple("priya_top", "Priya Sharma", "NG20261 (Top Score ~94%)"),
            Triple("amit_review", "Amit Patel", "NG20263 (Includes Multiple/Blank)"),
            Triple("vikram_average", "Vikram Singh", "NG20264 (Average ~62%)")
          )

          testProfiles.forEach { (profileKey, name, desc) ->
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("sample_profile_$profileKey")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                  onClick = {
                    showDemoFeederDialog = false
                    viewModel.simulateSampleScan(profileKey) {}
                  },
                  shape = RoundedCornerShape(8.dp),
                  contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Text("Scan", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
              }
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showDemoFeederDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }
}
