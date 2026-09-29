package com.anish.momentum

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.anish.momentum.utils.ServiceLocator
import com.anish.momentum.utils.Vibration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Entry point. A returning user is routed straight to the home screen; a first
 * run reveals the welcome splash instead.
 *
 * The welcome content lives in this activity rather than a second one so there
 * is no extra activity transition to flash through on a cold start.
 */
class LauncherActivity : AppCompatActivity() {

    private val settings get() = ServiceLocator.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_launcher)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        lifecycleScope.launch {
            if (settings.onboardingComplete.first()) {
                go(MainActivity::class.java)
            } else {
                showWelcome()
            }
        }
    }

    private fun showWelcome() {
        val content = findViewById<View>(R.id.welcome_content)
        content.alpha = 0f
        content.visibility = View.VISIBLE
        content.animate().alpha(1f).setDuration(WELCOME_FADE_MS).start()

        findViewById<View>(R.id.get_started_btn).setOnClickListener {
            Vibration.vibrate(this, 50)
            go(NameActivity::class.java)
        }
    }

    private fun go(target: Class<*>) {
        startActivity(Intent(this, target))
        finish()
    }

    private companion object {
        const val WELCOME_FADE_MS = 450L
    }
}
