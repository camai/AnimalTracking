package com.animaltracking.domain.usecase

import com.animaltracking.domain.model.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IoUTrackerTest {

    private lateinit var tracker: IoUTracker

    @Before
    fun setup() {
        tracker = IoUTracker()
    }

    @Test
    fun `초기_탐지시_새로운_ID가_부여되어야_한다`() {
        val detection = createBoundingBox(100f, 100f, 200f, 200f)
        val trackedObjects = tracker.track(listOf(detection))

        assertEquals(1, trackedObjects.size)
        assertEquals(0, trackedObjects[0].id)
    }

    @Test
    fun `지속적인_탐지시_ID가_유지되어야_한다`() {
        // 프레임 1
        val detection1 = createBoundingBox(100f, 100f, 200f, 200f)
        var trackedObjects = tracker.track(listOf(detection1))
        val id1 = trackedObjects[0].id

        // 프레임 2 (약간 움직임, 높은 IoU)
        val detection2 = createBoundingBox(105f, 105f, 205f, 205f)
        trackedObjects = tracker.track(listOf(detection2))

        assertEquals(1, trackedObjects.size)
        assertEquals(id1, trackedObjects[0].id)
    }

    @Test
    fun `멀리_떨어진_새로운_탐지는_새로운_ID가_부여되어야_한다`() {
        // 프레임 1
        val detection1 = createBoundingBox(0f, 0f, 50f, 50f)
        tracker.track(listOf(detection1))

        // 프레임 2 (멀리 떨어짐, IoU = 0)
        val detection2 = createBoundingBox(200f, 200f, 250f, 250f)
        val trackedObjects = tracker.track(listOf(detection2))

        // 2개의 객체가 반환되어야 하는가?
        // 현재 로직상:
        // - 기존 객체는 "소실(미스)" 상태 (메모리 유지), 현재 프레임에는 반환되지 않음?
        // - 새로운 객체는 새 ID로 추적됨.
        // 로직 확인: "newTrackedObjects"는 *현재* 탐지에 대해 *업데이트된* 또는 *신규* 트랙만 포함함.
        // 따라서 1개의 객체(새로운 것)만 보여야 함.
        
        assertEquals(1, trackedObjects.size) 
        assertTrue(trackedObjects[0].id != 0) // 새로운 ID여야 함
    }

    private fun createBoundingBox(x1: Float, y1: Float, x2: Float, y2: Float): BoundingBox {
        val w = x2 - x1
        val h = y2 - y1
        val cx = x1 + w / 2
        val cy = y1 + h / 2
        return BoundingBox(x1, y1, x2, y2, cx, cy, w, h, 0.9f, 0, "Horse")
    }
}
