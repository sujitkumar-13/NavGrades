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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.StrokeCap
import com.example.data.model.ScannedPaperEntity
import com.example.omr.CornerAlignmentState
import com.example.omr.OmrLayoutDefinition
import com.example.omr.OmrScannerEngine
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenContainer
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningAmberContainer
import com.example.ui.viewmodel.OmrViewModel
import java.util.concurrent.Executors

private fun rotateBitmapIfNeeded(bitmap: Bitmap, degrees: Int): Bitmap {
  if (degrees == 0) return bitmap
  val matrix = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
  return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

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
  var stableFrameCount by remember { mutableStateOf(0) }
  var previousAlignment by remember { mutableStateOf<CornerAlignmentState?>(null) }
  var scanStatusMessage by remember { mutableStateOf("Align squares in viewfinders") }
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
    stableFrameCount = 0
    previousAlignment = null
    scanStatusMessage = "Align squares in viewfinders"
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
                    if (!isScanning && lastPaper == null && (now - lastAnalysisTimestamp > 140L)) {
                      lastAnalysisTimestamp = now
                      val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                      val rawBmp = imageProxy.toBitmap()
                      if (rawBmp != null) {
                        val bmp = rotateBitmapIfNeeded(rawBmp, rotationDegrees)
                        val alignment = OmrScannerEngine.detectCornerAlignment(bmp)
                        cornerAlignment = alignment

                        if (alignment.isReadyForCapture) {
                          val prev = previousAlignment
                          val isSteady = prev != null && alignment.isCloseTo(prev, maxDrift = 0.035f)
                          if (isSteady) {
                            stableFrameCount++
                          } else {
                            stableFrameCount = 1
                          }
                          previousAlignment = alignment

                          scanStatusMessage = if (stableFrameCount >= 3) {
                            "Hold steady... Scanning!"
                          } else {
                            "Hold steady..."
                          }

                          if (stableFrameCount >= 4 && !isScanning && lastPaper == null && (now - lastCaptureTimestamp > 2200L)) {
                            lastCaptureTimestamp = now
                            stableFrameCount = 0
                            previousAlignment = null
                            scanStatusMessage = "Processing OMR..."
                            viewModel.processScannedBitmap(bmp) {}
                          }
                        } else {
                          stableFrameCount = 0
                          previousAlignment = null
                          scanStatusMessage = when (alignment.count) {
                            0 -> "Align squares in viewfinders"
                            1, 2, 3 -> "${alignment.count} of 4 corners locked"
                            else -> "Hold sheet flat inside viewfinder"
                          }
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

            // Aspect ratio of canonical sheet (1024 / 682 ~ 1.501f)
            val sheetAspectRatio = OmrLayoutDefinition.SHEET_ASPECT_RATIO
            val bottomReserved = 100.dp.toPx()
            val maxAvailableH = canvasH - bottomReserved - 24.dp.toPx()

            // Keep a comfortable visual margin between physical sheet and screen/frame edges
            val frameMargin = 16.dp.toPx()

            // Target sheet dimensions inside viewfinder (fits comfortably without touching boundary)
            var sheetW = canvasW * 0.85f
            var sheetH = sheetW * sheetAspectRatio

            if (sheetH > maxAvailableH) {
              sheetH = maxAvailableH
              sheetW = sheetH / sheetAspectRatio
            }

            val sheetLeft = (canvasW - sheetW) / 2f
            val sheetTop = ((maxAvailableH - sheetH) / 2f + 16.dp.toPx()).coerceAtLeast(18.dp.toPx())
            val sheetRight = sheetLeft + sheetW
            val sheetBottom = sheetTop + sheetH

            // Outer scanning viewfinder frame bounds (spaced outwards by frameMargin)
            val frameLeft = (sheetLeft - frameMargin).coerceAtLeast(6.dp.toPx())
            val frameTop = (sheetTop - frameMargin).coerceAtLeast(6.dp.toPx())
            val frameRight = (sheetRight + frameMargin).coerceAtMost(canvasW - 6.dp.toPx())
            val frameBottom = (sheetBottom + frameMargin)
            val frameW = frameRight - frameLeft
            val frameH = frameBottom - frameTop

            // 1. Semi-transparent scrim outside sheet area
            val scrimColor = Color(0x50000000)
            drawRect(color = scrimColor, topLeft = Offset(0f, 0f), size = Size(canvasW, frameTop))
            drawRect(color = scrimColor, topLeft = Offset(0f, frameBottom), size = Size(canvasW, canvasH - frameBottom))
            drawRect(color = scrimColor, topLeft = Offset(0f, frameTop), size = Size(frameLeft, frameH))
            drawRect(color = scrimColor, topLeft = Offset(frameRight, frameTop), size = Size(canvasW - frameRight, frameH))

            // 2. Viewfinder boundary outline with 2px stroke
            val isLockedAndSteady = cornerAlignment.isReadyForCapture && stableFrameCount >= 2
            val frameColor = if (isLockedAndSteady) Color(0xFF00E676)
                             else if (cornerAlignment.isReadyForCapture) Color(0xFF00E676)
                             else if (cornerAlignment.count > 0) Color(0xFFFFB300)
                             else Color(0x77FFFFFF)
            val frameStrokeWidth = 2.dp.toPx()
            drawRoundRect(
              color = frameColor,
              topLeft = Offset(frameLeft, frameTop),
              size = Size(frameW, frameH),
              cornerRadius = CornerRadius(16f, 16f),
              style = Stroke(width = frameStrokeWidth)
            )

            // 3. Corner L-bracket reticle guides at sheet perimeter
            val bracketLen = (sheetW * 0.07f).coerceAtLeast(20.dp.toPx())
            val bracketStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            val bracketColor = if (isLockedAndSteady) Color(0xFF00E676)
                               else if (cornerAlignment.count > 0) Color(0xFFFFB300)
                               else Color(0x88FFFFFF)

            // Top-Left L
            drawLine(bracketColor, Offset(sheetLeft, sheetTop), Offset(sheetLeft + bracketLen, sheetTop), bracketStroke.width)
            drawLine(bracketColor, Offset(sheetLeft, sheetTop), Offset(sheetLeft, sheetTop + bracketLen), bracketStroke.width)
            // Top-Right L
            drawLine(bracketColor, Offset(sheetRight, sheetTop), Offset(sheetRight - bracketLen, sheetTop), bracketStroke.width)
            drawLine(bracketColor, Offset(sheetRight, sheetTop), Offset(sheetRight, sheetTop + bracketLen), bracketStroke.width)
            // Bottom-Left L
            drawLine(bracketColor, Offset(sheetLeft, sheetBottom), Offset(sheetLeft + bracketLen, sheetBottom), bracketStroke.width)
            drawLine(bracketColor, Offset(sheetLeft, sheetBottom), Offset(sheetLeft, sheetBottom - bracketLen), bracketStroke.width)
            // Bottom-Right L
            drawLine(bracketColor, Offset(sheetRight, sheetBottom), Offset(sheetRight - bracketLen, sheetBottom), bracketStroke.width)
            drawLine(bracketColor, Offset(sheetRight, sheetBottom), Offset(sheetRight, sheetBottom - bracketLen), bracketStroke.width)

            // 4. 4 Corner Fiducial Target Markers (responsive size, centered on real detected markers, thin ~2px stroke)
            val imgW = cornerAlignment.imageWidth
            val imgH = cornerAlignment.imageHeight
            val guideStrokeWidth = 2.dp.toPx() // ~2px thin stroke

            // Default corner target positions when markers are not yet detected
            val defaultTlX = sheetLeft + (sheetW * 0.038f)
            val defaultTlY = sheetTop + (sheetH * 0.026f)

            val defaultTrX = sheetRight - (sheetW * 0.038f)
            val defaultTrY = sheetTop + (sheetH * 0.026f)

            val defaultBlX = sheetLeft + (sheetW * 0.038f)
            val defaultBlY = sheetBottom - (sheetH * 0.026f)

            val defaultBrX = sheetRight - (sheetW * 0.038f)
            val defaultBrY = sheetBottom - (sheetH * 0.026f)

            // Responsive marker mapping from camera image coordinates to Canvas screen coordinates
            fun getCornerGuide(pos: com.example.omr.CornerPoint?, defaultX: Float, defaultY: Float): Pair<Offset, Float> {
              if (pos != null && imgW > 0 && imgH > 0) {
                val scale = maxOf(canvasW / imgW.toFloat(), canvasH / imgH.toFloat())
                val offsetX = (canvasW - imgW * scale) / 2f
                val offsetY = (canvasH - imgH * scale) / 2f
                val sx = offsetX + (pos.x * imgW * scale)
                val sy = offsetY + (pos.y * imgH * scale)
                val markerPx = pos.size * imgW * scale
                val boxSize = (markerPx * 1.35f).coerceIn(24.dp.toPx(), 44.dp.toPx())
                return Pair(Offset(sx, sy), boxSize)
              } else {
                val fallbackSize = (sheetW * 0.085f).coerceIn(24.dp.toPx(), 40.dp.toPx())
                return Pair(Offset(defaultX, defaultY), fallbackSize)
              }
            }

            val (tlCenter, tlSize) = getCornerGuide(cornerAlignment.tlPos, defaultTlX, defaultTlY)
            val (trCenter, trSize) = getCornerGuide(cornerAlignment.trPos, defaultTrX, defaultTrY)
            val (blCenter, blSize) = getCornerGuide(cornerAlignment.blPos, defaultBlX, defaultBlY)
            val (brCenter, brSize) = getCornerGuide(cornerAlignment.brPos, defaultBrX, defaultBrY)

            val vibrantGreen = Color(0xFF00E676)
            val alignedFill = Color(0x2000E676) // 12% translucent green tint
            val unalignedBorder = Color(0x66FFFFFF)
            val unalignedFill = Color(0x0AFFFFFF)

            val cornerGuides = listOf(
              Triple(tlCenter, cornerAlignment.tl || lastPaper != null || isScanning, tlSize),
              Triple(trCenter, cornerAlignment.tr || lastPaper != null || isScanning, trSize),
              Triple(blCenter, cornerAlignment.bl || lastPaper != null || isScanning, blSize),
              Triple(brCenter, cornerAlignment.br || lastPaper != null || isScanning, brSize)
            )

            cornerGuides.forEach { (center, isAligned, boxSize) ->
              val halfBox = boxSize / 2f
              val boxTopLeft = Offset(center.x - halfBox, center.y - halfBox)
              val boxRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

              if (isAligned) {
                // Aligned state: thin ~2px green stroke centered on the real detected marker
                drawRoundRect(
                  color = alignedFill,
                  topLeft = boxTopLeft,
                  size = Size(boxSize, boxSize),
                  cornerRadius = boxRadius
                )
                drawRoundRect(
                  color = vibrantGreen,
                  topLeft = boxTopLeft,
                  size = Size(boxSize, boxSize),
                  cornerRadius = boxRadius,
                  style = Stroke(width = guideStrokeWidth)
                )
              } else {
                // Unaligned state: thin ~2px subtle outline guide at corner target
                drawRoundRect(
                  color = unalignedFill,
                  topLeft = boxTopLeft,
                  size = Size(boxSize, boxSize),
                  cornerRadius = boxRadius
                )
                drawRoundRect(
                  color = unalignedBorder,
                  topLeft = boxTopLeft,
                  size = Size(boxSize, boxSize),
                  cornerRadius = boxRadius,
                  style = Stroke(width = guideStrokeWidth)
                )
                drawCircle(
                  color = Color(0x55FFFFFF),
                  radius = 2.dp.toPx(),
                  center = center
                )
              }
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
                      text = scanStatusMessage,
                      color = if (cornerAlignment.isReadyForCapture && stableFrameCount >= 2) Color(0xFF00E676)
                              else if (cornerAlignment.count > 0) Color(0xFFFFB300)
                              else Color.White,
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
                              val rotationDegrees = image.imageInfo.rotationDegrees
                              val buffer = image.planes[0].buffer
                              val bytes = ByteArray(buffer.remaining())
                              buffer.get(bytes)
                              val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                              image.close()
                              if (rawBitmap != null) {
                                val bitmap = rotateBitmapIfNeeded(rawBitmap, rotationDegrees)
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
                    val displayName = listOf(paper.firstName, paper.lastName)
                      .filter { it.isNotBlank() }
                      .joinToString(" ")
                      .trim()
                      .ifBlank { paper.studentName }
                    Text(displayName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                  }
                  val phoneDisplay = paper.phoneNumber.ifBlank { paper.whatsappNumber }
                  if (phoneDisplay.isNotBlank()) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text("Phone:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                      Text(phoneDisplay, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                  }
                  if (paper.questionSetName.isNotBlank()) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text("Question Set:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                      Text(paper.questionSetName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                    }
                  }
                  if (paper.cast.isNotBlank()) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text("Caste:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                      Text(paper.cast, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
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
