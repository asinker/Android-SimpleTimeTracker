package com.example.util.simpletimetracker

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import com.example.util.simpletimetracker.core.provider.ContextProvider
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltAndroidApp
class TimeTrackerApp : Application() {

    @Inject
    lateinit var contextProvider: ContextProvider

    override fun onCreate() {
        super.onCreate()
        initLog()
        initLibraries()
        initStrictMode()
    }

    private fun initLog() {
        if (BuildConfig.DEBUG) {
            Timber.plant(DebugTree())
        }
    }

    private fun initLibraries() {
        val config = BundledEmojiCompatConfig(applicationContext)
            .setReplaceAll(true)
        EmojiCompat.init(config)
    }

    private fun initStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            // The violation type is inferred on purpose: StrictMode.Violation
                            // is @SystemApi and is not part of the public android.jar, but it
                            // extends Throwable, which is all that is needed here.
                            penaltyListener(strictModeExecutor) { onStrictModeViolation(it) }
                        } else {
                            penaltyDialog()
                        }
                    }
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build(),
            )
        }
    }

    /**
     * Only report violations that actually go through app code.
     *
     * Some OEM roms (OnePlus/Oppo: OplusSystemUINavigationGesture, OplusHansManager,
     * OplusScrollOptimizationHelper) read files from disk inside system_server, while
     * dispatching touches or inflating views. Those reads are attributed back to this
     * process through Binder, cannot be avoided from here, and would otherwise pop a
     * dialog on almost every touch, so for them penaltyLog() output is enough.
     */
    private fun onStrictModeViolation(violation: Throwable) {
        val belongsToApp = violation.stackTrace.any {
            it.className.startsWith(appNamespace)
        }
        if (belongsToApp) {
            Timber.e(violation, "StrictMode violation in app code")
            Handler(Looper.getMainLooper()).post { showStrictModeDialog(violation) }
        } else {
            Timber.d(violation, "StrictMode violation outside app code, ignored")
        }
    }

    private fun showStrictModeDialog(violation: Throwable) {
        // A dialog needs a window token, so it can only be shown over an activity.
        val activity = contextProvider.get() as? Activity ?: return
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            AlertDialog.Builder(activity)
                .setTitle("StrictMode: ${violation.javaClass.simpleName}")
                .setMessage(violation.stackTrace.joinToString("\n") { it.toString() })
                .setPositiveButton(android.R.string.ok, null)
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to show StrictMode dialog")
        }
    }

    companion object {
        // Namespace, not applicationId: app classes live in com.example..., and debug
        // builds are debuggable, so R8 keeps their names intact for this filter.
        private val appNamespace: String = TimeTrackerApp::class.java.packageName
        private val strictModeExecutor = Executors.newSingleThreadExecutor()
    }
}