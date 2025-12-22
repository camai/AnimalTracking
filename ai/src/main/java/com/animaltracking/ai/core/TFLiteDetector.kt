package com.animaltracking.ai.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.animaltracking.ai.api.DetectionBox
import com.animaltracking.ai.api.Detector
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
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

    // Model file name (ensure this file exists in assets)
    private val modelPath = "yolo11.tflite"

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val model = FileUtil.loadMappedFile(context, modelPath)
            val options = Interpreter.Options()
            options.setNumThreads(4) // Use 4 threads for CPU inference
            
            // Check for GPU Delegate validation
            try {
                val compatList = org.tensorflow.lite.gpu.CompatibilityList()
                if (compatList.isDelegateSupportedOnThisDevice) {
                     // Use GPU Delegate
                     // Note: GpuDelegate options can be customized if needed
                     val delegateOptions = compatList.bestOptionsForThisDevice
                     val gpuDelegate = org.tensorflow.lite.gpu.GpuDelegate(delegateOptions)
                     options.addDelegate(gpuDelegate)
                     Log.i("TFLiteDetector", "GPU Delegate Enabled")
                } else {
                    Log.i("TFLiteDetector", "GPU Delegate Not Supported, using CPU")
                }
            } catch (e: Exception) {
                Log.w("TFLiteDetector", "Failed to initialize GPU Delegate", e)
            }

            interpreter = Interpreter(model, options)

            val inputShape = interpreter?.getInputTensor(0)?.shape()
            inputImageHeight = inputShape?.get(1) ?: 320
            inputImageWidth = inputShape?.get(2) ?: 320
        } catch (e: Exception) {
            Log.e("TFLiteDetector", "Error setting up interpreter", e)
        }
    }

    override fun detect(image: Bitmap, rotation: Int): List<DetectionBox> {
        if (interpreter == null) setupInterpreter()
        if (interpreter == null) return emptyList()

        val numRotation = rotation / 90
        val imageProcessor = ImageProcessor.Builder()
            .add(Rot90Op(-numRotation))
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f))
            .build()
            
        var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
        tensorImage.load(image)
        tensorImage = imageProcessor.process(tensorImage)

        val outputTensor = interpreter!!.getOutputTensor(0)
        val outputShape = outputTensor.shape()
        
        val channels = outputShape[1]
        val anchors = outputShape[2]

        val outputBuffer = ByteBuffer.allocateDirect(4 * channels * anchors)
        outputBuffer.order(ByteOrder.nativeOrder())

        interpreter?.run(tensorImage.buffer, outputBuffer)

        outputBuffer.rewind()
        val floatArray = FloatArray(channels * anchors)
        outputBuffer.asFloatBuffer().get(floatArray)

        val detectionBoxes = ArrayList<DetectionBox>()

        for (i in 0 until anchors) {
            val indexCx = i
            val indexCy = anchors + i
            val indexW = 2 * anchors + i
            val indexH = 3 * anchors + i
            val indexScore = 4 * anchors + i
            
            val cx: Float = floatArray[indexCx]
            val cy: Float = floatArray[indexCy]
            val w: Float = floatArray[indexW]
            val h: Float = floatArray[indexH]
            val score: Float = floatArray[indexScore]

            if (score > 0.5f) {
                val halfW = w / 2.0f
                val halfH = h / 2.0f
                
                // Normalized coordinates [0, 1]
                val x1 = cx - halfW
                val y1 = cy - halfH
                val x2 = cx + halfW
                val y2 = cy + halfH

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

    private fun nms(boxes: List<DetectionBox>, iouThreshold: Float = 0.5f): List<DetectionBox> {
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
