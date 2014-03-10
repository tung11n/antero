package antero.player.ui;

import android.app.ListActivity;
import android.content.*;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import antero.player.R;
import antero.player.tag.ProximityService;
import antero.player.tag.StorageService;
import antero.player.tag.Tag;

public class TagListActivity extends ListActivity {

    private boolean storageBound = false;
    private boolean proximityBound = false;
    private StorageService storageService;
    private ProximityService proximityService;
    private ArrayAdapter<Tag> tagListAdapter;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tag_list);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, StorageService.Factory.newInstance());
        bindService(intent, storageServiceConnection, Context.BIND_AUTO_CREATE);

        intent = new Intent(this, ProximityService.class);
        bindService(intent, proximityServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, getBluetoothIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        tagListAdapter.clear();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (storageBound) {
            unbindService(storageServiceConnection);
            storageBound = false;
        }

        if (proximityBound) {
            unbindService(proximityServiceConnection);
            proximityBound = false;
        }
    }

    public void onClick(final View view) {
        if (view == findViewById(R.id.scan)) {
            proximityService.scan(true);
        }
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ProximityService.ACTION_GATT_FOUND.equals(action)) {
                Tag tag = (Tag)intent.getSerializableExtra(ProximityService.EXTRA_TAG_OBJECT);
                storageService.persistTag(tag);
                tagListAdapter.add(tag);
                tagListAdapter.notifyDataSetChanged();
            }
        }
    };

    private IntentFilter getBluetoothIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ProximityService.ACTION_GATT_FOUND);
        intentFilter.addAction(ProximityService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(ProximityService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(ProximityService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ProximityService.ACTION_GATT_SERVICES_DISCOVERED);
        return intentFilter;
    }

    private ServiceConnection proximityServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            ProximityService.LocalBinder binder = (ProximityService.LocalBinder)iBinder;
            proximityService = binder.getService();
            proximityBound = true;
            if (!proximityService.initialize()) {
                //
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            proximityBound = false;
        }
    };

    private ServiceConnection storageServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder iBinder) {
            StorageService.LocalBinder binder = (StorageService.LocalBinder)iBinder;
            storageService = binder.getService();
            storageBound = true;

            tagListAdapter = new ArrayAdapter<Tag>(TagListActivity.this, R.layout.list_item, R.id.row_id, storageService.getTagList());
            setListAdapter(tagListAdapter);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            storageBound = false;
        }
    };
}
