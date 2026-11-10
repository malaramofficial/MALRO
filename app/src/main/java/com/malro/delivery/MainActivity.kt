package com.malro.delivery

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

private val Ink=Color(0xFF3C2A1A)
private val Gold=Color(0xFFD8B56D)
private val Paper=Color(0xFFF4F6F9)

class MainActivity:ComponentActivity(){
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MalroAuthGate { MalroApp() }}}
}
@Composable private fun MalroApp(){
 var tab by remember{mutableIntStateOf(0)}
 MaterialTheme(colorScheme=lightColorScheme(primary=Ink,secondary=Gold,background=Paper)){
  Scaffold(containerColor=Paper,bottomBar={
   NavigationBar(containerColor=Color.White){
    listOf("होम","डिलीवरी","कमाई").forEachIndexed{i,label->
     NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","▤","▣")[i],fontSize=18.sp)},label={Text(label)})
    }
   }
  }){padding->
   Column(Modifier.fillMaxSize().padding(padding)){
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp)){
     Text("MALRO",color=Ink,fontWeight=FontWeight.Bold,letterSpacing=3.sp,fontSize=13.sp)
     Text("MALRO Delivery",color=Ink,fontWeight=FontWeight.Bold,fontSize=24.sp)
     Text("BARMER • LOCAL MARKETPLACE",color=Color.Gray,fontSize=10.sp)
     BackendConnectionStatus()
    }
    when(tab){
     0->{var online by remember { mutableStateOf(false) }
 var completed by remember { mutableIntStateOf(0) }
 Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text("डिलीवरी पार्टनर",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Ink)
  Card(colors=CardDefaults.cardColors(containerColor=Ink)){Column(Modifier.padding(18.dp)){Text(if(online)"आप ऑनलाइन हैं" else "आप ऑफलाइन हैं",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("लोकेशन तभी साझा करें जब आप उपलब्ध हों।",color=Color.White.copy(.8f),fontSize=12.sp)}}
  Row(verticalAlignment=Alignment.CenterVertically){Text("काम के लिए उपलब्ध",Modifier.weight(1f),color=Ink);Switch(checked=online,onCheckedChange={online=it})}
  Text("डेमो डिलीवरी जॉब",fontWeight=FontWeight.Bold,color=Ink)
  listOf("ऑर्डर #204 • मीठी बेरी रोड","ऑर्डर #205 • मुख्य बाज़ार").forEach { job -> Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(14.dp)){Text(job,color=Ink,fontWeight=FontWeight.Bold);Button(onClick={completed++},enabled=online){Text("डिलीवरी पूरी करें")}}} }
  Text("इस डिवाइस पर डेमो में पूरी की गई: $completed",color=Ink)
  Text("GPS और जॉब असाइनमेंट वास्तविक बैकएंड जुड़ने के बाद काम करेंगे।",fontSize=12.sp,color=Color.Gray)
 }}
     1->{Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("डिलीवरी",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink);Text("लाइव डेटा बैकएंड इंटीग्रेशन के बाद दिखेगा।",color=Color.Gray)}}
     else->{Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("कमाई",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink);Text("कमाई का रिकॉर्ड सर्वर से जोड़ा जाएगा।",color=Color.Gray)}}
    }
   }
  }
 }
}
