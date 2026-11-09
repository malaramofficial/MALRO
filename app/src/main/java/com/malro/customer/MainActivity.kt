package com.malro.customer

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

private val Ink=Color(0xFF0D2340)
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
    listOf("बाज़ार","कार्ट","ऑर्डर").forEachIndexed{i,label->
     NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","▤","▣")[i],fontSize=18.sp)},label={Text(label)})
    }
   }
  }){padding->
   Column(Modifier.fillMaxSize().padding(padding)){
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp)){
     Text("MALRO",color=Ink,fontWeight=FontWeight.Bold,letterSpacing=3.sp,fontSize=13.sp)
     Text("MALRO Customer",color=Ink,fontWeight=FontWeight.Bold,fontSize=24.sp)
     Text("BARMER • LOCAL MARKETPLACE",color=Color.Gray,fontSize=10.sp)
     BackendConnectionStatus()
    }
    when(tab){
     0->{var cart by remember { mutableIntStateOf(0) }
 val products=listOf("बाजरे का आटा • ₹55","मूंग दाल • ₹120","देसी घी • ₹650","मिट्टी का मटका • ₹180")
 Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
  Text("बाड़मेर का अपना बाज़ार",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink)
  Text("स्थानीय दुकानों से खरीदारी करें",color=Color.Gray)
  products.forEach { p -> Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(p,Modifier.weight(1f),color=Ink,fontWeight=FontWeight.Medium);Button(onClick={cart++}){Text("जोड़ें")}}} }
  Card(colors=CardDefaults.cardColors(containerColor=Ink)){Column(Modifier.padding(16.dp)){Text("कार्ट में $cart उत्पाद",color=Color.White,fontSize=18.sp);Button(onClick={tab=1},enabled=cart>0){Text("कार्ट देखें")}}}
  Text("डेमो उत्पाद हैं; वास्तविक कीमत, स्टॉक और भुगतान सर्वर से सत्यापित होंगे।",fontSize=12.sp,color=Color.Gray)
 }}
     1->{Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("कार्ट",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink);Text("इस मॉड्यूल का लाइव डेटा बैकएंड कनेक्शन के बाद दिखेगा।",color=Color.Gray)}}
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
