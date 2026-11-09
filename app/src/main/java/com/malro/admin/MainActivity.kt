package com.malro.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink=Color(0xFF3B2345)
private val Gold=Color(0xFFD8B56D)
private val Paper=Color(0xFFF4F6F9)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MalroApp()}}
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
     0->{var approved by remember { mutableIntStateOf(1) }
 var cancelled by remember { mutableIntStateOf(0) }
 Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text("ऑपरेशन कंट्रोल",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Ink)
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("स्वीकृत दुकानें",approved.toString(),Modifier.weight(1f));Metric("रद्द ऑर्डर",cancelled.toString(),Modifier.weight(1f))}
  Text("विक्रेता अनुमोदन",fontWeight=FontWeight.Bold,color=Ink)
  listOf("राजस्थानी किराना","श्री बालाजी डेयरी").forEach { shop -> Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(shop,Modifier.weight(1f),color=Ink);Button(onClick={approved++}){Text("स्वीकृत करें")}}} }
  Text("ऑर्डर निगरानी",fontWeight=FontWeight.Bold,color=Ink)
  listOf("ऑर्डर #501 • ₹760","ऑर्डर #502 • ₹360").forEach { order -> Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(order,Modifier.weight(1f),color=Ink);TextButton(onClick={cancelled++}){Text("रद्द")}}} }
  Text("डेमो बदलाव केवल स्क्रीन पर हैं। वास्तविक एडमिन अधिकार सर्वर पर लागू होंगे।",fontSize=12.sp,color=Color.Gray)
 }}
     1->{Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("विक्रेता",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink);Text("इस मॉड्यूल का लाइव डेटा बैकएंड कनेक्शन के बाद दिखेगा।",color=Color.Gray)}}
     else->{Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("ऑर्डर",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink);Text("सुरक्षित लॉगिन और सर्वर डेटा अगली स्टेज में जुड़ेगा।",color=Color.Gray)}}
    }
   }
  }
 }
}
@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier){
 Card(modifier,colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(16.dp)){
  Column(Modifier.padding(16.dp)){Text(label,color=Color.Gray,fontSize=12.sp);Text(value,color=Ink,fontWeight=FontWeight.Bold,fontSize=25.sp)}
 }
}
