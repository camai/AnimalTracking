package com.animaltracking.domain.di

import com.animaltracking.core.tracking.algorithm.IoUTracker
import com.animaltracking.core.tracking.repository.ObjectTracker
import com.animaltracking.domain.usecase.TrackObjectsUseCase
import com.animaltracking.domain.usecase.TrackObjectsUseCaseImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindObjectTracker(
        ioUTracker: IoUTracker
    ): ObjectTracker

    @Binds
    @Singleton
    abstract fun bindTrackObjectsUseCase(
        trackObjectsUseCaseImpl: TrackObjectsUseCaseImpl
    ): TrackObjectsUseCase
}
