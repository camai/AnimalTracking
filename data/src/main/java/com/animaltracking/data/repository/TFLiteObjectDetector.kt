package com.animaltracking.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.animaltracking.domain.model.BoundingBox
import com.animaltracking.domain.repository.ObjectDetector
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import javax.inject.Inject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TFLiteObjectDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : ObjectDetector {

    private var interpreter: Interpreter? = null
    private var inputImageWidth: Int = 0
    private var inputImageHeight: Int = 0

    // 보통 YOLO11n 320x320 모델 사용
    private val modelPath = "yolo11.tflite"

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options()
        interpreter = Interpreter(model, options)

        val inputShape =
            interpreter?.getInputTensor(0)?.shape() // [1, 320, 320, 3] 혹은 [1, 3, 320, 320]
        inputImageHeight = inputShape?.get(1) ?: 320
        inputImageWidth = inputShape?.get(2) ?: 320

        // 디버그: 입력 텐서 형태 로그 확인
        // Log.d("TFLite", "Input shape: ${java.util.Arrays.toString(inputShape)}")
    }

    override fun detect(image: Bitmap, rotation: Int): List<BoundingBox> {
        if (interpreter == null) setupInterpreter()

        // 전처리 (Preprocess)
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputImageHeight, inputImageWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // 실수(Float) 입력을 위해 [0, 1] 범위로 정규화
            .build()

        var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
        tensorImage.load(image)
        tensorImage = imageProcessor.process(tensorImage)

        // 출력 버퍼 (Output buffer)
        // YOLO 출력: 보통 [1, 4+클래스수, 8400] 혹은 전치된 형태
        // 동적으로 출력 형태(Shape)를 확인할 필요가 있음
        // YOLO11n 출력: 단일 클래스(말)의 경우 보통 [1, 5, 8400] (cx, cy, w, h, conf)
        // 실제 내보내기(export) 설정에 따라 [1, 8400, 5]가 될 수도 있음
        // 하지만 기본 Ultralytics 내보내기는 [1, 4+nc, 8400]임

        val outputTensor = interpreter!!.getOutputTensor(0)
        val outputShape = outputTensor.shape()

        // 일반적인 형태 처리
        // 경우 1: [1, 5, 8400] -> [1, 채널, 앵커]
        val channels = outputShape[1] // 5 (cx, cy, w, h, conf)
        val anchors = outputShape[2]  // 8400 (320x320 입력 기준)

        val outputBuffer = ByteBuffer.allocateDirect(4 * channels * anchors)
        outputBuffer.order(ByteOrder.nativeOrder())

        interpreter?.run(tensorImage.buffer, outputBuffer)

        outputBuffer.rewind()
        val floatArray = FloatArray(channels * anchors)
        outputBuffer.asFloatBuffer().get(floatArray)

        val boundingBoxes = ArrayList<BoundingBox>()

        for (i in 0 until anchors) {
            // [1, 5, 8400] 메모리 레이아웃:
            // 채널 0 (cx): 8400개 값 연속
            // 채널 1 (cy): 8400개 값 연속
            // ...
            // 따라서 i번째 탐지 결과는 인덱스: i, anchors+i, 2*anchors+i, ... 에 위치함

            val cx = floatArray[i]
            val cy = floatArray[anchors + i]
            val w = floatArray[2 * anchors + i]
            val h = floatArray[3 * anchors + i]
            val score = floatArray[4 * anchors + i] // 클래스 0(말) 신뢰도

            if (score > 0.5f) { // 신뢰도 임계값 (Confidence threshold)
                val x1 = cx - w / 2
                val y1 = cy - h / 2
                val x2 = cx + w / 2
                val y2 = cy + h / 2

                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h,
                        cnf = score, cls = 0, clsName = "Horse"
                    )
                )
            }
        }

        // 단순 비최대 억제 (Simple NMS)
        return nms(boundingBoxes)
    }

    private fun nms(boxes: List<BoundingBox>, iouThreshold: Float = 0.5f): List<BoundingBox> {
        if (boxes.isEmpty()) return emptyList()

        val sortedBoxes = boxes.sortedByDescending { it.cnf }
        val activeBoxes = MutableList(boxes.size) { true }
        val result = ArrayList<BoundingBox>()

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

    private fun calculateIoU(boxA: BoundingBox, boxB: BoundingBox): Float {
        val xA = maxOf(boxA.x1, boxB.x1)
        val yA = maxOf(boxA.y1, boxB.y1)
        val xB = minOf(boxA.x2, boxB.x2)
        val yB = minOf(boxA.y2, boxB.y2)

        val interArea = maxOf(0f, xB - xA) * maxOf(0f, yB - yA)
        val boxAArea = (boxA.x2 - boxA.x1) * (boxA.y2 - boxA.y1)
        val boxBArea = (boxB.x2 - boxB.x1) * (boxB.y2 - boxB.y1)

        return interArea / (boxAArea + boxBArea - interArea)
    }
}
