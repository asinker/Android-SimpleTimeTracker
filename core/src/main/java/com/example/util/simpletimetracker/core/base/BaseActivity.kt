package com.example.util.simpletimetracker.core.base

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.viewbinding.ViewBinding
import com.example.util.simpletimetracker.core.extension.allowDiskRead
import com.example.util.simpletimetracker.core.extension.allowDiskWrite
import com.example.util.simpletimetracker.core.manager.ThemeManager
import com.example.util.simpletimetracker.core.provider.ContextProvider
import com.example.util.simpletimetracker.core.utils.applyStatusBarInsets

abstract class BaseActivity<T : ViewBinding> : AppCompatActivity() {

    abstract val inflater: (LayoutInflater) -> T
    abstract val themeManager: ThemeManager
    abstract val contextProvider: ContextProvider
    protected val binding: T get() = _binding!!
    private var _binding: T? = null

    override fun attachBaseContext(newBase: Context?) {
        // Suppress strictMode for per app language prefs read for stored locale (see autoStoreLocales).
        // Only for api lower than 33.
        allowDiskWrite { super.attachBaseContext(newBase) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        allowDiskRead { super.onCreate(savedInstanceState) }
        contextProvider.attach(this)
        // enableEdgeToEdge() calls Window.getDecorView(), which creates the DecorView
        // and initializes AccessibilityManager. That is a binder call executed inside
        // system_server, and on some OEM roms (OnePlus/Oppo OplusHansManager) it reads
        // /proc, which StrictMode attributes back to this process as a DiskReadViolation.
        // The decor view cannot be created later, so the read is allowed here, same as
        // for super.onCreate() above.
        allowDiskRead { themeManager.setTheme(this) }
        // OnePlus/Oppo read a config file from disk in the static initializer of the
        // first OverScroller, which is reached here while TabLayout inflates a
        // HorizontalScrollView. Layout inflation has to run on the main thread and
        // that rom behaviour cannot be avoided, so only this call is allowed to
        // read from disk.
        val oldPolicy = android.os.StrictMode.allowThreadDiskReads()
        try {
            _binding = inflater(layoutInflater)
        } finally {
            android.os.StrictMode.setThreadPolicy(oldPolicy)
        }
        setContentView(binding.root)
        binding.root.applyStatusBarInsets()
        initUi()
        initUx()
        initViewModel()
    }

    override fun onResume() {
        super.onResume()
        contextProvider.attach(this)
    }

    open fun initUi() {
        // Override in subclasses
    }

    open fun initUx() {
        // Override in subclasses
    }

    open fun initViewModel() {
        // Override in subclasses
    }

    inline fun <T> LiveData<T>.observe(
        crossinline onChanged: (T) -> Unit,
    ) {
        observe(this@BaseActivity) { onChanged(it) }
    }
}