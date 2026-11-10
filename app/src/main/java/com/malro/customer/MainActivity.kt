package com.malro.customer

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF102743)
private val Gold = Color(0xFFD8B56D)
private val Paper = Color(0xFFF4F6F9)
private val Muted = Color(0xFF667085)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MalroAuthGate { MalroApp() } }
    }
}

private fun money(paise: Long): String =
    "₹" + NumberFormat.getNumberInstance(Locale("en", "IN")).format(paise / 100.0)

@Composable
private fun MalroApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var userId by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("") }
    var nameInput by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<CustomerProduct>>(emptyList()) }
    var orders by remember { mutableStateOf<List<CustomerOrder>>(emptyList()) }
    var cart by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var cartRestored by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("सभी") }
    var addressLine by remember { mutableStateOf("") }
    var locality by remember { mutableStateOf("Barmer") }
    var pincode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            val user = MalroPhoneAuth.currentUser(context)
            userId = user.optString("id")
            phone = user.optString("phone")
            val profile = CustomerBackend.loadProfile(context, userId, phone)
            profileName = profile.fullName
            nameInput = profile.fullName
            if (profile.phone.isNotBlank()) phone = profile.phone
            products = CustomerBackend.loadProducts(context)
            orders = CustomerBackend.loadOrders(context, userId)
            val saved = context.getSharedPreferences("malro_customer", 0).getString("cart", "{}")
            val json = JSONObject(saved ?: "{}")
            val restored = mutableMapOf<String, Int>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val quantity = json.optInt(key)
                if (quantity > 0) restored[key] = quantity
            }
            cart = restored
            cartRestored = true
        } catch (e: Exception) {
            message = e.message ?: "डेटा लोड नहीं हो पाया"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(cart, cartRestored) {
        if (cartRestored) {
            val json = JSONObject()
            cart.forEach { (id, quantity) -> json.put(id, quantity) }
            context.getSharedPreferences("malro_customer", 0).edit().putString("cart", json.toString()).apply()
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(primary = Ink, secondary = Gold, background = Paper, surface = Color.White, onPrimary = Color.White),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(22.dp))
    ) {
        Scaffold(
            containerColor = Paper,
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    val labels = listOf("बाज़ार", "कार्ट", "ऑर्डर", "खाता")
                    val icons = listOf("⌂", "▤", "▣", "●")
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = tab == index, onClick = { tab = index; message = "" },
                            icon = { Text(icons[index], fontSize = 19.sp, fontWeight = FontWeight.Bold) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MALRO", color = Ink, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 13.sp)
                            Text("बाड़मेर का अपना बाज़ार", color = Ink, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                        Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
                            Text("स्थानीय", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                    }
                }
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Ink) }
                } else {
                    when (tab) {
                        0 -> HomeScreen(
                            products, search, { search = it }, category, { category = it },
                            onAdd = { product ->
                                val currentShop = cart.keys.firstOrNull()?.let { id -> products.firstOrNull { it.id == id }?.shopId }
                                if (currentShop != null && currentShop != product.shopId) {
                                    message = "एक बार में एक ही दुकान से ऑर्डर करें। पहले कार्ट पूरा करें।"
                                } else {
                                    cart = cart + (product.id to ((cart[product.id] ?: 0) + 1))
                                    message = product.name + " कार्ट में जुड़ गया"
                                }
                            },
                            onRefresh = {
                                scope.launch {
                                    busy = true
                                    try { products = CustomerBackend.loadProducts(context); message = "उत्पाद सूची अपडेट हो गई" }
                                    catch (e: Exception) { message = e.message ?: "अपडेट नहीं हो पाया" }
                                    finally { busy = false }
                                }
                            }
                        )
                        1 -> CartScreen(
                            products, cart, profileName.ifBlank { nameInput }, phone,
                            addressLine, { addressLine = it }, locality, { locality = it },
                            pincode, { pincode = it }, busy,
                            onQuantity = { id, quantity -> cart = if (quantity <= 0) cart - id else cart + (id to quantity) },
                            onCheckout = {
                                val lines = cart.mapNotNull { (id, quantity) -> products.firstOrNull { it.id == id }?.let { it to quantity } }
                                val shopId = lines.firstOrNull()?.first?.shopId.orEmpty()
                                scope.launch {
                                    busy = true
                                    message = ""
                                    try {
                                        if (lines.isEmpty()) throw IllegalStateException("कार्ट में उपलब्ध उत्पाद नहीं हैं")
                                        if (lines.any { it.first.shopId != shopId }) throw IllegalStateException("एक ऑर्डर में एक ही दुकान के उत्पाद रखें")
                                        if (nameInput.trim().length < 2) throw IllegalStateException("खाते में अपना नाम भरें")
                                        if (addressLine.trim().length < 4 || locality.trim().length < 2 || !pincode.matches(Regex("[0-9]{6}")))
                                            throw IllegalStateException("पूरा पता और 6 अंकों का पिनकोड भरें")
                                        if (!phone.filter(Char::isDigit).takeLast(10).matches(Regex("[6-9][0-9]{9}")))
                                            throw IllegalStateException("खाते का मोबाइल नंबर जाँचें")
                                        val quantities = lines.associate { it.first.id to it.second }
                                        val orderId = CustomerBackend.placeOrder(context, shopId, nameInput, phone, addressLine, locality, pincode, quantities)
                                        cart = emptyMap()
                                        orders = CustomerBackend.loadOrders(context, userId)
                                        message = "ऑर्डर सफलतापूर्वक दर्ज हुआ • " + orderId.take(8)
                                        tab = 2
                                    } catch (e: Exception) {
                                        message = e.message ?: "ऑर्डर दर्ज नहीं हो पाया"
                                        try { products = CustomerBackend.loadProducts(context) } catch (_: Exception) { }
                                    } finally { busy = false }
                                }
                            }
                        )
                        2 -> OrdersScreen(
                            orders = orders, busy = busy,
                            onRefresh = {
                                scope.launch {
                                    busy = true
                                    try { orders = CustomerBackend.loadOrders(context, userId); message = "ऑर्डर अपडेट हो गए" }
                                    catch (e: Exception) { message = e.message ?: "ऑर्डर लोड नहीं हुए" }
                                    finally { busy = false }
                                }
                            },
                            onCancel = { orderId ->
                                scope.launch {
                                    busy = true
                                    try {
                                        CustomerBackend.cancelOrder(context, orderId)
                                        orders = CustomerBackend.loadOrders(context, userId)
                                        products = CustomerBackend.loadProducts(context)
                                        message = "ऑर्डर रद्द हो गया और स्टॉक वापस जोड़ दिया गया"
                                    } catch (e: Exception) { message = e.message ?: "ऑर्डर रद्द नहीं हो पाया" }
                                    finally { busy = false }
                                }
                            }
                        )
                        else -> AccountScreen(
                            nameInput, { nameInput = it }, phone, busy,
                            onSave = {
                                scope.launch {
                                    busy = true
                                    try {
                                        if (nameInput.trim().length < 2) throw IllegalStateException("कम से कम 2 अक्षरों का नाम भरें")
                                        val profile = CustomerBackend.updateProfile(context, userId, nameInput)
                                        profileName = profile.fullName
                                        message = "प्रोफाइल सेव हो गई"
                                    } catch (e: Exception) { message = e.message ?: "सेव नहीं हुआ" }
                                    finally { busy = false }
                                }
                            },
                            onSignOut = {
                                scope.launch {
                                    busy = true
                                    MalroPhoneAuth.signOut(context)
                                    (context as? Activity)?.recreate()
                                }
                            }
                        )
                    }
                }
                if (message.isNotBlank()) {
                    Surface(color = Color(0xFFE8EEF5), modifier = Modifier.fillMaxWidth()) {
                        Text(message, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp), color = Ink, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    products: List<CustomerProduct>, search: String, onSearch: (String) -> Unit,
    category: String, onCategory: (String) -> Unit, onAdd: (CustomerProduct) -> Unit, onRefresh: () -> Unit
) {
    val categories = listOf("सभी") + products.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    val filtered = products.filter {
        (category == "सभी" || it.category == category) &&
            (search.isBlank() || it.name.contains(search, true) || it.category.contains(search, true) || it.shopName.contains(search, true))
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text("अपने आसपास की दुकानों से खरीदें", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
                label = { Text("उत्पाद या दुकान खोजें") }, singleLine = true)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.take(4).forEach { item ->
                    FilterChip(selected = category == item, onClick = { onCategory(item) }, label = { Text(item) })
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("उपलब्ध उत्पाद", modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                TextButton(onClick = onRefresh) { Text("रीफ्रेश") }
            }
        }
        if (filtered.isEmpty()) {
            EmptyState(
                if (products.isEmpty()) "अभी उत्पाद उपलब्ध नहीं हैं" else "कोई उत्पाद नहीं मिला",
                if (products.isEmpty()) "जब स्वीकृत स्थानीय दुकानें उत्पाद जोड़ेंगी, वे यहाँ दिखाई देंगे।" else "दूसरा नाम या श्रेणी खोजकर देखें।"
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { product ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(product.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    Text(product.shopName + " • " + product.locality, color = Muted, fontSize = 12.sp)
                                }
                                Text(money(product.pricePaise), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            }
                            if (product.description.isNotBlank()) {
                                Spacer(Modifier.height(5.dp))
                                Text(product.description, color = Muted, fontSize = 13.sp)
                            }
                            Spacer(Modifier.height(9.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(product.category + " • स्टॉक " + product.stock, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Button(onClick = { onAdd(product) }) { Text("कार्ट में जोड़ें") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CartScreen(
    products: List<CustomerProduct>, cart: Map<String, Int>, name: String, phone: String,
    addressLine: String, onAddress: (String) -> Unit, locality: String, onLocality: (String) -> Unit,
    pincode: String, onPincode: (String) -> Unit, busy: Boolean,
    onQuantity: (String, Int) -> Unit, onCheckout: () -> Unit
) {
    val lines = cart.mapNotNull { (id, quantity) -> products.firstOrNull { it.id == id }?.let { it to quantity } }
    val total = lines.sumOf { it.first.pricePaise * it.second }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("आपका कार्ट", color = Ink, fontWeight = FontWeight.Bold, fontSize = 23.sp) }
        if (lines.isEmpty()) {
            item { EmptyState("कार्ट खाली है", "बाज़ार से उत्पाद जोड़ें, वे यहाँ दिखाई देंगे।") }
        } else {
            items(lines, key = { it.first.id }) { line ->
                val product = line.first
                val quantity = line.second
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, color = Ink, fontWeight = FontWeight.SemiBold)
                            Text(money(product.pricePaise) + " • " + product.shopName, color = Muted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onQuantity(product.id, quantity - 1) }) { Text("−") }
                        Text(quantity.toString(), color = Ink, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onQuantity(product.id, minOf(quantity + 1, product.stock)) }) { Text("+") }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("डिलीवरी पता", color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("नाम: " + name, color = Muted, fontSize = 13.sp)
                        Text("मोबाइल: " + phone, color = Muted, fontSize = 13.sp)
                        OutlinedTextField(value = addressLine, onValueChange = onAddress, modifier = Modifier.fillMaxWidth(),
                            label = { Text("मकान / गली / गाँव") }, minLines = 2)
                        OutlinedTextField(value = locality, onValueChange = onLocality, modifier = Modifier.fillMaxWidth(),
                            label = { Text("क्षेत्र / कस्बा") }, singleLine = true)
                        OutlinedTextField(value = pincode, onValueChange = { onPincode(it.filter(Char::isDigit).take(6)) },
                            modifier = Modifier.fillMaxWidth(), label = { Text("6 अंकों का पिनकोड") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        Divider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("कुल राशि", color = Ink, fontWeight = FontWeight.Bold)
                            Text(money(total), color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        }
                        Text("भुगतान: डिलीवरी पर नकद (COD) • डिलीवरी शुल्क अभी ₹0", color = Muted, fontSize = 12.sp)
                        Button(onClick = onCheckout, enabled = !busy && lines.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("ऑर्डर कन्फर्म करें")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrdersScreen(orders: List<CustomerOrder>, busy: Boolean, onRefresh: () -> Unit, onCancel: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("मेरे ऑर्डर", modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold, fontSize = 23.sp)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("रीफ्रेश") }
        }
        if (orders.isEmpty()) {
            EmptyState("अभी कोई ऑर्डर नहीं", "आपका पहला ऑर्डर यहाँ दिखाई देगा।")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(orders, key = { it.id }) { order ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(order.shopName, modifier = Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Bold)
                                Text(statusText(order.status), color = Color(0xFF176B48), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                            Text(order.items.joinToString(" • ").ifBlank { "ऑर्डर विवरण" }, color = Muted, fontSize = 13.sp)
                            Text(order.deliveryAddress, color = Muted, fontSize = 12.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(order.createdAt.substringBefore("T"), color = Muted, fontSize = 12.sp)
                                Text(money(order.totalPaise), color = Ink, fontWeight = FontWeight.Bold)
                            }
                            Text("भुगतान: " + if (order.paymentStatus == "paid") "भुगतान हो गया" else "डिलीवरी पर नकद / लंबित",
                                color = Muted, fontSize = 12.sp)
                            Text("ऑर्डर आईडी: " + order.id.take(8), color = Muted, fontSize = 10.sp)
                            if (order.status == "placed") {
                                OutlinedButton(onClick = { onCancel(order.id) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                                    Text("ऑर्डर रद्द करें")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusText(status: String): String = when (status) {
    "placed" -> "ऑर्डर मिला"
    "accepted" -> "स्वीकृत"
    "packed" -> "पैक हो रहा है"
    "assigned" -> "डिलीवरी तय"
    "picked_up" -> "रास्ते में"
    "delivered" -> "डिलीवर हुआ"
    "cancelled" -> "रद्द"
    "refunded" -> "रिफंड"
    else -> status
}

@Composable
private fun AccountScreen(
    name: String, onName: (String) -> Unit, phone: String, busy: Boolean,
    onSave: () -> Unit, onSignOut: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("आपका खाता", color = Ink, fontWeight = FontWeight.Bold, fontSize = 23.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = onName, modifier = Modifier.fillMaxWidth(),
                    label = { Text("पूरा नाम") }, singleLine = true)
                OutlinedTextField(value = phone, onValueChange = {}, modifier = Modifier.fillMaxWidth(),
                    label = { Text("लॉगिन मोबाइल नंबर") }, readOnly = true, singleLine = true)
                Button(onClick = onSave, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("प्रोफाइल सेव करें") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("मदद और सहायता", color = Ink, fontWeight = FontWeight.Bold)
                Text("ऑर्डर से जुड़ी जानकारी 'ऑर्डर' टैब में देखें। सहायता संपर्क विवरण व्यवस्थापक द्वारा जोड़े जाने बाकी हैं।",
                    color = Muted, fontSize = 13.sp)
            }
        }
        OutlinedButton(onClick = onSignOut, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("लॉगआउट") }
        Text("MALRO Customer • Barmer", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(detail, color = Muted, fontSize = 13.sp)
        }
    }
}
