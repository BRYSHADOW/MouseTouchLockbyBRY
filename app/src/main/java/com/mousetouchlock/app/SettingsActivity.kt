package com.mousetouchlock.app

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.mousetouchlock.app.databinding.ActivitySettingsBinding
import com.mousetouchlock.app.service.FloatingButtonService
import com.mousetouchlock.app.util.PreferenceHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferenceHelper

    // SeekBar ranges
    private val SIZE_MIN   = 40      // dp
    private val SIZE_MAX   = 90      // dp  → seekbar max = 50
    private val ALPHA_MIN  = 30      // %
    private val ALPHA_MAX  = 100     // %  → seekbar max = 70

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceHelper(this)

        setupToolbar()
        setupAutoStartSwitch()
        setupSizeSeekBar()
        setupAlphaSeekBar()
        setupPositionReset()
    }

    // ── Toolbar ─────────────────────────────────────────────────────────────
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(false)
        }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    // ── Auto-start switch ────────────────────────────────────────────────────
    private fun setupAutoStartSwitch() {
        binding.switchAutoStart.isChecked = prefs.autoStart
        binding.switchAutoStart.setOnCheckedChangeListener { _, checked ->
            prefs.autoStart = checked
        }
    }

    // ── Button size SeekBar ─────────────────────────────────────────────────
    private fun setupSizeSeekBar() {
        val current = prefs.buttonSize.coerceIn(SIZE_MIN, SIZE_MAX)
        binding.seekSize.max      = SIZE_MAX - SIZE_MIN
        binding.seekSize.progress = current - SIZE_MIN
        binding.tvSizeValue.text  = "${current}dp"

        binding.seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val sizeDp = progress + SIZE_MIN
                binding.tvSizeValue.text = "${sizeDp}dp"
                if (fromUser) {
                    FloatingButtonService.instance?.applyButtonSize(sizeDp)
                        ?: run { prefs.buttonSize = sizeDp }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ── Button opacity SeekBar ──────────────────────────────────────────────
    private fun setupAlphaSeekBar() {
        val currentPct = (prefs.buttonAlpha * 100).toInt().coerceIn(ALPHA_MIN, ALPHA_MAX)
        binding.seekAlpha.max      = ALPHA_MAX - ALPHA_MIN
        binding.seekAlpha.progress = currentPct - ALPHA_MIN
        binding.tvAlphaValue.text  = "${currentPct}%"

        binding.seekAlpha.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                val pct   = progress + ALPHA_MIN
                val alpha = pct / 100f
                binding.tvAlphaValue.text = "${pct}%"
                if (fromUser) {
                    FloatingButtonService.instance?.applyButtonAlpha(alpha)
                        ?: run { prefs.buttonAlpha = alpha }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    // ── Reset position ──────────────────────────────────────────────────────
    private fun setupPositionReset() {
        val posX = prefs.buttonX
        val posY = prefs.buttonY
        binding.tvPositionValue.text =
            if (posX < 0 || posY < 0) getString(R.string.pos_default)
            else "X: ${posX}px  Y: ${posY}px"

        binding.btnResetPosition.setOnClickListener {
            FloatingButtonService.instance?.resetButtonPosition()
                ?: run { prefs.resetPosition() }
            binding.tvPositionValue.text = getString(R.string.pos_default)
        }
    }
}
