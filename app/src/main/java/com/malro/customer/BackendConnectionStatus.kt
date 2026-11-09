package com.malro.customer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private suspend fun checkMalroBackend(): String = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        val endpoint = URL("${BuildConfig.SUPABASE_URL}/rest/v1/shops?select=id&status=eq.approved&limit=1")
        connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            setRequestProperty("Accept", "application/json")
        }
        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            "MALRO डेटाबेस कनेक्शन सक्रिय"
        } else {
            "डेटाबेस जवाब: ${connection.responseCode}"
        }
    } catch (_: Exception) {
        "डेटाबेस से कनेक्शन नहीं हो पाया"
    } finally {
        connection?.disconnect()
    }
}

@Composable
fun BackendConnectionStatus(modifier: Modifier = Modifier) {
    var status by remember { mutableStateOf("MALRO डेटाबेस से जुड़ रहे हैं…") }
    LaunchedEffect(Unit) { status = checkMalroBackend() }
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(status, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}
