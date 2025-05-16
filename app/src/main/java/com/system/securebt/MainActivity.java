package com.system.securebt;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SecureBT";
    private EditText btNameInput;
    private Button connectButton, discoverButton;
    private ListView deviceList;
    private BluetoothAdapter bluetoothAdapter;
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final UUID[] UUIDS = {
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"), // SPP
            UUID.fromString("00001108-0000-1000-8000-00805F9B34FB"), // Headset
            UUID.fromString("0000111E-0000-1000-8000-00805F9B34FB")  // Handsfree
    };
    private ArrayList<BluetoothDevice> discoveredDevices = new ArrayList<>();
    private ArrayAdapter<String> deviceAdapter;
    private ArrayList<String> deviceNames = new ArrayList<>();

    private final BroadcastReceiver discoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    String deviceName = device.getName() != null ? device.getName() : "Unknown";
                    String deviceAddress = device.getAddress();
                    int deviceType = device.getType();
                    String typeStr = deviceType == BluetoothDevice.DEVICE_TYPE_CLASSIC ? "Classic" :
                            deviceType == BluetoothDevice.DEVICE_TYPE_LE ? "LE" :
                                    deviceType == BluetoothDevice.DEVICE_TYPE_DUAL ? "Dual" : "Unknown";
                    Log.d(TAG, "Found device: " + deviceName + " (" + deviceAddress + "), Type: " + typeStr);
                    if (!discoveredDevices.contains(device)) {
                        discoveredDevices.add(device);
                        deviceNames.add(deviceName + " (" + deviceAddress + ", " + typeStr + ")");
                        deviceAdapter.notifyDataSetChanged();
                    }
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Toast.makeText(MainActivity.this, "Device discovery finished", Toast.LENGTH_SHORT).show();
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Device paired: " + device.getName());
                    Toast.makeText(MainActivity.this, "Paired with " + device.getName(), Toast.LENGTH_SHORT).show();
                } else if (bondState == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "Pairing failed or bond removed for: " + device.getName());
                    Toast.makeText(MainActivity.this, "Pairing failed for " + device.getName() + ". Try PINs like 0000 or 1234.", Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        btNameInput = findViewById(R.id.btNameInput);
        connectButton = findViewById(R.id.connectButton);
        discoverButton = findViewById(R.id.discoverButton);
        deviceList = findViewById(R.id.deviceList);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Set up ListView adapter
        deviceAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        deviceList.setAdapter(deviceAdapter);
        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            BluetoothDevice device = discoveredDevices.get(position);
            connectToDevice(device.getName(), device.getAddress());
        });

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Request permissions
        requestBluetoothPermissions();

        // Check battery optimization
        checkBatteryOptimization();

        // Set up button listeners
        connectButton.setOnClickListener(v -> {
            String btName = btNameInput.getText().toString().trim();
            if (btName.isEmpty()) {
                Toast.makeText(this, "Please enter a device name or select a device", Toast.LENGTH_SHORT).show();
                return;
            }
            connectToDevice(btName, "");
        });

        discoverButton.setOnClickListener(v -> startDeviceDiscovery());
    }

    private void requestBluetoothPermissions() {
        String[] permissions = {
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        };

        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_BLUETOOTH_PERMISSIONS
            );
        } else {
            checkLocationServices();
        }
    }

    private void checkLocationServices() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!isLocationEnabled) {
            Toast.makeText(this, "Please enable location services for Bluetooth discovery", Toast.LENGTH_LONG).show();
            Intent locationSettings = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(locationSettings);
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Toast.makeText(this, "Please disable battery optimization for better Bluetooth performance", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }

    private boolean checkBluetoothAndLocation() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                startActivityForResult(enableBtIntent, REQUEST_BLUETOOTH_PERMISSIONS);
            }
            return false;
        }

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!isLocationEnabled) {
            Toast.makeText(this, "Please enable location services", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void startDeviceDiscovery() {
        if (!checkBluetoothAndLocation()) return;

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions();
            return;
        }

        // Clear previous discoveries
        discoveredDevices.clear();
        deviceNames.clear();
        deviceAdapter.notifyDataSetChanged();

        // Register BroadcastReceiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(discoveryReceiver, filter);

        // Start discovery
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        bluetoothAdapter.startDiscovery();
        Toast.makeText(this, "Starting device discovery...", Toast.LENGTH_SHORT).show();
    }

    private void connectToDevice(String btName, String macAddress) {
        if (!checkBluetoothAndLocation()) return;

        Log.d(TAG, "Attempting to connect: Name=" + btName + ", MAC=" + macAddress);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions();
            return;
        }

        BluetoothDevice targetDevice = null;

        // Check paired devices
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device : pairedDevices) {
            if (!btName.isEmpty() && device.getName() != null && device.getName().equalsIgnoreCase(btName)) {
                targetDevice = device;
                break;
            }
            if (!macAddress.isEmpty() && device.getAddress().equalsIgnoreCase(macAddress)) {
                targetDevice = device;
                break;
            }
        }

        // Check discovered devices
        if (targetDevice == null) {
            for (BluetoothDevice device : discoveredDevices) {
                if (!btName.isEmpty() && device.getName() != null && device.getName().equalsIgnoreCase(btName)) {
                    targetDevice = device;
                    break;
                }
                if (!macAddress.isEmpty() && device.getAddress().equalsIgnoreCase(macAddress)) {
                    targetDevice = device;
                    break;
                }
            }
        }

        if (targetDevice == null) {
            Log.d(TAG, "Device not found in paired or discovered devices");
            Toast.makeText(this, "Device not found. Ensure it is paired or discovered.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if device is already paired
        if (targetDevice.getBondState() == BluetoothDevice.BOND_BONDED) {
            Log.d(TAG, "Device already paired: " + targetDevice.getName());
            Toast.makeText(this, "Device " + targetDevice.getName() + " is already paired", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check device type
        int deviceType = targetDevice.getType();
        if (deviceType == BluetoothDevice.DEVICE_TYPE_LE) {
            Log.d(TAG, "Device is BLE-only: " + targetDevice.getName());
            Toast.makeText(this, "Device uses Bluetooth Low Energy, which is not supported by this app.", Toast.LENGTH_LONG).show();
            return;
        }

        // Initiate pairing if not paired
        Log.d(TAG, "Device not paired, attempting to pair: " + targetDevice.getName());
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            targetDevice.createBond();
            Toast.makeText(this, "Pairing with " + targetDevice.getName() + ". Enter PIN (try 0000 or 1234) in the system dialog.", Toast.LENGTH_LONG).show();
        }

        // Attempt connection
        new ConnectThread(targetDevice).start();
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private static final int MAX_RETRIES = 3;

        public ConnectThread(BluetoothDevice device) {
            mmDevice = device;
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            // Try multiple UUIDs
            for (int i = 0; i < UUIDS.length; i++) {
                try {
                    Log.d(TAG, "Creating socket with UUID: " + UUIDS[i]);
                    mmSocket = device.createRfcommSocketToServiceRecord(UUIDS[i]);
                    break; // Success, exit loop
                } catch (IOException e) {
                    Log.e(TAG, "Socket creation failed with UUID " + UUIDS[i], e);
                }
            }

            // Fallback to reflection method
            if (mmSocket == null) {
                try {
                    Log.d(TAG, "Attempting reflection-based socket creation");
                    Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                    mmSocket = (BluetoothSocket) m.invoke(device, 1);
                } catch (Exception e) {
                    Log.e(TAG, "Reflection socket creation failed", e);
                }
            }
        }

        public void run() {
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                cancel();
                return;
            }
            bluetoothAdapter.cancelDiscovery();

            // Wait for pairing if necessary
            if (mmDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                Log.d(TAG, "Waiting for pairing to complete for: " + mmDevice.getName());
                int waitTime = 0;
                while (waitTime < 20000 && mmDevice.getBondState() != BluetoothDevice.BOND_BONDED) { // 20s timeout
                    try {
                        Thread.sleep(500);
                        waitTime += 500;
                    } catch (InterruptedException e) {
                        Log.e(TAG, "Interrupted while waiting for pairing", e);
                    }
                }
                if (mmDevice.getBondState() != BluetoothDevice.BOND_BONDED) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Pairing failed for " + mmDevice.getName() + ". Check PIN and try again.", Toast.LENGTH_LONG).show());
                    cancel();
                    return;
                }
            }

            if (mmSocket == null) {
                Log.e(TAG, "Socket is null, cannot connect");
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to create socket for " + mmDevice.getName(), Toast.LENGTH_LONG).show());
                cancel();
                return;
            }

            int attempt = 0;
            while (attempt < MAX_RETRIES) {
                try {
                    Log.d(TAG, "Connecting to device: " + mmDevice.getName() + ", attempt " + (attempt + 1));
                    mmSocket.connect();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connected to " + mmDevice.getName(), Toast.LENGTH_SHORT).show());
                    return;
                } catch (IOException connectException) {
                    Log.e(TAG, "Connection attempt " + (attempt + 1) + " failed: " + connectException.getMessage());
                    attempt++;
                    if (attempt < MAX_RETRIES) {
                        try {
                            Thread.sleep(2000); // 2s delay between retries
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Interrupted during retry delay", e);
                        }
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "Connection failed after " + MAX_RETRIES + " attempts. Check Bluetooth settings or device PIN.", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                            startActivity(intent);
                        });
                        cancel();
                    }
                }
            }
        }

        public void cancel() {
            if (mmSocket != null) {
                try {
                    mmSocket.close();
                    Log.d(TAG, "Socket closed for: " + mmDevice.getName());
                } catch (IOException e) {
                    Log.e(TAG, "Socket close failed", e);
                } finally {
                    mmSocket = null; // Ensure no reuse
                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
                checkLocationServices();
            } else {
                Toast.makeText(this, "Required permissions denied. App cannot function.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(discoveryReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered", e);
        }
    }
}