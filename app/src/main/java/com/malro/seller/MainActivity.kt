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
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setContent{MalroAuthGate { MalroApp() }}}
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
     0->Column(Modifier.fillMaxSize().padding(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
      Text("आपकी दुकान सक्रिय है",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink)
      Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){
       Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Admin Approval सफल",fontWeight=FontWeight.Bold,color=Ink,fontSize=18.sp)
        Text("आपका Seller अकाउंट MALRO में सक्रिय कर दिया गया है।",color=Color.Gray)
        Text("अब आप अपने Seller ID और पासवर्ड से सुरक्षित Login कर सकते हैं।",color=Color.Gray)
       }
      }
      Text("अगला चरण",fontWeight=FontWeight.Bold,color=Ink)
      Card(colors=CardDefaults.cardColors(containerColor=Color.White),shape=RoundedCornerShape(18.dp)){
       Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        Text("उत्पाद और ऑर्डर प्रबंधन",fontWeight=FontWeight.Bold,color=Ink)
        Text("दुकान में उत्पाद जोड़ना, कीमत/स्टॉक बदलना और ग्राहक के ऑर्डर संभालना अगले बैकएंड इंटीग्रेशन चरण में जोड़ा जाएगा।",color=Color.Gray,fontSize=13.sp)
       }
      }
     }
     1->Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      Text("उत्पाद",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink)
      Text("लाइव उत्पाद जोड़ना और स्टॉक अपडेट अगली इंटीग्रेशन स्टेज में आएगा।",color=Color.Gray)
     }
     else->Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
      Text("ऑर्डर",fontSize=25.sp,fontWeight=FontWeight.Bold,color=Ink)
      Text("लाइव ग्राहक ऑर्डर अगली इंटीग्रेशन स्टेज में आएँगे।",color=Color.Gray)
     }
    }
   }
  }
 }
}
