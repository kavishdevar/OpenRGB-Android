package me.kavishdevar.openrgb

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import io.gitlab.mguimard.openrgb.client.OpenRGBClient
import io.gitlab.mguimard.openrgb.entity.OpenRGBDevice
import io.gitlab.mguimard.openrgb.examples.ListDevices
import kotlinx.coroutines.launch
import me.kavishdevar.openrgb.ui.theme.OpenRGBTheme
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sharedPref = this.getSharedPreferences("servers", Context.MODE_PRIVATE)

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        enableEdgeToEdge()
        setContent {
            OpenRGBTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Main(sharedPref = sharedPref, modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@SuppressLint("CoroutineCreationDuringComposition")
@Composable
// Should not be passing the whole SharedPreferences, but doing it anyways.
fun Main(sharedPref: SharedPreferences, modifier: Modifier) {
    val showNewDeviceDialog = remember { mutableStateOf(false) }
    val servers=sharedPref.all.keys
    if (servers.size==0) {
       showNewDeviceDialog.value = true
    }
    else {
        // Focusing on only 1 server (PC) at first but will use an array to avoid complications later.
        val selectedServer = remember { mutableStateOf(servers.first()) }
        val ipPort = sharedPref.getString(selectedServer.value, "192.168.1.90:6742")!!
        val ipToConnect = ipPort.split(":")[0]
        val portToConnect = ipPort.split(":")[1].toInt()
        Log.d("me.kavishdevar.openrgb", "Connecting to ${selectedServer.value}. IP address is $ipPort")
        val client = remember {
            mutableStateOf(
                OpenRGBClient(
                    ipToConnect,
                    portToConnect,
                    android.os.Build.MODEL.toString()
                )
            )
        }

        val clientConnected = remember { mutableStateOf(false) }
        // Should NOT start a Coroutine in a view, but doing it anyways.
        rememberCoroutineScope().launch {
            Log.d("me.kavishdevar.openrgb", "Trying to connect")
            try {
                client.value.connect()
                clientConnected.value = true
                Log.d("me.kavishdevar.openrgb", "Connected successfully!")
            } catch (e: IOException) {
                e.printStackTrace()
                if (e.message?.contains("ECONNREFUSED") == true) {
                    clientConnected.value = false
                }
            }
        }
        if (clientConnected.value) {
            if (client.value.controllerCount != 0) {
                if (client.value.getDeviceController(0).leds[0].value.red
                    and client.value.getDeviceController(0).leds[0].value.green
                    and client.value.getDeviceController(0).leds[0].value.blue == 0
                ) {
                    Log.d("me.kavishdevar.openrgb", "lights are off")
                } else {
                    Log.d("me.kavishdevar.openrgb", "lights are on")
                }
            }
            CreateDeviceCards(client.value)
        } else {
            Column (modifier.padding(100.dp)) {
                Log.d(
                    "me.kavishdevar.openrgb",
                    "Couldn't connect! Check server and details:\n$ipPort"
                )
                Text("Was trying to connect to $ipPort, but was unsuccessful, check server!\n Made an error?")
                Button({
                    sharedPref.edit().remove(selectedServer.value)
                        .apply(); showNewDeviceDialog.value = true
                }) { Text("Edit server details")}
            }
        }
    }
    if (showNewDeviceDialog.value) {
        Box(
            modifier = Modifier
                .padding(30.dp),
            contentAlignment = Alignment.Center
        ) {

            val name = remember { mutableStateOf("") }
            val ip = remember { mutableStateOf("192.168.1.") }
            val port = remember { mutableIntStateOf(6742) }

            val restart = remember { mutableStateOf(false) }

            val buttonEnabled = remember { mutableStateOf(false) }
            if (ip.value != "") {
                buttonEnabled.value = true
            }
            Column {
                Text(
                    text = "Add Device",
                    modifier = Modifier
                        .padding(start = 2.dp, end = 2.dp, top = 24.dp, bottom = 8.dp)
                        .align(Alignment.CenterHorizontally),
                    fontSize = MaterialTheme.typography.labelLarge.fontSize * 2,
                    fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
                    fontWeight = MaterialTheme.typography.labelLarge.fontWeight,
                    fontStyle = MaterialTheme.typography.labelLarge.fontStyle,
                )
                TextField(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    value = name.value,
                    onValueChange = {
                        name.value = it
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    label = { Text("Device Name") },
                    supportingText = { Text("Defaults to the IP Address") }
                )
                Row {
                    TextField(
                        modifier = Modifier
                            .padding(start = 8.dp, top = 8.dp, end = 8.dp)
                            .fillMaxWidth(0.7f),
                        value = ip.value,
                        onValueChange = {
                            ip.value = it
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("IP Address") }
                    )
                    TextField(
                        modifier = Modifier
                            .padding(end = 8.dp, top = 8.dp, bottom = 8.dp),
                        value = port.intValue.toString(),
                        onValueChange = {
                            port.intValue = it.toInt()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text("Port") }
                    )
                }
                Button(
                    enabled = buttonEnabled.value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 28.dp),
                    onClick = {
                        Log.d("me.kavishdevar.openrgb","Show new-server dialog box")
                        val devName = if (name.value == "") {
                            ip.value
                        } else {
                            name.value
                        }
                        sharedPref
                            .edit()
                            .putString(devName, "${ip.value}:${port.intValue}")
                            .apply()
                        restart.value = true
                    }
                )
                {
                    Text("Save")
                }
            }
            if (restart.value) {
                Main(sharedPref =  sharedPref ,modifier = modifier)
                restart.value = false
            }
        }
        showNewDeviceDialog.value = false
    }
}

@Composable
fun CreateDeviceCards(client: OpenRGBClient) {

}