package com.malro.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Ink=Color(0xFF3B2345)
private val Gold=Color(0xFFD8B56D)
private val Paper=Color(0xFFF4F6F9)

private data class SellerApplication(
    val id:String,
    val code:String,
    val name:String,
    val phone:String,
    val shop:String,
    val address:String,
    val locality:String,
    val description:String
)

private data class SellerCredentials(val sellerId:String,val password:String)

private object AdminSellerApi {
    private fun request(path:String, token:String, body:JSONObject?=null):String {
        val connection=URL(BuildConfig.SUPABASE_URL+path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod=if(body==null) "GET" else "POST"
            connection.connectTimeout=15000
            connection.readTimeout=20000
            connection.setRequestProperty("apikey",BuildConfig.SUPABASE_ANON_KEY)
            connection.setRequestProperty("Authorization","Bearer $token")
            connection.setRequestProperty("Accept","application/json")
            if(body!=null){
                connection.setRequestProperty("Content-Type","application/json")
                connection.doOutput=true
                connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            }
            val code=connection.responseCode
            val response=(if(code in 200..299)connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use{it.readText()}.orEmpty()
            if(code !in 200..299){
                val message=try{val j=JSONObject(response);j.optString("message").ifBlank{j.optString("error")}.ifBlank{j.optString("msg")}}catch(_:Exception){""}
                throw IllegalStateException(message.ifBlank{"सर्वर त्रुटि ($code)"})
            }
            return response
        } finally { connection.disconnect() }
    }

    suspend fun pending(token:String):List<SellerApplication> = withContext(Dispatchers.IO) {
        val raw=request("/rest/v1/seller_applications?select=id,application_code,full_name,phone,shop_name,address,locality,description,status&status=eq.pending&order=submitted_at.desc",token)
        val array=JSONArray(raw)
        (0 until array.length()).map{ i ->
            val j=array.getJSONObject(i)
            SellerApplication(j.getString("id"),j.getString("application_code"),j.getString("full_name"),
                j.getString("phone"),j.getString("shop_name"),j.getString("address"),
                j.optString("locality"),j.optString("description"))
        }
    }

    suspend fun approve(token:String,applicationId:String):SellerCredentials = withContext(Dispatchers.IO) {
        val raw=request("/functions/v1/admin-approve-seller",token,JSONObject().put("application_id",applicationId))
        val j=JSONObject(raw)
        SellerCredentials(j.getString("seller_login_id"),j.getString("temporary_password"))
    }
}

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MalroAuthGate { MalroApp() }}}
}

@Composable private fun MalroApp(){
 var tab by remember{mutableIntStateOf(0)}
 MaterialTheme(colorScheme=lightColorScheme(primary=Ink,secondary=Gold,background=Paper)){
  Scaffold(containerColor=Paper,bottomBar={
   NavigationBar(containerColor=Color.White){
    listOf("डैशबोर्ड","विक्रेता","ऑर्डर").forEachIndexed{i,label->
     NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","▤","▣")[i],fontSize=18.sp)},label={Text(label)})
    }
   }
  }){padding->
   Column(Modifier.fillMaxSize().padding(padding)){
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp)){
     Text("MALRO",color=Ink,fontWeight=FontWeight.Bold,letterSpacing=3.sp,fontSize=13.sp)
     Text("MALRO Admin",color=Ink,fontWeight=FontWeight.Bold,fontSize=24.sp)
     Text("BARMER • LOCAL MARKETPLACE",color=Color.Gray,fontSize=10.sp)
     BackendConnectionStatus()
    }
    when(tab){
     0->Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
      Text("ऑपरेशन कंट्रोल",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Ink)
      Text("विक्रेता टैब में नए पंजीकरण देखें और वास्तविक Admin Approval दें।",color=Color.Gray)
      Card(colors=CardDefaults.cardColors(containerColor=Color.White)){
       Column(Modifier.fillMaxWidth().padding(16.dp)){
        Text("विक्रेता पंजीकरण",fontWeight=FontWeight.Bold,color=Ink)
        Text("आवेदन स्वीकार करने पर Seller ID और अस्थायी पासवर्ड बनेगा।",color=Color.Gray)
        TextButton(onClick={tab=1}){Text("आवेदन देखें")}
       }
      }
     }
     1->SellerApplicationsScreen()
     else->Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      Text("ऑर्डर",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink)
      Text("ऑर्डर निगरानी अगली इंटीग्रेशन स्टेज में जोड़ी जाएगी।",color=Color.Gray)
     }
    }
   }
  }
 }
}

