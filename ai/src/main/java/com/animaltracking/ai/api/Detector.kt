package com.animaltracking.ai.api

import android.graphics.Bitmap

interface Detector {
    /**
     * 입력 비트맵에서 객체를 탐지합니다.
     * @param image 입력 이미지 (변환 시 회전이 적용된다면 정방향이어야 함)
     * @param rotation 회전 각도 (구현에 따라 선택적으로 사용)
     * @return 탐지된 바운딩 박스 목록 (정규화 범위 [0, 1])
     */
    fun detect(image: Bitmap, rotation: Int): List<DetectionBox>
}
