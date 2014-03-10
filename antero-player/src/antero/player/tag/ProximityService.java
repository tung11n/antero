package antero.player.tag;

import android.app.Service;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.util.*;

/**
 * Created by tungtt on 3/2/14.
 */
public class ProximityService extends Service {

    private static final String TAG = ProximityService.class.getName();

    private static final int SCAN_PERIOD = 10000;
    private static final UUID LINK_LOSS_SERVICE_UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb");
    private static final UUID ALERT_LEVEL_CHARACTERISTICS_UUID = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
    private static final UUID ANTERO_SERVICE_UUID = LINK_LOSS_SERVICE_UUID;
    private static final UUID ANTERO_ID_UUID = ALERT_LEVEL_CHARACTERISTICS_UUID;
    private static final UUID ANTERO_NAME_UUID = UUID.fromString("0000db5c-0000-1000-8000-00805f9b34fb");

    public final static String ACTION_GATT_FOUND = "antero.bluetooth.le.ACTION_GATT_FOUND";
    public final static String ACTION_GATT_CONNECTED = "antero.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED = "antero.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED = "antero.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE = "antero.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String ACTION_TAG_FOUND = "antero.bluetooth.le.ACTION_TAG_FOUND";
    public final static String ACTION_SCAN_ENDED = "antero.bluetooth.le.ACTION_SCAN_ENDED";
    public final static String EXTRA_TAG_OBJECT =  "antero.bluetooth.tag.TAG";

    private boolean mScanning;
    private BluetoothAdapter mBluetoothAdapter;
    private Map<String, DeviceBuilder> deviceBuilders;
    private Handler mHandler = new Handler();

    public class LocalBinder extends Binder {
        public ProximityService getService() {
            return ProximityService.this;
        }
    }

    private final Binder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }


    public boolean initialize() {
        if (mBluetoothAdapter == null) {
            BluetoothManager manager  = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
            mBluetoothAdapter = manager.getAdapter();
        }

        if (mBluetoothAdapter == null) {
            Log.i(TAG, "Unable to obtain bluetooth manager");
            return false;
        }

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.i(TAG, "BLE not supported");
            return false;
        }

        if (mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "BLE enabled");
        }
        else {
            Log.i(TAG, "BLE not enabled");
        }

        return true;
    }

    public void scan(final boolean enable) {
        if (enable) {
            if (!mScanning) {
                // Stops scanning after a pre-defined scan period.
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mScanning = false;
                        Log.i(TAG, "End scanning");
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);

                        Intent intent = new Intent();
                        intent.setAction(ACTION_SCAN_ENDED);
                        sendBroadcast(intent);

                        for (DeviceBuilder builder: deviceBuilders.values()) {
                            BluetoothGatt gatt = builder.gatt;
                            if (gatt != null) gatt.disconnect();
                        }

                    }
                }, SCAN_PERIOD);

                deviceBuilders = new HashMap<String, DeviceBuilder>();
                mScanning = true;
                Log.i(TAG, "Start scanning");
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            }
        } else {
            if (mScanning) {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Log.i(TAG, "Device found: " + device.getName());
                    device.connectGatt(ProximityService.this, false, mGattCallback);
                }
            };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server: " + gatt.getDevice().getName());
                DeviceBuilder builder = new DeviceBuilder(gatt);
                deviceBuilders.put(gatt.getDevice().getAddress(), builder);
                gatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server");
                gatt.close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                DeviceBuilder builder = deviceBuilders.get(gatt.getDevice().getAddress());
                if (builder != null) {
                    builder.queueCharacteristics();
                    builder.readNextCharacteristic();
                }
                else {
                    // should never happen
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                DeviceBuilder builder = deviceBuilders.get(gatt.getDevice().getAddress());

                if (!builder.buildCompleted()) {
                    builder.readNextCharacteristic();
                }
                else {
                    String id = readCharacteristicValue(gatt, ANTERO_ID_UUID);
                    String name = readCharacteristicValue(gatt, ANTERO_NAME_UUID);

                    if (id != null && name != null) {
                        Tag tag = new Tag(id, name, true);

                        Log.i(TAG, "Tag in proximity: " + tag);
                        Intent intent = new Intent(ACTION_TAG_FOUND);
                        intent.putExtra(EXTRA_TAG_OBJECT, tag);

                        sendBroadcast(intent);
                    }
                }
            }
        }

        private String readCharacteristicValue(BluetoothGatt gatt, UUID uuid) {
            BluetoothGattService anteroService = gatt.getService(ANTERO_SERVICE_UUID);
            if (anteroService != null) {
                BluetoothGattCharacteristic characteristic = anteroService.getCharacteristic(uuid);
                if (characteristic != null) {
                    byte[] data = characteristic.getValue();
                    return data != null && data.length > 0 ? new String(data) : null;
                }
            }
            else {
               // should never happen
            }

            return null;
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.i(TAG,String.format("RSSI read: %d", rssi));
        }
    };

    class DeviceBuilder {
        BluetoothGatt gatt;
        Deque<BluetoothGattCharacteristic> characteristicReadingQueue;

        DeviceBuilder(BluetoothGatt gatt) {
            this.gatt = gatt;
            characteristicReadingQueue = new ArrayDeque<BluetoothGattCharacteristic>();
        }

        void queueCharacteristics() {
            BluetoothGattService anteroService = gatt.getService(ANTERO_SERVICE_UUID);
            if (anteroService != null) {

                List<BluetoothGattCharacteristic> characteristicList = anteroService.getCharacteristics();
                for (BluetoothGattCharacteristic c: characteristicList) {
                    characteristicReadingQueue.push(c);
                }
            }
        }

        boolean buildCompleted() {
            return characteristicReadingQueue.isEmpty();
        }

        void readNextCharacteristic() {
            try {
                BluetoothGattCharacteristic characteristic = characteristicReadingQueue.pop();
                gatt.readCharacteristic(characteristic);
            } catch (Exception e) {
                // no more to read
            }
        }
    }
}
