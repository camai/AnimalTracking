# Animal Tracking Application

CameraX와 TensorFlow Lite를 활용한 실시간 동물(말) 추적 안드로이드 애플리케이션입니다.

##  아키텍처 (Architecture)
 
이 프로젝트는 **Clean Architecture** 원칙을 따르며, **멀티 모듈(Multi-Module)** 구조로 확장성을 극대화했습니다.
 
```mermaid
graph TD
    subgraph FeatureHorseTracking [Feature: Horse Tracking]
        ViewModel[TrackingViewModel] --> UseCase[IoUTracker]
        Screen[TrackingScreen] --> ViewModel
        Screen --> Overlay[BoundingBoxOverlay]
    end

    subgraph FeatureCamera [Feature: Camera]
        Camera[CameraPreview]
    end
 
    subgraph Domain [Domain Layer]
        UseCase --> TrackerInterface[ObjectTracker]
        ViewModel --> DetectorInterface[ObjectDetector]
        Entity[TrackedObject]
        EntityBox[BoundingBox]
    end
 
    subgraph Data [Data Layer]
        DetectorImpl[TFLiteObjectDetector] -.-> DetectorInterface
        DetectorImpl -- Delegate --> AiLib[AI Library]
    end
    
    subgraph AI [Core AI Module]
        AiLib[Detector Interface]
        AiCore[TFLiteDetector] -.-> AiLib
        AiCore --> Assets[yolo11.tflite]
    end
 
    subgraph Core [Core Android Layer]
        Utils[BitmapUtils]
    end
 
    Screen --> Camera
    Camera -- ImageProxy --> ViewModel
    ViewModel -- Bitmap --> DetectorImpl
    DetectorImpl -- Bitmap --> AiCore
    AiCore -- List<DetectionBox> --> DetectorImpl
    DetectorImpl -- List<BoundingBox> --> ViewModel
    ViewModel -- List<BoundingBox> --> UseCase
    UseCase -- List<TrackedObject> --> ViewModel
    ViewModel -- StateFlow --> Screen
```

## 모듈 구조 (Module Structure)

*   **:app**: 애플리케이션의 진입점 및 의존성 조립.
*   **:domain**: 순수 비즈니스 로직 (UseCases, Models, Interface). 플랫폼 의존성 없음.
*   **:data**: 데이터 리포지토리 구현. AI 엔진의 결과를 도메인 모델로 변환.
*   **:feature:camera**: 카메라 프리뷰/권한 처리 전용 UI.
*   **:feature:horse-tracking**: 말 추적 UI 및 화면 로직 (Compose, ViewModel).
*   **:ai**: 독립적인 AI 탐지 라이브러리 모듈.
    *   TensorFlow Lite 로직을 캡슐화하여, 다른 앱에서도 재사용 가능하도록 설계.
*   **:core-android**: Android 전용 공통 유틸리티 (Bitmap 처리 등).

---

##  주요 기능 (Key Features)

*   **실시간 객체 탐지 (Real-time Object Detection)**: **YOLO11n** (TensorFlow)을 사용하여 말을 실시간으로 탐지합니다.
*   **객체 추적 (Object Tracking)**: **IoU (Intersection over Union)** 기반의 트래커를 구현하여 프레임 간 객체에 고유 ID를 부여하고 추적합니다.
*   **CameraX 통합**: `ImageAnalysis`와 `STRATEGY_KEEP_ONLY_LATEST` 전략을 사용하여 카메라 프레임을 효율적으로 처리합니다.
*   **최신 UI**: **Jetpack Compose**로 완전히 구축되었으며, Bounding Box를 그리기 위한 커스텀 Canvas 오버레이를 포함합니다.

##  기술 스택 (Tech Stack)

*   **Language**: Kotlin
*   **UI**: Jetpack Compose
*   **Dependency Injection**: Hilt
*   **ML**: LiteRT (TensorFlow Lite), YOLO11
*   **Camera**: CameraX
*   **Architecture**: Clean Architecture, MVVM, Multi-Module
*   **Build**: Gradle Kotlin DSL, Version Catalogs
