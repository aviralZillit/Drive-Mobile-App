package com.zillit.drive.presentation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.zillit.drive.R
import com.zillit.drive.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        binding.bottomNavigation.setupWithNavController(navController)

        // Hide bottom nav on login screen
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNavigation.visibility = when (destination.id) {
                R.id.loginFragment -> android.view.View.GONE
                else -> android.view.View.VISIBLE
            }
        }

        // Create notification channels for push notifications
        createNotificationChannels()

        // Handle deep link from initial launch
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val scheme = uri.scheme ?: return

        if (scheme == "zillit-drive") {
            val pathSegments = uri.pathSegments
            val host = uri.host

            when (host) {
                "file" -> {
                    // zillit-drive://file/{fileId}
                    val fileId = pathSegments.firstOrNull() ?: return
                    navController.navigate(
                        R.id.fileDetailFragment,
                        bundleOf("fileId" to fileId)
                    )
                }
                "folder" -> {
                    // zillit-drive://folder/{folderId}
                    val folderId = pathSegments.firstOrNull() ?: return
                    navController.navigate(
                        R.id.homeFragment,
                        bundleOf("folderId" to folderId)
                    )
                }
            }
        }
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)

            val uploadChannel = android.app.NotificationChannel(
                CHANNEL_UPLOADS,
                "Drive Uploads",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for file upload progress"
            }

            val sharingChannel = android.app.NotificationChannel(
                CHANNEL_SHARING,
                "Drive Sharing",
                android.app.NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when files are shared with you"
            }

            notificationManager.createNotificationChannel(uploadChannel)
            notificationManager.createNotificationChannel(sharingChannel)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }

    companion object {
        const val CHANNEL_UPLOADS = "drive_uploads"
        const val CHANNEL_SHARING = "drive_sharing"
    }
}
