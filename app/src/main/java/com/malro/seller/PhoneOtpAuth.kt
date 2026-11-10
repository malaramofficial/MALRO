package com.malro.seller

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    fun read(context: Context): MalroSession? = try {
        val encoded = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ENTRY, null)
            ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        if (packed.size < 13) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        val json = JSONObject(String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8))
        MalroSession(json.getString("access"), json.getString("refresh"), json.getLong("expires"))
    } catch (_: Exception) { null }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(ENTRY).apply()
    }
}

private object MalroSellerApi {
    private fun request(path: String, body: JSONObject? = null, bearer: String? = null): JSONObject {
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Accept", "application/json")
            if (bearer != null) connection.setRequestProperty("Authorization", "Bearer $bearer")
            if (body != null) {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val error = try {
                    val json = JSONObject(response)
                    json.optString("msg").ifBlank { json.optString("message") }
                        .ifBlank { json.optString("error") }
                } catch (_: Exception) { "" }
                throw IllegalStateException(error.ifBlank { "सर्वर अनुरोध अस्वीकार हुआ ($code)" })
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally { connection.disconnect() }
    }

    suspend fun submitApplication(
        fullName: String, phone: String, shopName: String,
        description: String, address: String, locality: String
    ) = withContext(Dispatchers.IO) {
        request("/rest/v1/rpc/submit_seller_application", JSONObject()
            .put("p_full_name", fullName.trim())
            .put("p_phone", "+91" + phone.filter(Char::isDigit))
            .put("p_shop_name", shopName.trim())
            .put("p_description", description.trim())
            .put("p_address", address.trim())
            .put("p_locality", locality.trim().ifBlank { "Barmer" }))
    }

    suspend fun checkApplication(code: String, phone: String) = withContext(Dispatchers.IO) {
        request("/rest/v1/rpc/get_seller_application_status", JSONObject()
            .put("p_application_code", code.trim().uppercase())
            .put("p_phone", "+91" + phone.filter(Char::isDigit)))
    }

    suspend fun login(context: Context, sellerCode: String, password: String) = withContext(Dispatchers.IO) {
        val code = sellerCode.trim().uppercase()
        if (!code.matches(Regex("MLR-[A-F0-9]{10}"))) {
            throw IllegalArgumentException("सही Seller ID डालें (उदाहरण: MLR-XXXXXXXXXX)")
        }
        val email = "seller-${code.lowercase()}@malro.app"
        val json = request("/auth/v1/token?grant_type=password",
            JSONObject().put("email", email).put("password", password))
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) throw IllegalStateException("लॉगिन सेशन नहीं मिला")
        if (!hasSellerAccess(access)) throw IllegalStateException("यह Seller अकाउंट अभी सक्रिय नहीं है")
        EncryptedSessionStore.save(context, MalroSession(access, refresh,
            System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600L)))
    }

    private suspend fun hasSellerAccess(access: String) = withContext(Dispatchers.IO) {
        val user = request("/auth/v1/user", bearer = access)
        if (user.optJSONObject("app_metadata")?.optString("role") != "seller") return@withContext false
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
            JSONObject(profile).optString("account_role") == "seller"
        } finally { connection.disconnect() }
    }

    suspend fun restore(context: Context): Boolean = withContext(Dispatchers.IO) {
        val saved = EncryptedSessionStore.read(context) ?: return@withContext false
        try {
            val current = if (saved.expiresAt > System.currentTimeMillis() / 1000 + 60) saved else {
                val json = request("/auth/v1/token?grant_type=refresh_token",
                    JSONObject().put("refresh_token", saved.refreshToken))
                val access = json.optString("access_token")
                val refresh = json.optString("refresh_token")
                if (access.isBlank() || refresh.isBlank()) throw IllegalStateException("सेशन रिफ्रेश नहीं हुआ")
                MalroSession(access, refresh, System.currentTimeMillis() / 1000 +
                    json.optLong("expires_in", 3600L)).also { EncryptedSessionStore.save(context, it) }
            }
            if (!hasSellerAccess(current.accessToken)) throw IllegalStateException("Seller अनुमति उपलब्ध नहीं है")
            true
        } catch (_: Exception) {
            EncryptedSessionStore.clear(context)
            false
        }
    }
}

@Composable
fun MalroAuthGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var authenticated by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { authenticated = MalroSellerApi.restore(context) }
    when (authenticated) {
        null -> Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("MALRO सुरक्षित Seller सेशन जाँच रहा है…")
            }
        }
        true -> content()
        false -> SellerEntryScreen(onAuthenticated = { authenticated = true })
    }
}

