package com.malro.admin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
private val Navy=Color(0xFF3B2345); private val Gold=Color(0xFFD8B56D); private val Canvas=Color(0xFFF4F6F9)
class MainActivity:ComponentActivity(){override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MalroApp()}}}
@Composable private fun MalroApp(){
 var tab by remember{mutableIntStateOf(0)}
 MaterialTheme(colorScheme=lightColorScheme(primary=Navy,secondary=Gold,background=Canvas)){
 Scaffold(containerColor=Canvas,bottomBar={NavigationBar(containerColor=Color.White){listOf("होम","गतिविधि","प्रोफ़ाइल").forEachIndexed{i,s->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","◷","●")[i])},label={Text(s)})}}}){pad->
 when(tab){0->Home(Modifier.padding(pad));1->Column(Modifier.fillMaxSize().padding(pad).padding(24.dp)){Text("गतिविधि",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Navy);Text("लाइव सर्वर डेटा जुड़ने के बाद यहाँ दिखेगा।")};else->Column(Modifier.fillMaxSize().padding(pad).padding(24.dp)){Text("प्रोफ़ाइल",fontSize=28.sp,fontWeight=FontWeight.Bold,color=Navy);Text("MALRO Admin");Text("सुरक्षित लॉगिन अगली बैकएंड स्टेज में जुड़ेगा।")}}
 }}
}
@Composable private fun Home(modifier:Modifier=Modifier){
 LazyColumn(modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
 item{Text("MALRO",color=Navy,fontWeight=FontWeight.Bold,letterSpacing=3.sp);Text("प्रशासन नियंत्रण",color=Color.DarkGray);Text("MALRO Admin",fontSize=30.sp,fontWeight=FontWeight.Bold,color=Navy)}
 item{Card(colors=CardDefaults.cardColors(containerColor=Navy),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(22.dp)){Text("BARMER • LOCAL MARKETPLACE",color=Gold,fontSize=11.sp);Spacer(Modifier.height(10.dp));Text("विक्रेता, ऑर्डर, डिलीवरी और सहायता की निगरानी करें।",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.SemiBold);Spacer(Modifier.height(16.dp));Text("प्रारंभिक संस्करण 0.1.0",color=Color.White)}}}
 item{Text("मुख्य सुविधाएँ",fontSize=19.sp,fontWeight=FontWeight.Bold,color=Navy)}
 items(listOf("ऑपरेशन डैशबोर्ड","विक्रेता अनुमोदन","ऑर्डर और विवाद","डिलीवरी असाइनमेंट","रिपोर्ट और ऑडिट")){item->Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(16.dp)){Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(36.dp).background(Gold.copy(alpha=.2f),RoundedCornerShape(10.dp)),contentAlignment=Alignment.Center){Text("✓",color=Navy)};Spacer(Modifier.width(14.dp));Text(item,color=Navy,fontWeight=FontWeight.Medium)}}}
 item{Text("डेमो UI: वास्तविक डेटा, भुगतान और अनुमतियों के लिए सुरक्षित बैकएंड आवश्यक है।",fontSize=12.sp,color=Color.Gray)}
 }}
