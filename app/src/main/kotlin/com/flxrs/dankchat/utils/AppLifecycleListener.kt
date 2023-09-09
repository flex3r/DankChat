package com.flxrs.dankchat.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLifecycleListener @Inject constructor(val app: Application) {
    sealed interface AppLifecycle {
        data object Background : AppLifecycle
        data object Foreground : AppLifecycle
    }

    private val _appState = MutableStateFlow<AppLifecycle>(AppLifecycle.Background)
    val appState = _appState.asStateFlow()

    init {
        app.registerActivityLifecycleCallbacks(LifecycleCallback { _appState.value = it })
    }

    private class LifecycleCallback(private val action: (AppLifecycle) -> Unit) : Application.ActivityLifecycleCallbacks {
        var currentForegroundActivity: Activity? = null

        override fun onActivityPaused(activity: Activity) {
            if (currentForegroundActivity == activity) {
                currentForegroundActivity = null
                action(AppLifecycle.Background)
            }
        }

        override fun onActivityResumed(activity: Activity) {
            currentForegroundActivity = activity
            action(AppLifecycle.Foreground)
        }

        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    }
}