@Composable private fun SellerApplicationsScreen(){
 val context= LocalContext.current
 val scope= rememberCoroutineScope()
 val applications= remember{ mutableStateListOf<SellerApplication>() }
 var loading by remember{mutableStateOf(false)}
 var message by remember{mutableStateOf("")}
 var credentials by remember{mutableStateOf<SellerCredentials?>(null)}
 var busyId by remember{mutableStateOf("")}

 suspend fun refresh(){
  loading=true;message=""
  try{
   val token=RoleAuth.currentAccessToken(context)?:throw IllegalStateException("Admin सेशन समाप्त हुआ। दोबारा Login करें।")
   applications.clear();applications.addAll(AdminSellerApi.pending(token))
   if(applications.isEmpty())message="अभी कोई नया लंबित आवेदन नहीं है।"
  }catch(e:Exception){message=e.message?: "आवेदन लोड नहीं हुए"}
  finally{loading=false}
 }
 LaunchedEffect(Unit){refresh()}

 Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){
   Column(Modifier.weight(1f)){
    Text("विक्रेता आवेदन",fontSize=23.sp,fontWeight=FontWeight.Bold,color=Ink)
    Text("${applications.size} लंबित आवेदन",fontSize=12.sp,color=Color.Gray)
   }
   OutlinedButton(onClick={scope.launch{refresh()}},enabled=!loading){Text("रिफ्रेश")}
  }
  if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
  if(message.isNotBlank())Text(message,color=Color.Gray,fontSize=13.sp)
  LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=18.dp)){
   items(applications,key={it.id}){app->
    Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(16.dp)){
     Column(Modifier.fillMaxWidth().padding(15.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
      Text(app.shop,fontWeight=FontWeight.Bold,fontSize=18.sp,color=Ink)
      Text("आवेदन ID: ${app.code}",fontWeight=FontWeight.SemiBold,color=Ink)
      Text("विक्रेता: ${app.name}  •  ${app.phone}",fontSize=13.sp)
      Text("पता: ${app.address}, ${app.locality}",fontSize=13.sp)
      if(app.description.isNotBlank())Text("विवरण: ${app.description}",fontSize=13.sp,color=Color.Gray)
      Text("मोबाइल नंबर OTP से सत्यापित नहीं है। मंजूरी से पहले संपर्क करके पुष्टि करें।",fontSize=11.sp,color=Color(0xFF9A5B00))
      Button(onClick={
       scope.launch{
        busyId=app.id;message=""
        try{
         val token=RoleAuth.currentAccessToken(context)?:throw IllegalStateException("Admin Login आवश्यक है")
         credentials=AdminSellerApi.approve(token,app.id)
         applications.removeAll{it.id==app.id}
        }catch(e:Exception){message=e.message?: "मंजूरी नहीं हो सकी"}
        finally{busyId=""}
       }
      },modifier=Modifier.fillMaxWidth(),enabled=busyId.isBlank()){
       if(busyId==app.id)CircularProgressIndicator() else Text("स्वीकृत करें और Seller ID बनाएँ")
      }
     }
    }
   }
  }
 }
 credentials?.let{c->
  AlertDialog(onDismissRequest={credentials=null},
   title={Text("दुकान स्वीकृत — Login तैयार")},
   text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
    Text("Seller को ये जानकारी सुरक्षित तरीके से दें। पासवर्ड दोबारा नहीं दिखेगा।")
    Text("Seller ID",fontWeight=FontWeight.Bold);Text(c.sellerId)
    Text("अस्थायी पासवर्ड",fontWeight=FontWeight.Bold);Text(c.password)
   }},
   confirmButton={TextButton(onClick={credentials=null}){Text("मैंने नोट कर लिया")}}
  )
 }
}

@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier){
 Card(modifier,colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(16.dp)){
  Column(Modifier.padding(16.dp)){Text(label,color=Color.Gray,fontSize=12.sp);Text(value,color=Ink,fontWeight=FontWeight.Bold,fontSize=25.sp)}
 }
}
