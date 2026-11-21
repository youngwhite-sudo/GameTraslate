package com.youngwhite.gametranslate

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.youngwhite.gametranslate.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            saveCurrentSettings()
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra("resultCode", result.resultCode)
                putExtra("data", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            binding.tvStatus.text = "Estado: GameTranslate activo. Ya puedes abrir tu juego."
        } else {
            Toast.makeText(this, "Se necesita permiso de captura de pantalla.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.seekFontScale.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val pct = 50 + progress
                binding.tvFontScaleLabel.text = "Tamaño de letra: $pct%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnStartService.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                Toast.makeText(this, "Activa 'Mostrar sobre otras apps' y vuelve a presionar Iniciar.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
        }

        binding.btnStopService.setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            binding.tvStatus.text = "Estado: Servicio detenido"
        }
    }

    private fun saveCurrentSettings() {
        val lang = when {
            binding.rbJapanese.isChecked -> PrefsManager.LANG_JA
            binding.rbChinese.isChecked -> PrefsManager.LANG_ZH
            else -> PrefsManager.LANG_EN
        }
        val font = when {
            binding.rbFontRetro.isChecked -> PrefsManager.FONT_RETRO
            binding.rbFontSerif.isChecked -> PrefsManager.FONT_SERIF
            else -> PrefsManager.FONT_MODERN
        }
        val scale = (50 + binding.seekFontScale.progress) / 100f

        PrefsManager.saveSettings(
            context = this,
            sourceLang = lang,
            isFullscreen = binding.rbFullscreen.isChecked,
            fontStyle = font,
            fontScale = scale,
            camouflageColors = binding.swCamouflage.isChecked,
            autoMode = binding.swAutoMode.isChecked
        )
    }
}
