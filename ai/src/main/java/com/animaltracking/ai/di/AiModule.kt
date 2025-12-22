package com.animaltracking.ai.di

import com.animaltracking.ai.api.Detector
import com.animaltracking.ai.core.TFLiteDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindDetector(
        tfLiteDetector: TFLiteDetector
    ): Detector
}