@Composable
private fun SellerEntryScreen(onAuthenticated: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf("register") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var locality by remember { mutableStateOf("Barmer") }
    var sellerCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var statusCode by remember { mutableStateOf("") }
    var statusPhone by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var applicationCode by remember { mutableStateOf("") }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            verticalArrangement = Arrangement.Center) {
            Text("MALRO", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("विक्रेता पोर्टल", fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("दुकान का पंजीकरण और सुरक्षित Seller Login", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(18.dp))
            if (applicationCode.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("आवेदन सफलतापूर्वक भेजा गया", fontWeight = FontWeight.Bold)
                        Text("आपकी आवेदन ID", style = MaterialTheme.typography.labelMedium)
                        Text(applicationCode, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("इसे सुरक्षित रखें। Admin की मंजूरी के बाद इसी ID से Login होगा।")
                        TextButton(onClick = { mode = "status"; statusCode = applicationCode; statusPhone = phone }) {
                            Text("आवेदन की स्थिति देखें")
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == "register", onClick = { mode = "register"; message = ""; result = null }, label = { Text("नया पंजीकरण") })
                FilterChip(selected = mode == "login", onClick = { mode = "login"; message = ""; result = null }, label = { Text("Seller Login") })
                FilterChip(selected = mode == "status", onClick = { mode = "status"; message = ""; result = null }, label = { Text("स्थिति जाँचें") })
            }
            Spacer(Modifier.height(14.dp))
            when (mode) {
                "register" -> {
                    OutlinedTextField(fullName, { fullName = it }, Modifier.fillMaxWidth(), label = { Text("विक्रेता का पूरा नाम") }, singleLine = true)
                    OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, Modifier.fillMaxWidth(),
                        label = { Text("मोबाइल नंबर") }, prefix = { Text("+91 ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(shopName, { shopName = it }, Modifier.fillMaxWidth(), label = { Text("दुकान का नाम") }, singleLine = true)
                    OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("दुकान का विवरण (वैकल्पिक)") }, minLines = 2)
                    OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("दुकान का पूरा पता") }, minLines = 2)
                    OutlinedTextField(locality, { locality = it }, Modifier.fillMaxWidth(), label = { Text("क्षेत्र / शहर") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Text("ध्यान दें: आवेदन के समय OTP या Login नहीं होगा। मोबाइल नंबर संपर्क के लिए है; Admin इसकी पुष्टि कर सकता है।", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = {
                        scope.launch {
                            busy = true; message = ""
                            try {
                                if (fullName.trim().length < 2 || shopName.trim().length < 2 ||
                                    address.trim().length < 5 || phone.length != 10 || phone.first() !in "6789") {
                                    throw IllegalArgumentException("नाम, दुकान, पता और सही 10 अंकों का मोबाइल नंबर भरें")
                                }
                                val response = MalroSellerApi.submitApplication(fullName, phone, shopName, description, address, locality)
                                applicationCode = response.optString("application_code")
                                if (applicationCode.isBlank()) throw IllegalStateException("आवेदन ID नहीं मिली")
                                message = "आवेदन Admin को भेज दिया गया है।"
                            } catch (e: Exception) { message = e.message ?: "आवेदन भेजा नहीं जा सका" }
                            finally { busy = false }
                        }
                    }, Modifier.fillMaxWidth(), enabled = !busy) {
                        if (busy) CircularProgressIndicator() else Text("Admin को आवेदन भेजें")
                    }
                }
                "login" -> {
                    OutlinedTextField(sellerCode, { sellerCode = it.uppercase() }, Modifier.fillMaxWidth(),
                        label = { Text("Seller ID") }, placeholder = { Text("MLR-XXXXXXXXXX") }, singleLine = true)
                    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(),
                        label = { Text("पासवर्ड") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                    Button(onClick = {
                        scope.launch {
                            busy = true; message = ""
                            try {
                                if (password.length < 8) throw IllegalArgumentException("पासवर्ड सही भरें")
                                MalroSellerApi.login(context, sellerCode, password)
                                onAuthenticated()
                            } catch (e: Exception) { message = e.message ?: "Login विफल" }
                            finally { busy = false }
                        }
                    }, Modifier.fillMaxWidth(), enabled = !busy) {
                        if (busy) CircularProgressIndicator() else Text("सुरक्षित Login करें")
                    }
                }
                else -> {
                    OutlinedTextField(statusCode, { statusCode = it.uppercase() }, Modifier.fillMaxWidth(),
                        label = { Text("आवेदन ID") }, singleLine = true)
                    OutlinedTextField(statusPhone, { statusPhone = it.filter(Char::isDigit).take(10) }, Modifier.fillMaxWidth(),
                        label = { Text("आवेदन वाला मोबाइल नंबर") }, prefix = { Text("+91 ") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Button(onClick = {
                        scope.launch {
                            busy = true; message = ""; result = null
                            try {
                                if (statusPhone.length != 10) throw IllegalArgumentException("10 अंकों का मोबाइल नंबर भरें")
                                result = MalroSellerApi.checkApplication(statusCode, statusPhone)
                            } catch (e: Exception) { message = e.message ?: "स्थिति नहीं मिली" }
                            finally { busy = false }
                        }
                    }, Modifier.fillMaxWidth(), enabled = !busy) {
                        if (busy) CircularProgressIndicator() else Text("स्थिति जाँचें")
                    }
                    result?.let { r ->
                        val status = r.optString("status")
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(r.optString("shop_name"), fontWeight = FontWeight.Bold)
                                Text("स्थिति: " + when (status) {
                                    "pending" -> "Admin की मंजूरी बाकी है"
                                    "approved" -> "स्वीकृत — अब Seller Login करें"
                                    "rejected" -> "आवेदन अस्वीकृत"
                                    else -> status
                                })
                                if (r.optString("admin_note").isNotBlank()) Text("Admin नोट: ${r.optString("admin_note")}")
                                if (status == "approved") Text("अपनी आवेदन ID को Seller ID के रूप में उपयोग करें।")
                            }
                        }
                    }
                }
            }
            if (message.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(message, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
