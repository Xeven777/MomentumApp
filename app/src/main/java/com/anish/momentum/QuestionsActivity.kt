package com.anish.momentum

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.anish.momentum.databinding.ActivityQuestionsBinding
import com.anish.momentum.utils.Vibration

class QuestionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuestionsBinding

    /** A selectable option: the card the user taps and the row that gets highlighted. */
    private class Option(
        val card: View,
        val row: RelativeLayout,
        val label: TextView
    ) {
        val text: String get() = label.text.toString()

        fun setSelected(selected: Boolean) {
            val ctx = row.context
            row.setBackgroundColor(
                ContextCompat.getColor(ctx, if (selected) R.color.white else R.color.dark_grey)
            )
            label.setTextColor(
                ContextCompat.getColor(ctx, if (selected) R.color.black else R.color.white)
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityQuestionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Q1: multi-select, max 3
        val qn1 = listOf(
            option(binding.qn1a, binding.qn1aRl, binding.qn1aTv),
            option(binding.qn1b, binding.qn1bRl, binding.qn1bTv),
            option(binding.qn1c, binding.qn1cRl, binding.qn1cTv),
            option(binding.qn1d, binding.qn1dRl, binding.qn1dTv),
            option(binding.qn1e, binding.qn1eRl, binding.qn1eTv)
        )
        val qn1Selected = mutableSetOf<Option>()
        qn1.forEach { opt ->
            opt.card.setOnClickListener {
                Vibration.vibrate(this, 50)
                if (qn1Selected.contains(opt)) {
                    qn1Selected.remove(opt)
                    opt.setSelected(false)
                } else if (qn1Selected.size < 3) {
                    qn1Selected.add(opt)
                    opt.setSelected(true)
                }
            }
        }

        // Q2 / Q3 / Q5: single select
        val qn2 = listOf(
            option(binding.qn2a, binding.qn2aRl, binding.qn2aTv),
            option(binding.qn2b, binding.qn2bRl, binding.qn2bTv),
            option(binding.qn2c, binding.qn2cRl, binding.qn2cTv),
            option(binding.qn2d, binding.qn2dRl, binding.qn2dTv)
        )
        var qn2Selected: Option? = null
        wireSingleSelect(qn2) { qn2Selected = it }

        val qn3 = listOf(
            option(binding.qn3a, binding.qn3aRl, binding.qn3aTv),
            option(binding.qn3b, binding.qn3bRl, binding.qn3bTv),
            option(binding.qn3c, binding.qn3cRl, binding.qn3cTv),
            option(binding.qn3d, binding.qn3dRl, binding.qn3dTv)
        )
        var qn3Selected: Option? = null
        wireSingleSelect(qn3) { qn3Selected = it }

        // Q4: multi-select, no cap, but 'None' is exclusive
        val qn4None = option(binding.qn4e, binding.qn4eRl, binding.qn4eTv)
        val qn4 = listOf(
            option(binding.qn4a, binding.qn4aRl, binding.qn4aTv),
            option(binding.qn4b, binding.qn4bRl, binding.qn4bTv),
            option(binding.qn4c, binding.qn4cRl, binding.qn4cTv),
            option(binding.qn4d, binding.qn4dRl, binding.qn4dTv),
            qn4None
        )
        val qn4Selected = mutableSetOf<Option>()
        qn4.forEach { opt ->
            opt.card.setOnClickListener {
                Vibration.vibrate(this, 50)
                if (opt === qn4None) {
                    // 'None' wins: clear everything else
                    qn4Selected.clear()
                    qn4Selected.add(qn4None)
                    qn4.forEach { it.setSelected(it === qn4None) }
                } else {
                    // picking a habit clears 'None'
                    qn4Selected.remove(qn4None)
                    qn4None.setSelected(false)
                    if (qn4Selected.remove(opt)) {
                        opt.setSelected(false)
                    } else {
                        qn4Selected.add(opt)
                        opt.setSelected(true)
                    }
                }
            }
        }

        val qn5 = listOf(
            option(binding.qn5a, binding.qn5aRl, binding.qn5aTv),
            option(binding.qn5b, binding.qn5bRl, binding.qn5bTv),
            option(binding.qn5c, binding.qn5cRl, binding.qn5cTv)
        )
        var qn5Selected: Option? = null
        wireSingleSelect(qn5) { qn5Selected = it }

        binding.submitCard.setOnClickListener {
            Vibration.vibrate(this, 50)
            val anythingElse = binding.anythingElse.text?.toString() ?: ""
            val result = """
                |I want to ${qn1Selected.joinToString(", ") { it.text }}
                |I can allot ${qn2Selected?.text ?: ""} everyday
                |I prefer doing my habits during ${qn3Selected?.text ?: ""}
                |My bad habits are ${qn4Selected.joinToString(", ") { it.text }}
                |I am currently a ${qn5Selected?.text ?: ""}
                |$anythingElse
            """.trimMargin()
            Log.d("Qnresult", result)
            startActivity(
                Intent(this, AiActivity::class.java).putExtra("result", result)
            )
            finish()
        }
    }

    private fun option(card: View, row: RelativeLayout, label: TextView) =
        Option(card, row, label)

    private fun wireSingleSelect(options: List<Option>, onSelect: (Option) -> Unit) {
        options.forEach { opt ->
            opt.card.setOnClickListener {
                Vibration.vibrate(this, 50)
                options.forEach { it.setSelected(it === opt) }
                onSelect(opt)
            }
        }
    }
}
