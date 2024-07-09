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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import io.gitlab.mguimard.openrgb.client.OpenRGBClient
import io.gitlab.mguimard.openrgb.entity.OpenRGBColor
import io.gitlab.mguimard.openrgb.entity.OpenRGBDevice
import kotlinx.coroutines.launch
import me.kavishdevar.openrgb.ui.theme.OpenRGBTheme
import java.io.IOException
import java.util.Locale

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
    val showNewServerDialog = remember { mutableStateOf(false) }

    val selectedServer = remember { mutableStateOf("") }
    var ipToConnect = "192.168.1."
    var portToConnect = 6742

    val servers=sharedPref.all.keys
    if (servers.size==0) {
       showNewServerDialog.value = true
    }
    else {
        // Focusing on only 1 server (PC) at first but will use an array to avoid complications later.
        selectedServer.value = servers.first()
        val ipPort = sharedPref.getString(selectedServer.value, "192.168.1.90:6742")!!
        ipToConnect = ipPort.split(":")[0]
        portToConnect = ipPort.split(":")[1].toInt()

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
        val readyToCreate = remember { mutableStateOf(false) }
        if (readyToCreate.value) {
            CreateDeviceCards(client.value)
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
            readyToCreate.value = true
        } else {
            Column (modifier.padding(100.dp)) {
                Log.d(
                    "me.kavishdevar.openrgb",
                    "Couldn't connect! Check server and details:\n$ipPort"
                )
                Text("Was trying to connect to $ipPort, but was unsuccessful, check server!\n Made an error?")
                Row {
                    Button({
                        showNewServerDialog.value = true
                    }) { Text("Edit server details")}
                    Button({
                        val t = Thread {
                            Log.d("me.kavishdevar.openrgb", "Trying to connect")
                            try {
                                client.value.connect()
                                clientConnected.value = true
                                Log.d("me.kavishdevar.openrgb", "Connected successfully!")
                                readyToCreate.value = true
                            } catch (e: IOException) {
                                e.printStackTrace()
                                if (e.message?.contains("ECONNREFUSED") == true) {
                                    clientConnected.value = false
                                }
                            }
                        }
                        t.start()
                    }) { Text("Retry")}
                }
            }
        }
    }
    if (showNewServerDialog.value) {
        Box(
            modifier = Modifier
                .padding(30.dp),
            contentAlignment = Alignment.Center
        ) {

            val name = remember { mutableStateOf(selectedServer.value) }
            val ip = remember { mutableStateOf(ipToConnect) }
            val port = remember { mutableIntStateOf(portToConnect) }
            sharedPref.edit().remove(selectedServer.value).apply()
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
        showNewServerDialog.value = false
    }
}

@Composable
fun CreateDeviceCards(client: OpenRGBClient) {
    val controllers = mutableListOf<OpenRGBDevice>()
    for (i in 0 until client.controllerCount) {
       controllers.add(client.getDeviceController(i))
    }

    val deviceIndex = remember { mutableIntStateOf(0) }
    val showPicker = remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    )
    {
        controllers.forEach { device ->
            OutlinedCard(
                onClick = {
                    deviceIndex.intValue = controllers.indexOf(device)
                    showPicker.value = true
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            )
            {
                Image(
                    painter = painterResource(
                        id = if (device.type.name.contains("LIGHT")) {
                            R.drawable.light
                        } else if (device.type.name.contains("MOTHERBOARD")) {
                            R.drawable.motherboard
                        } else if (device.type.name.contains("GAMEPAD")) {
                            R.drawable.gamepad
                        } else if (device.type.name.contains("KEYBOARD")) {
                            R.drawable.keyboard
                        } else if (device.type.name.contains("MOUSE")) {
                            R.drawable.mouse
                        } else {
                            R.drawable.lightbulb
                        }
                    ),
                    contentDescription = null,
                    alignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .size(48.dp, 48.dp)
                        .padding(start = 16.dp, top = 8.dp)
                )
                Text(
                    text = device.name.toString(),
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 2.dp, top = 8.dp),
                    textAlign = TextAlign.Left,
                    fontStyle = MaterialTheme.typography.headlineMedium.fontStyle,
                    fontFamily = MaterialTheme.typography.headlineMedium.fontFamily,
                    fontWeight = MaterialTheme.typography.headlineMedium.fontWeight,
                    fontSize = MaterialTheme.typography.headlineMedium.fontSize,
                )
                Text(
                    text = device.type.toString().replace("DEVICE_TYPE_", "")
                        .lowercase()
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                    modifier = Modifier
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp,
                            top = 2.dp
                        ),
                    textAlign = TextAlign.Left,
                )

            }
        }
    }
    if (showPicker.value) {
        Dialog(
            onDismissRequest = {
                showPicker.value = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),

                ) {
                Text(
                    text = client.getDeviceController(deviceIndex.intValue).name.toString(),
                    textAlign = TextAlign.Center,
                    fontWeight = MaterialTheme.typography.titleLarge.fontWeight,
                    fontFamily = MaterialTheme.typography.titleLarge.fontFamily,
                    fontStyle = MaterialTheme.typography.titleLarge.fontStyle,
                    fontSize = MaterialTheme.typography.titleLarge.fontSize * 1.5,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 25.dp)
                )
                DeviceControl(deviceIndex = deviceIndex.intValue, client = client)
            }
        }
    }
}
@OptIn(ExperimentalStdlibApi::class)
@Composable
fun DeviceControl(deviceIndex: Int, client: OpenRGBClient) {
    Column(
        modifier = Modifier
            .padding(10.dp)
            .fillMaxHeight(.8f)
    )
    {
        val device = client.getDeviceController(deviceIndex)
        Log.d(
            "colorGet", "Colors: " + device.colors
        )
        val controller = rememberColorPickerController()
        Spacer(modifier = Modifier.height(20.dp))
        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp),
            controller = controller,
            initialColor = Color(
                device.leds[0].value.red,
                device.leds[0].value.green,
                device.leds[0].value.blue
            ),
            onColorChanged = {
                for (led in device.leds) {
                    client.updateLed(
                        deviceIndex,
                        device.leds.indexOf(led),
                        OpenRGBColor.fromInt(it.hexCode.hexToInt())
                    )
                }
            }
        )

        BrightnessSlider(
            controller = controller,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .padding(10.dp),
            borderRadius = 28.dp,
            wheelRadius = 10.dp
        )
    }
}