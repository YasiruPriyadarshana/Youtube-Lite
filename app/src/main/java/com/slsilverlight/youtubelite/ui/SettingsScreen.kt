package com.slsilverlight.youtubelite.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.slsilverlight.youtubelite.data.AppSettings

@Composable
fun SettingsScreen(
    settings: AppSettings,
    signedIn: Boolean,
    onShowShortsChange: (Boolean) -> Unit,
    onShowHomeChange: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ListItem(
            headlineContent = { Text("Show Shorts") },
            supportingContent = { Text("Include Shorts in your feeds") },
            trailingContent = { Switch(checked = settings.showShorts, onCheckedChange = onShowShortsChange) },
        )
        ListItem(
            headlineContent = { Text("Show Home tab") },
            supportingContent = { Text("Trending videos for your country. YouTube doesn't share your personal recommendations with apps.") },
            trailingContent = { Switch(checked = settings.showHome, onCheckedChange = onShowHomeChange) },
        )
        if (signedIn) {
            HorizontalDivider()
            TextButton(onClick = onSignOut, modifier = Modifier.padding(8.dp)) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
