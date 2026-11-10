package com.malro.seller

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private data class MalroSession(val accessToken: String, val refreshToken: String, val expiresAt: Long)

private object EncryptedSessionStore {
    private const val PREFS = "malro_secure_auth"
    private const val ENTRY = "session_v1"
    private const val KEY_ALIAS = "malro_auth_session_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun save(context: Context, session: MalroSession) {
        val plain = JSONObject().put("access", session.accessToken)
            .put("refresh", session.refreshToken).put("expires", session.expiresAt).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ENTRY, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun read(context: Context): MalroSession? {
        return try {
            val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ENTRY, null)
                ?: return null
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            if (packed.size < 13) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8))
            MalroSession(json.getString("access"), json.getString("refresh"), json.getLong("expires"))
        } catch (_: Exception) { null }
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ENTRY).apply()
    }
}

private object MalroPhoneAuth {
    private fun request(path: String, body: JSONObject? = null, bearer: String? = null): JSONObject {
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val error = try {
                    val json = JSONObject(response)
                    json.optString("msg").ifBlank { json.optString("message") }
                } catch (_: Exception) { "" }
                throw IllegalStateException(error.ifBlank { "सर्वर ने अनुरोध स्वीकार नहीं किया ($code)" })
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally { connection.disconnect() }
    }

    private suspend fun hasSellerAccess(access: String) = withContext(Dispatchers.IO) {
        val user = request("/auth/v1/user", bearer = access)
        val userId = user.optString("id")
        if (userId.isBlank()) return@withContext false
        val connection = URL(BuildConfig.SUPABASE_URL +
            "/rest/v1/profiles?id=eq.$userId&select=account_role").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization", "Bearer $access")
            connection.setRequestProperty("Accept", "application/vnd.pgrst.object+json")
            if (connection.responseCode !in 200..299) return@withContext false
            val profile = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(profile).optString("account_role") in setOf("seller_pending", "seller")
        } finally { connection.disconnect() }
    }

    suspend fun sendOtp(phone: String) = withContext(Dispatchers.IO) {
        request("/auth/v1/otp", JSONObject().put("phone", phone))
    }

    suspend fun verifyOtp(context: Context, phone: String, otp: String) = withContext(Dispatchers.IO) {
        val json = request("/auth/v1/verify", JSONObject()
            .put("type", "sms").put("phone", phone).put("token", otp))
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) throw IllegalStateException("लॉगिन सेशन नहीं मिला")
        if (!hasSellerAccess(access)) throw IllegalStateException("आपका विक्रेता खाता अभी सक्रिय नहीं है। पहले विक्रेता पंजीकरण पूरा करें।")
        EncryptedSessionStore.save(context, MalroSession(access, refresh,
            System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600L)))
    }

    suspend fun restore(context: Context): Boolean = withContext(Dispatchers.IO) {
        val saved = EncryptedSessionStore.read(context) ?: return@withContext false
        if (saved.expiresAt > System.currentTimeMillis() / 1000 + 60) {
            if (hasSellerAccess(saved.accessToken)) return@withContext true
            EncryptedSessionStore.clear(context)
            return@withContext false
        }
        try {
            val json = request("/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", saved.refreshToken))
            val access = json.optString("access_token")
            val refresh = json.optString("refresh_token")
            if (access.isBlank() || refresh.isBlank()) throw IllegalStateException("सेशन रिफ्रेश नहीं हुआ")
            EncryptedSessionStore.save(context, MalroSession(access, refresh,
                System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600L)))
            if (!hasSellerAccess(access)) throw IllegalStateException("विक्रेता अधिकार उपलब्ध नहीं हैं")
            true
        } catch (_: Exception) {
            EncryptedSessionStore.clear(context)
            false
        }
    }

    suspend fun signOut(context: Context) = withContext(Dispatchers.IO) {
        val saved = EncryptedSessionStore.read(context)
        try {
            if (saved != null) request("/auth/v1/logout", JSONObject(), saved.accessToken)
        } catch (_: Exception) { }
        EncryptedSessionStore.clear(context)
    }
}

@Composable
fun MalroAuthGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var authenticated by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { authenticated = MalroPhoneAuth.restore(context) }
    when (authenticated) {
        null -> Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("MALRO सुरक्षित सेशन जाँच रहा है…")
            }
        }
        true -> content()
        false -> PhoneOtpLogin(onAuthenticated = { authenticated = true })
    }
}

@Composable
private fun PhoneOtpLogin(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phoneDigits by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpSent by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text("MALRO", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text("मोबाइल नंबर से लॉगिन", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("आपके नंबर पर एक बार इस्तेमाल होने वाला OTP भेजा जाएगा।",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(value = phoneDigits,
                onValueChange = { phoneDigits = it.filter(Char::isDigit).take(10) },
                modifier = Modifier.fillMaxWidth(), label = { Text("मोबाइल नंबर") },
                prefix = { Text("+91 ") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !otpSent && !loading)
            if (otpSent) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = otp,
                    onValueChange = { otp = it.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("6 अंकों का OTP") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    enabled = !loading)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                scope.launch {
                    loading = true
                    message = ""
                    try {
                        if (!otpSent) {
                            if (phoneDigits.length != 10 || phoneDigits.first() !in "6789")
                                throw IllegalArgumentException("सही 10 अंकों का भारतीय मोबाइल नंबर डालें")
                            MalroPhoneAuth.sendOtp("+91$phoneDigits")
                            otpSent = true
                            message = "OTP भेजने का अनुरोध स्वीकार हुआ। SMS जाँचें।"
                        } else {
                            if (otp.length != 6) throw IllegalArgumentException("6 अंकों का OTP डालें")
                            MalroPhoneAuth.verifyOtp(context, "+91$phoneDigits", otp)
                            onAuthenticated()
                        }
                    } catch (e: Exception) {
                        message = e.message ?: "कुछ गलत हुआ। फिर कोशिश करें।"
                    } finally { loading = false }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !loading) {
                if (loading) CircularProgressIndicator() else
                    Text(if (otpSent) "OTP सत्यापित करें" else "OTP भेजें")
            }
            if (otpSent) {
                TextButton(onClick = { otpSent = false; otp = ""; message = "" }, enabled = !loading) {
                    Text("मोबाइल नंबर बदलें")
                }
            }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(message, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(18.dp))
            Text("SMS OTP चालू होने के लिए Supabase में SMS प्रदाता कॉन्फ़िगर होना आवश्यक है।",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
