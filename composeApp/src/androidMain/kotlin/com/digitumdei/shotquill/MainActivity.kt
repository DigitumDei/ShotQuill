package com.digitumdei.shotquill

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.digitumdei.shotquill.shared.settings.AndroidLocalSettingsRepository
import com.digitumdei.shotquill.shared.storage.AndroidDatabaseDriverFactory
import com.digitumdei.shotquill.shared.storage.SqlDelightManualWorkflowRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = AndroidLocalSettingsRepository(this)
        val workflowRepository = SqlDelightManualWorkflowRepository(AndroidDatabaseDriverFactory(this).create())
        setContent {
            App(settingsRepository = settingsRepository, brandProfileRepository = workflowRepository)
        }
    }
}
