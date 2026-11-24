package com.example.ui.diagnostics.connect

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.obd.BluetoothArchitecture
import com.example.data.obd.LiveTelemetryStreamState
import com.example.data.obd.ObdConnectionManager
import com.example.data.obd.ObdConnectionManagerState
import com.example.data.obd.ObdConnectionMethod
import com.example.data.obd.ObdProtocolPreference
import com.example.data.obd.WifiObdPreset
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DiagnosticsConnectViewModel(
    private val connectionManager: ObdConnectionManager
) : ViewModel() {

    val state: StateFlow<ObdConnectionManagerState> = connectionManager.state
    val liveTelemetry: StateFlow<LiveTelemetryStreamState> = connectionManager.liveTelemetry

    fun refreshAll(context: Context) {
        connectionManager.refreshWifiState(context)
        connectionManager.refreshBluetoothState(context)
    }

    fun attachBluetoothObserver(context: Context) {
        connectionManager.attachBluetoothObserver(context, autoDiscoverIfEnabled = true)
    }

    fun detachBluetoothObserver() {
        connectionManager.detachBluetoothObserver()
    }

    fun selectMethod(method: ObdConnectionMethod) {
        connectionManager.selectMethod(method)
    }

    fun selectProtocol(protocol: ObdProtocolPreference) {
        connectionManager.selectProtocol(protocol)
    }

    fun refreshWifi(context: Context) {
        connectionManager.refreshWifiState(context)
    }

    fun selectWifiPreset(preset: WifiObdPreset) {
        connectionManager.selectWifiPreset(preset)
    }

    fun updateWifiHost(host: String) {
        connectionManager.updateWifiHost(host)
    }

    fun updateWifiPort(port: String) {
        connectionManager.updateWifiPort(port)
    }

    fun requiredBluetoothPermissions(): List<String> = connectionManager.requiredBluetoothPermissions()

    fun refreshBluetooth(context: Context) {
        connectionManager.refreshBluetoothState(context)
    }

    fun selectBluetoothArchitecture(context: Context, architecture: BluetoothArchitecture) {
        connectionManager.selectBluetoothArchitecture(context, architecture)
    }

    fun setShowAllBluetoothArchitectures(context: Context, showAll: Boolean) {
        connectionManager.setShowAllBluetoothArchitectures(context, showAll)
    }

    fun toggleBleDiscovery(context: Context) {
        if (connectionManager.state.value.bluetoothState.isDiscovering) {
            connectionManager.stopBleDiscovery(context)
        } else {
            connectionManager.startBleDiscovery(context)
        }
    }

    fun stopDiscovery(context: Context) {
        connectionManager.stopBleDiscovery(context)
    }

    fun selectBluetoothDevice(address: String) {
        connectionManager.selectBluetoothDevice(address)
    }

    fun attemptConnect(context: Context) {
        viewModelScope.launch {
            connectionManager.connectTransport(context.applicationContext ?: context)
        }
    }

    fun attemptConnect() {
        connectionManager.attemptConnect()
    }

    fun disconnect(context: Context) {
        viewModelScope.launch {
            connectionManager.disconnectAsync(context)
        }
    }
}
