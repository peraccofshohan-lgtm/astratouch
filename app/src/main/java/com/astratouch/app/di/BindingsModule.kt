package com.astratouch.app.di

import com.astratouch.app.screenshot.ScreenshotStore
import com.astratouch.app.service.action.ScreenshotSaver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    @Singleton
    abstract fun bindScreenshotSaver(impl: ScreenshotStore): ScreenshotSaver
}
