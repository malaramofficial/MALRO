package com.malro.seller

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

private val Ink=Color(0xFF173D3A)
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
    listOf("डैशबोर्ड","उत्पाद","ऑर्डर").forEachIndexed{i,label->
     NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("⌂","▤","▣")[i],fontSize=18.sp)},label={Text(label)})
    }
   }
  }){padding->
   Column(Modifier.fillMaxSize().padding(padding)){
    Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=12.dp)){
     Text("MALRO",color=Ink,fontWeight=FontWeight.Bold,letterSpacing=3.sp,fontSize=13.sp)
     Text("MALRO Seller",color=Ink,fontWeight=FontWeight.Bold,fontSize=24.sp)
     Text("BARMER • LOCAL MARKETPLACE",color=Color.Gray,fontSize=10.sp)
     BackendConnectionStatus()
    }
    when(tab){
     0->{var added by remember { mutableIntStateOf(3) }
 var stock by remember { mutableIntStateOf(41) }
 Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
  Text("आपकी दुकान",fontSize=26.sp,fontWeight=FontWeight.Bold,color=Ink)
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){Metric("उत्पाद",added.toString(),Modifier.weight(1f));Metric("कुल स्टॉक",stock.toString(),Modifier.weight(1f))}
  Button(onClick={added++;stock++},modifier=Modifier.fillMaxWidth()){Text("+ नया डेमो उत्पाद जोड़ें")}
  Text("उत्पाद सूची",fontWeight=FontWeight.Bold,color=Ink)
  listOf("बाजरे का आटा","मूंग दाल","देसी घी").forEach { p -> Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(p,Modifier.weight(1f),color=Ink);TextButton(onClick={stock=(stock-1).coerceAtLeast(0)}){Text("− स्टॉक")};TextButton(onClick={stock++}){Text("+")}}} }
  Text("ऑर्डर स्वीकृति और स्टॉक बदलाव अभी स्थानीय डेमो हैं। असली बिक्री के लिए विक्रेता सत्यापन व सर्वर API चाहिए।",fontSize=12.sp,color=Color.Gray)
 }}
     1->{Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("उत्पाद",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink);Text("इस मॉड्यूल का लाइव डेटा बैकएंड कनेक्शन के बाद दिखेगा।",color=Color.Gray)}}
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
