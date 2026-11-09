package com.malro.delivery

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

private val Navy = Color(0xFF3C2A1A)
private val Gold = Color(0xFFD8B56D)
private val Canvas = Color(0xFFF4F6F9)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MalroApp() }
    }
}

@Composable
private fun MalroApp() {
    var selected by remember { mutableIntStateOf(0) }
    val sections = listOf("होम", "गतिविधि", "प्रोफ़ाइल")
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Gold, background = Canvas)) {
        Scaffold(containerColor = Canvas, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                sections.forEachIndexed { index, label ->
                    NavigationBarItem(selected = selected == index, onClick = { selected = index },
                        icon = { Text(listOf("⌂", "◷", "●")[index], fontSize = 18.sp) }, label = { Text(label) })
                }
            }
        }) { padding ->
            when (selected) {
                0 -> HomeScreen(Modifier.padding(padding))
                1 -> ActivityScreen(Modifier.padding(padding))
                else -> ProfileScreen(Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier) {
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text("MALRO", color = Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Spacer(Modifier.height(8.dp))
            Text("डिलीवरी पार्टनर", color = Color.DarkGray, fontSize = 14.sp)
            Text("MALRO Delivery", color = Navy, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp)) {
                    Text("BARMER • LOCAL MARKETPLACE", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("डिलीवरी जॉब देखें, पिकअप करें और सुरक्षित रूप से ऑर्डर पहुँचाएं।", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(18.dp))
                    Text("शुरुआती संस्करण 0.1.0", color = Color.White.copy(alpha = .75f), fontSize = 12.sp)
                }
            }
        }
        item { Text("मुख्य सुविधाएँ", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        items(listOf("ऑनलाइन / ऑफलाइन", "असाइन किए गए ऑर्डर", "पिकअप पुष्टि", "OTP डिलीवरी", "कमाई और इतिहास")) { feature ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).background(Gold.copy(alpha = .18f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("✓", color = Navy, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(feature, color = Navy, fontWeight = FontWeight.Medium)
                }
            }
        }
        item {
            Text("यह शुरुआती UI है। वास्तविक लॉगिन, भुगतान, लाइव डेटा और सर्वर अनुमतियाँ बैकएंड जुड़ने के बाद सक्रिय होंगी।",
                color = Color(0xFF6B7280), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActivityScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("गतिविधि", color = Navy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("अभी कोई लाइव रिकॉर्ड उपलब्ध नहीं है।", color = Color.DarkGray)
        Text("यह स्क्रीन सर्वर API जुड़ने पर वास्तविक डेटा दिखाएगी।", color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable
private fun ProfileScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("प्रोफ़ाइल", color = Navy, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("MALRO Delivery", color = Color.DarkGray)
        Text("साइन-इन और खाता सत्यापन सुरक्षित बैकएंड स्टेज में जोड़े जाएंगे।", color = Color.Gray, fontSize = 13.sp)
    }
}
