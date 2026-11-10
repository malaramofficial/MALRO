package com.malro.customer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CustomerProduct(
    val id: String, val shopId: String, val shopName: String, val locality: String,
    val name: String, val description: String, val category: String,
    val pricePaise: Long, val stock: Int
)
data class CustomerProfile(val id: String, val fullName: String, val phone: String)
data class CustomerOrder(
    val id: String, val status: String, val paymentStatus: String, val totalPaise: Long,
    val createdAt: String, val shopName: String, val items: List<String>, val deliveryAddress: String
)

object CustomerBackend {
    private suspend fun request(context: Context, method: String, path: String, body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            val token = MalroPhoneAuth.currentAccessToken(context)
            val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                connection.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/json")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val message = try {
                        val json = JSONObject(response)
                        json.optString("message").ifBlank { json.optString("details").ifBlank { json.optString("hint") } }
                    } catch (_: Exception) { "" }
                    throw IllegalStateException(message.ifBlank { "सर्वर त्रुटि ($code)" })
                }
                response
            } finally { connection.disconnect() }
        }

    suspend fun loadProfile(context: Context, userId: String, phone: String): CustomerProfile {
        val query = "/rest/v1/profiles?id=eq.$userId&select=id,full_name,phone"
        val existing = JSONArray(request(context, "GET", query))
        if (existing.length() > 0) {
            val row = existing.getJSONObject(0)
            return CustomerProfile(row.getString("id"), row.optString("full_name"), row.optString("phone", phone))
        }
        val insert = JSONObject().put("id", userId).put("phone", phone).put("full_name", "")
        request(context, "POST", "/rest/v1/profiles", insert)
        val created = JSONArray(request(context, "GET", query))
        if (created.length() == 0) throw IllegalStateException("प्रोफाइल तैयार नहीं हो पाई")
        val row = created.getJSONObject(0)
        return CustomerProfile(row.getString("id"), row.optString("full_name"), row.optString("phone", phone))
    }

    suspend fun updateProfile(context: Context, userId: String, name: String): CustomerProfile {
        request(context, "PATCH", "/rest/v1/profiles?id=eq.$userId", JSONObject().put("full_name", name.trim()))
        val rows = JSONArray(request(context, "GET", "/rest/v1/profiles?id=eq.$userId&select=id,full_name,phone"))
        if (rows.length() == 0) throw IllegalStateException("प्रोफाइल अपडेट नहीं हुई")
        val row = rows.getJSONObject(0)
        return CustomerProfile(row.getString("id"), row.optString("full_name"), row.optString("phone"))
    }

    suspend fun loadProducts(context: Context): List<CustomerProduct> {
        val path = "/rest/v1/products?select=id,shop_id,name,description,category,price_paise,stock_quantity,shops(id,name,locality)" +
            "&status=eq.active&stock_quantity=gt.0&order=created_at.desc&limit=100"
        val rows = JSONArray(request(context, "GET", path))
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val shop = row.optJSONObject("shops") ?: JSONObject()
            CustomerProduct(
                id = row.getString("id"), shopId = row.getString("shop_id"),
                shopName = shop.optString("name", "स्थानीय दुकान"),
                locality = shop.optString("locality", "Barmer"),
                name = row.optString("name"), description = row.optString("description"),
                category = row.optString("category", "अन्य"),
                pricePaise = row.optLong("price_paise"), stock = row.optInt("stock_quantity")
            )
        }
    }

    suspend fun placeOrder(
        context: Context, shopId: String, name: String, phone: String,
        addressLine: String, locality: String, pincode: String, quantities: Map<String, Int>
    ): String {
        val address = JSONObject().put("name", name.trim()).put("phone", phone.trim())
            .put("address_line", addressLine.trim()).put("locality", locality.trim()).put("pincode", pincode.trim())
        val items = JSONArray()
        quantities.forEach { (productId, quantity) ->
            items.put(JSONObject().put("product_id", productId).put("quantity", quantity))
        }
        val body = JSONObject().put("p_shop_id", shopId).put("p_delivery_address", address).put("p_items", items)
        return request(context, "POST", "/rest/v1/rpc/place_customer_order", body).trim().trim('"')
    }

    suspend fun loadOrders(context: Context, userId: String): List<CustomerOrder> {
        val path = "/rest/v1/orders?select=id,status,payment_status,total_paise,created_at,delivery_address,shops(name),order_items(product_name_snapshot,quantity),order_events(event_type,note,created_at)" +
            "&customer_id=eq.$userId&order=created_at.desc&limit=50"
        val rows = JSONArray(request(context, "GET", path))
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val shop = row.optJSONObject("shops") ?: JSONObject()
            val itemRows = row.optJSONArray("order_items") ?: JSONArray()
            val itemLabels = (0 until itemRows.length()).map { itemIndex ->
                val item = itemRows.getJSONObject(itemIndex)
                "${item.optString("product_name_snapshot")} × ${item.optInt("quantity")}"
            }
            val address = row.optJSONObject("delivery_address") ?: JSONObject()
            val addressLabel = listOf(address.optString("address_line"), address.optString("locality"), address.optString("pincode"))
                .filter { it.isNotBlank() }.joinToString(", ")
            CustomerOrder(
                id = row.getString("id"), status = row.optString("status"),
                paymentStatus = row.optString("payment_status"), totalPaise = row.optLong("total_paise"),
                createdAt = row.optString("created_at"), shopName = shop.optString("name", "स्थानीय दुकान"),
                items = itemLabels, deliveryAddress = addressLabel
            )
        }
    }
}
