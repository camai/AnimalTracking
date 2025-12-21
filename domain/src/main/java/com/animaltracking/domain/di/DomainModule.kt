package com.animaltracking.domain.di

import com.animaltracking.domain.repository.ObjectTracker
import com.animaltracking.domain.usecase.IoUTracker
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
}
