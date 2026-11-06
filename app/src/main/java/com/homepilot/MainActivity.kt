package com.homepilot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    private val prefs by lazy {
        val key = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(this, "homepilot_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HomePilot(prefs.getString("url", "") ?: "", prefs.getString("token", "") ?: "") { u,t -> prefs.edit().putString("url",u).putString("token",t).apply() } }
    }
}

data class Entity(val id: String, val name: String, val state: String, val domain: String, val unit: String = "")

private suspend fun request(base: String, token: String, path: String, method: String = "GET", payload: String? = null): String = withContext(Dispatchers.IO) {
    val conn = (URL(base.trimEnd('/') + "/api/" + path).openConnection() as HttpURLConnection)
    conn.connectTimeout = 8000; conn.readTimeout = 10000; conn.requestMethod = method
    conn.setRequestProperty("Authorization", "Bearer $token"); conn.setRequestProperty("Content-Type", "application/json")
    if (payload != null) { conn.doOutput = true; conn.outputStream.bufferedWriter().use { it.write(payload) } }
    try {
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) error("Home Assistant HTTP $code: ${body.take(180)}")
        body
    } finally { conn.disconnect() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePilot(initialUrl: String, initialToken: String, save: (String,String)->Unit) {
    var url by remember { mutableStateOf(initialUrl) }; var token by remember { mutableStateOf(initialToken) }
    var status by remember { mutableStateOf("Home Assistant nicht verbunden") }; var entities by remember { mutableStateOf(listOf<Entity>()) }
    var tab by remember { mutableStateOf(0) }; var energy by remember { mutableStateOf(listOf("sensor.solar_power" to "Solarleistung", "sensor.house_consumption" to "Hausverbrauch", "sensor.grid_power" to "Netzleistung", "sensor.battery_level" to "Batteriestand")) }
    val scope = rememberCoroutineScope()
    fun refresh() { scope.launch { status = "Verbinde …"; try {
        val raw = request(url, token, "states"); val arr = JSONArray(raw)
        entities = (0 until arr.length()).map { arr.getJSONObject(it) }.map { o -> val id=o.getString("entity_id"); val attrs=o.optJSONObject("attributes"); Entity(id,attrs?.optString("friendly_name",id) ?: id,o.optString("state"),id.substringBefore('.'),attrs?.optString("unit_of_measurement","").orEmpty()) }
        status = "Verbunden · ${entities.size} Entitäten"; save(url,token)
    } catch (e: Exception) { status = e.message ?: "Verbindung fehlgeschlagen" } } }
    MaterialTheme(colorScheme = darkColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF66D9B0), secondary = androidx.compose.ui.graphics.Color(0xFF87B7FF))) {
        Scaffold(topBar = { TopAppBar(title = { Text("HomePilot") }, actions = { Text(status, modifier=Modifier.padding(end=12.dp), style=MaterialTheme.typography.labelSmall) }) },
            bottomBar = { NavigationBar { listOf("Übersicht","Geräte","Automationen","Energie","Einstellungen").forEachIndexed { i, label -> NavigationBarItem(selected=tab==i,onClick={tab=i},icon={},label={Text(label)}) } } }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                when(tab) {
                    0 -> { item { Text("Dein Zuhause", style=MaterialTheme.typography.headlineMedium) }; item { Text(status) }; item { Button(onClick={refresh}) { Text("Aktualisieren") } }; item { Text("Schnellzugriff",style=MaterialTheme.typography.titleLarge) }; items(entities.filter { it.domain in listOf("light","switch","climate") }.take(8)) { e -> EntityCard(e) { act(url,token,e,refresh=::refresh,scope=scope) } } }
                    1,2 -> { item { Text(if(tab==1) "Geräte" else "Automationen",style=MaterialTheme.typography.headlineMedium) }; item { Button(onClick={refresh}) { Text("Laden") } }; items(entities.filter { if(tab==1) it.domain in listOf("light","switch","fan","climate","cover") else it.domain=="automation" }) { e -> EntityCard(e) { act(url,token,e,refresh=::refresh,scope=scope) } } }
                    3 -> { item { Text("Energie",style=MaterialTheme.typography.headlineMedium) }; item { Text("Sensor-Entitäten in Einstellungen anpassen. Werte kommen direkt aus Home Assistant.") }; items(energy) { (id,label) -> val e=entities.find{it.id==id}; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(label,style=MaterialTheme.typography.titleMedium); Text(e?.let { "${it.state} ${it.unit}" } ?: "Sensor nicht gefunden: $id") } } } }
                    else -> { item { Text("Verbindung",style=MaterialTheme.typography.headlineMedium) }; item { OutlinedTextField(url,{url=it},Modifier.fillMaxWidth(),label={Text("Home Assistant URL")},placeholder={Text("https://homeassistant.local:8123")},singleLine=true) }; item { OutlinedTextField(token,{token=it},Modifier.fillMaxWidth(),label={Text("Long-Lived Access Token")},visualTransformation=PasswordVisualTransformation(),singleLine=true) }; item { Button(onClick={refresh}) { Text("Verbinden und speichern") } }; item { Text("Energie-Sensoren (Entity-ID)",style=MaterialTheme.typography.titleLarge) }; items(energy.indices.toList()) { i -> OutlinedTextField(energy[i].first,{v->energy=energy.toMutableList().also{it[i]=it[i].copy(first=v)}},Modifier.fillMaxWidth(),label={Text(energy[i].second)},singleLine=true) } }
                }
            }
        }
    }
}
@Composable private fun EntityCard(e:Entity, click:()->Unit) { Card(Modifier.fillMaxWidth(),onClick=click) { Row(Modifier.padding(16.dp),horizontalArrangement=Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(e.name,style=MaterialTheme.typography.titleMedium); Text("${e.id} · ${e.state}") }; Text(if(e.state in listOf("on","open","home","playing")) "●" else "○") } } }
private fun act(url:String,token:String,e:Entity,refresh:()->Unit,scope:kotlinx.coroutines.CoroutineScope) { scope.launch { try { val service=if(e.state=="on") "turn_off" else "turn_on"; val body="{\"entity_id\":\"${e.id}\"}"; request(url,token,"services/${e.domain}/$service","POST",body); refresh() } catch (_:Exception) {} } }
