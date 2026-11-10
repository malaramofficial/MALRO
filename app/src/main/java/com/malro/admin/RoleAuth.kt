package com.malro.admin

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

private const val REQUIRED_admin = "admin"
private data class AdminSession(val access: String, val refresh: String, val expires: Long)
private object AdminSessionStore {
    private const val PREFS="malro_role_session"
    private const val ENTRY="session"
    private const val ALIAS="malro_role_session_key"
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(ALIAS,null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    fun save(c:Context,s:AdminSession){
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val data=JSONObject().put("a",s.access).put("r",s.refresh).put("e",s.expires).toString().toByteArray()
        c.getSharedPreferences(PREFS,0).edit().putString(ENTRY,Base64.encodeToString(cipher.iv+cipher.doFinal(data),Base64.NO_WRAP)).apply()
    }
    fun read(c:Context):AdminSession? {
        return try {
            val raw=c.getSharedPreferences(PREFS,0).getString(ENTRY,null) ?: return null
            val packed=Base64.decode(raw,Base64.NO_WRAP)
            if(packed.size<13) return null
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,packed.copyOfRange(0,12)))
            val j=JSONObject(String(cipher.doFinal(packed.copyOfRange(12,packed.size))))
            AdminSession(j.getString("a"),j.getString("r"),j.getLong("e"))
        } catch(_:Exception) { null }
    }
    fun clear(c:Context){c.getSharedPreferences(PREFS,0).edit().remove(ENTRY).apply()}
}
private object RoleAuth {
    private fun request(path:String,body:JSONObject?=null,token:String?=null):JSONObject {
        val conn=URL(BuildConfig.SUPABASE_URL+path).openConnection() as HttpURLConnection
        try{
            conn.requestMethod=if(body==null)"GET" else "POST";conn.connectTimeout=15000;conn.readTimeout=15000
            conn.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY);conn.setRequestProperty("Accept","application/json")
            if(body!=null){conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json");conn.outputStream.use{it.write(body.toString().toByteArray())}}
            if(token!=null)conn.setRequestProperty("Authorization","Bearer $token")
            val code=conn.responseCode
            val response=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
            if(code !in 200..299){
                val msg=try{val j=JSONObject(response);j.optString("msg").ifBlank{j.optString("message")}}catch(_:Exception){""}
                throw IllegalStateException(msg.ifBlank{"सर्वर अनुरोध अस्वीकार हुआ ($code)"})
            }
            return if(response.isBlank())JSONObject() else JSONObject(response)
        }finally{conn.disconnect()}
    }
    private suspend fun hasRole(access:String)=withContext(Dispatchers.IO){
        val user=request("/auth/v1/user",token=access)
        user.optJSONObject("app_metadata")?.optString("role")==REQUIRED_admin
    }
    suspend fun send(phone:String)=withContext(Dispatchers.IO){request("/auth/v1/otp",JSONObject().put("phone",phone))}
    suspend fun verify(c:Context,phone:String,otp:String)=withContext(Dispatchers.IO){
        val j=request("/auth/v1/verify",JSONObject().put("type","sms").put("phone",phone).put("token",otp))
        val access=j.optString("access_token");val refresh=j.optString("refresh_token")
        if(access.isBlank()||refresh.isBlank())throw IllegalStateException("लॉगिन सेशन नहीं मिला")
        if(!hasRole(access))throw IllegalStateException("इस ऐप के लिए आपका $REQUIRED_admin अधिकार स्वीकृत नहीं है। व्यवस्थापक से संपर्क करें।")
        AdminSessionStore.save(c,AdminSession(access,refresh,System.currentTimeMillis()/1000+j.optLong("expires_in",3600)))
    }
    suspend fun restore(c:Context):Boolean=withContext(Dispatchers.IO){
        var s=AdminSessionStore.read(c)?:return@withContext false
        try{
            if(s.expires<System.currentTimeMillis()/1000+60){
                val j=request("/auth/v1/token?grant_type=refresh_token",JSONObject().put("refresh_token",s.refresh))
                s=AdminSession(j.optString("access_token"),j.optString("refresh_token"),
                    System.currentTimeMillis()/1000+j.optLong("expires_in",3600))
                if(s.access.isBlank()||s.refresh.isBlank())throw IllegalStateException("सेशन रिफ्रेश नहीं हुआ")
                AdminSessionStore.save(c,s)
            }
            if(!hasRole(s.access)){AdminSessionStore.clear(c);return@withContext false}
            true
        }catch(_:Exception){AdminSessionStore.clear(c);false}
    }
    suspend fun logout(c:Context)=withContext(Dispatchers.IO){
        val s=AdminSessionStore.read(c)
        try{if(s!=null)request("/auth/v1/logout",JSONObject(),s.access)}catch(_:Exception){}
        AdminSessionStore.clear(c)
    }
}
@Composable fun MalroAuthGate(content:@Composable()->Unit){
    val c=LocalContext.current;var ok by remember{mutableStateOf<Boolean?>(null)}
    LaunchedEffect(Unit){ok=RoleAuth.restore(c)}
    when(ok){
        null->Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){CircularProgressIndicator();Text("सुरक्षित अधिकार जाँच रहे हैं…")}}
        true->content()
        false->RoleLogin{ok=true}
    }
}
@Composable private fun RoleLogin(onSuccess:()->Unit){
    val c=LocalContext.current;val scope=rememberCoroutineScope()
    var phone by remember{mutableStateOf("")};var otp by remember{mutableStateOf("")}
    var sent by remember{mutableStateOf(false)};var busy by remember{mutableStateOf(false)};var msg by remember{mutableStateOf("")}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background){
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center){
            Text("MALRO",fontWeight=FontWeight.Bold,letterSpacing=3.sp)
            Spacer(Modifier.height(8.dp));Text("सुरक्षित $REQUIRED_admin लॉगिन",fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text("केवल सर्वर द्वारा स्वीकृत $REQUIRED_admin खाते को प्रवेश मिलेगा।",style=MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(value=phone,onValueChange={phone=it.filter(Char::isDigit).take(10)},modifier=Modifier.fillMaxWidth(),
                label={Text("मोबाइल नंबर")},prefix={Text("+91 ")},enabled=!sent&&!busy,singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
            if(sent){Spacer(Modifier.height(10.dp));OutlinedTextField(value=otp,onValueChange={otp=it.filter(Char::isDigit).take(6)},
                modifier=Modifier.fillMaxWidth(),label={Text("6 अंकों का OTP")},enabled=!busy,singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword))}
            Spacer(Modifier.height(14.dp))
            Button(onClick={scope.launch{busy=true;msg="";try{
                if(!sent){if(phone.length!=10||phone.first() !in "6789")throw IllegalArgumentException("सही 10 अंकों का मोबाइल नंबर डालें")
                    RoleAuth.send("+91$phone");sent=true;msg="OTP भेजने का अनुरोध स्वीकार हुआ।"}
                else{if(otp.length!=6)throw IllegalArgumentException("6 अंकों का OTP डालें");RoleAuth.verify(c,"+91$phone",otp);onSuccess()}
            }catch(e:Exception){msg=e.message?:"लॉगिन विफल"}finally{busy=false}}},modifier=Modifier.fillMaxWidth(),enabled=!busy){
                if(busy)CircularProgressIndicator() else Text(if(sent)"OTP सत्यापित करें" else "OTP भेजें")
            }
            if(sent)TextButton(onClick={sent=false;otp=""},enabled=!busy){Text("नंबर बदलें")}
            if(msg.isNotBlank()){Spacer(Modifier.height(8.dp));Text(msg,color=MaterialTheme.colorScheme.primary)}
            Spacer(Modifier.height(16.dp));Text("SMS OTP सेवा के लिए Supabase SMS प्रदाता कॉन्फ़िगर होना आवश्यक है।",style=MaterialTheme.typography.bodySmall)
        }
    }
}
