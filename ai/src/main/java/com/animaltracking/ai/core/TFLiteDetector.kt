package com.animaltracking.ai.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.animaltracking.ai.api.DetectionBox
import com.animaltracking.ai.api.Detector
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

class TFLiteDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : Detector {

    private var interpreter: Interpreter? = null
    private var inputImageWidth: Int = 0
    private var inputImageHeight: Int = 0

    private val modelPath = "yolo11.tflite"

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val model = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options()
            options.setNumThreads(4)
            
            var gpuDelegate: GpuDelegate? = null
            
            try {
                val compatList = CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                     gpuDelegate = GpuDelegate()
                     options.addDelegate(gpuDelegate)
                }
            } catch (e: Exception) {
                Log.w("TFLiteDetector", "Failed to configure GPU Delegate: ${e.message}")
            }

            try {
                interpreter = Interpreter(model, options)
            } catch (e: Exception) {
                Log.e("TFLiteDetector", "Failed to initialize Interpreter with GPU options. Falling back to CPU.", e)
                
                if (gpuDelegate != null) {
                    gpuDelegate.close()
                    gpuDelegate = null
                }
                
                val cpuOptions = Interpreter.Options()
                cpuOptions.setNumThreads(4)
                
                interpreter = Interpreter(model, cpuOptions)
            }

            val inputShape = interpreter?.getInputTensor(0)?.shape()
            inputImageHeight = inputShape?.get(1) ?: 320
            inputImageWidth = inputShape?.get(2) ?: 320
        } catch (e: Exception) {
            Log.e("TFLiteDetector", "Fatal Error setting up interpreter: ${e.message}", e)
        }
    }

    override fun detect(image: Bitmap, rotation: Int): List<DetectionBox> {
        if (interpreter == null) {
            setupInterpreter()
        }
        if (interpreter == null) return emptyList()

        // 1. 전처리
        val numRotation = rotation / 90
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-numRotation))
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()
            
        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(image)
        tensorImage = imageProcessor.process(tensorImage)

        // 2. 추론
        val outputTensor = interpreter!!.getOutputTensor(0)
        val outputShape = outputTensor.shape() // [1, 채널, 앵커]
        val channels = outputShape[1]
        val anchors = outputShape[2]

        val outputBuffer = ByteBuffer.allocateDirect(4 * channels * anchors)
        outputBuffer.order(ByteOrder.nativeOrder())
        interpreter?.run(tensorImage.buffer, outputBuffer)

        outputBuffer.rewind()
        val floatArray = FloatArray(channels * anchors)
        outputBuffer.asFloatBuffer().get(floatArray)

        // 3. 후처리(파싱 및 필터링)
        val detectionBoxes = ArrayList<DetectionBox>()

        for (i in 0 until anchors) {
            // YOLO 출력 레이아웃은 다를 수 있음. 앵커당 [cx, cy, w, h, score, ...]로 가정
            // 이전 코드 기준: indexScore가 4 * anchors + i 위치(평면 또는 특정 stride 가정)
            // 모델에 맞다고 가정하고 기존 인덱싱 로직 유지
            
            val indexCx = i
            val indexCy = anchors + i
            val indexW = 2 * anchors + i
            val indexH = 3 * anchors + i
            val indexScore = 4 * anchors + i
            
            val score = floatArray[indexScore]

            // 신뢰도로만 필터링(후면 탐지를 위해 0.45로 완화)
            if (score > 0.45f) {
                var cx = floatArray[indexCx]
                var cy = floatArray[indexCy]
                var w = floatArray[indexW]
                var h = floatArray[indexH]

                // 필요 시 정규화(모델 출력이 1.0 미만이라고 가정하지만 안전 체크)
                if (cx > 1.0f || cy > 1.0f || w > 1.0f || h > 1.0f) {
                     cx /= inputImageWidth
                     cy /= inputImageHeight
                     w /= inputImageWidth
                     h /= inputImageHeight
                }

                val x1 = cx - w / 2f
                val y1 = cy - h / 2f
                val x2 = cx + w / 2f
                val y2 = cy + h / 2f

                detectionBoxes.add(
                    DetectionBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h,
                        confidence = score, 
                        classId = 0, 
                        className = "Horse"
                    )
                )
            }
        }

        return nms(detectionBoxes)
    }

    private fun nms(boxes: List<DetectionBox>, iouThreshold: Float = 0.3f): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()

        val sortedBoxes = boxes.sortedByDescending { it.confidence }
        val activeBoxes = MutableList(boxes.size) { true }
        val result = ArrayList<DetectionBox>()

        for (i in sortedBoxes.indices) {
            if (activeBoxes[i]) {
                val boxA = sortedBoxes[i]
                result.add(boxA)

                for (j in i + 1 until sortedBoxes.size) {
                    if (activeBoxes[j]) {
                        val boxB = sortedBoxes[j]
                        if (calculateIoU(boxA, boxB) > iouThreshold) {
                            activeBoxes[j] = false
                        }
                    }
                }
            }
        }
        return result
    }

    private fun calculateIoU(boxA: DetectionBox, boxB: DetectionBox): Float {
        val xA = max(boxA.x1, boxB.x1)
        val yA = max(boxA.y1, boxB.y1)
        val xB = min(boxA.x2, boxB.x2)
        val yB = min(boxA.y2, boxB.y2)

        val interArea = max(0f, xB - xA) * max(0f, yB - yA)
        val boxAArea = (boxA.x2 - boxA.x1) * (boxA.y2 - boxA.y1)
        val boxBArea = (boxB.x2 - boxB.x1) * (boxB.y2 - boxB.y1)

        return interArea / (boxAArea + boxBArea - interArea)
    }
}
