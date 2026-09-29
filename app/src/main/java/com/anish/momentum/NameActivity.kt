package com.anish.momentum

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.anish.momentum.databinding.ActivityNameBinding
import com.anish.momentum.utils.ServiceLocator
import com.anish.momentum.utils.Vibration
import kotlinx.coroutines.launch

/**
 * Second onboarding step: the name used for the home-screen greeting.
 *
 * "Skip for now" still marks onboarding complete, so the flow never shows
 * twice — the name can be set later from Settings.
 */
class NameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNameBinding

    private val settings get() = ServiceLocator.settings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // The root carries the screen's horizontal padding in XML. setPadding
        // below must ADD the system-bar insets to it, not replace it —
        // replacing it zeroes the side padding on gesture-nav devices and the
        // content sticks to the edges.
        val sidePadding = binding.main.paddingStart
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                sidePadding + systemBars.left,
                systemBars.top,
                sidePadding + systemBars.right,
                systemBars.bottom
            )
            insets
        }

        binding.continueBtn.setOnClickListener { submit() }

        binding.skipBtn.setOnClickListener {
            Vibration.vibrate(this, 50)
            finishOnboarding(name = null)
        }

        binding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
    }

    private fun submit() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            // An inline error keeps the fix next to the field it belongs to.
            binding.nameInputLayout.error = getString(R.string.name_error_required)
            return
        }
        binding.nameInputLayout.error = null
        Vibration.vibrate(this, 50)
        finishOnboarding(name = name)
    }

    private fun finishOnboarding(name: String?) {
        lifecycleScope.launch {
            if (name != null) settings.setUserName(name)
            settings.setOnboardingComplete(true)
            startActivity(Intent(this@NameActivity, MainActivity::class.java))
            finish()
        }
    }
}
