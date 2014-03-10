package antero.player.messaging;

import android.app.Activity;
import android.content.*;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import antero.player.R;
import antero.player.tag.ProximityService;
import antero.player.tag.StorageService;
import antero.player.tag.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class PlayActivity extends Activity {

    public static final String ACTIVE_ACTIVITY = "antero.player.ACTIVE";

    private static final String TAG = PlayActivity.class.getName();
    private static final int DELAYED_TIMEOUT = 3000;
    private static final int DELAYED_SCAN = 1000;

    enum State {IDLE, RUNNING, SUSPENDED};

    private boolean storageBound = false;
    private boolean proximityBound = false;
    private StorageService storageService;
    private ProximityService proximityService;

    private MessagePlayer messagePlayer;
    private BlockingQueue<MessageData> incomingMessages = new ArrayBlockingQueue<MessageData>(64);
    private State state;
    private Handler displayingHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.antero_play);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Intent intent = new Intent(this, StorageService.Factory.newInstance());
        bindService(intent, storageServiceConnection, Context.BIND_AUTO_CREATE);

        intent = new Intent(this, ProximityService.class);
        bindService(intent, proximityServiceConnection, Context.BIND_AUTO_CREATE);

        registerReceiver(tagEventReceiver, getTagSensingIntentFilter());
        registerReceiver(messageEventReceiver, getMessageIntentFilter());

        displayingHandler = new Handler();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setActivityStatus(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        setActivityStatus(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        state = State.IDLE;
        Intent intent = getIntent();
        launchMessagePlayer(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(messageEventReceiver);
        unregisterReceiver(tagEventReceiver);
        unbindService(proximityServiceConnection);
        unbindService(storageServiceConnection);
    }

    private final BroadcastReceiver messageEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (GcmIntentService.MESSAGE_AVAILABLE.equals(action)) {
                launchMessagePlayer(intent);
            }
        }
    };

    private void launchMessagePlayer(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String recipient = extras.getString(GcmIntentService.MESSAGE_RECIPIENT);
            String payload = extras.getString(GcmIntentService.MESSAGE_PAYLOAD);

            if (!TextUtils.isEmpty(recipient) && !TextUtils.isEmpty(payload)) {
                try {
                    Log.i(TAG, "Add message to queue: '" + payload + "' for " + recipient);
                    incomingMessages.put(new MessageData(recipient, payload));

                    if (proximityBound) {
                        playMessagePendingTagScan();
                    } else {
                        // wait for a moment before retrying while service binding is still going on
                        displayingHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                               playMessagePendingTagScan();
                            }
                        }, DELAYED_SCAN);
                    }

                } catch (InterruptedException e) {
                    Log.i(TAG, "Error putting message to queue");
                }
            }
        }
    }

    private void playMessagePendingTagScan() {
        if (state == State.IDLE) {
            proximityService.scan(true);
            state = State.SUSPENDED;
        }
    }

    private final BroadcastReceiver tagEventReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (ProximityService.ACTION_TAG_FOUND.equals(action)) {
                Tag tag = (Tag)intent.getSerializableExtra(ProximityService.EXTRA_TAG_OBJECT);
                Log.i(TAG, "Tag found " + tag);

                if (state == State.SUSPENDED && tag != null){// && storageService.getTag(tag.getId()) != null) {
                    state = State.RUNNING;
                    List<MessageData> messageList = new ArrayList<MessageData>();

                    if (!incomingMessages.isEmpty())
                        incomingMessages.drainTo(messageList);

                    List<MessageData> notPlayed = new ArrayList<MessageData>();
                    for (MessageData messageData: messageList) {
                        if (!messageData.recipient.equals(tag.getId())) notPlayed.add(messageData);
                    }
                    messageList.removeAll(notPlayed);

                    if (!messageList.isEmpty()) {
                        messagePlayer = new MessagePlayer(messageList);
                        displayingHandler.post(messagePlayer);
                    }
                }
            } else
                if (ProximityService.ACTION_SCAN_ENDED.equals(action)) {
                    // time-out the activity if no tags have been found when scanning ends
                    if (state == State.SUSPENDED) {
                        displayingHandler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (state == State.SUSPENDED) {
                                    // time out
                                    Log.i(TAG, "Time out");
                                    state = State.IDLE;
                                }
                            }
                        }, DELAYED_TIMEOUT);
                    }
                }
        }
    };

    private class MessagePlayer implements Runnable {

        private List<MessageData> messageList;
        private String text;
        private int index;
        private MediaPlayer beeper;

        public MessagePlayer(List<MessageData> messageList) {
            this.messageList = messageList;
            text = messageList.get(0).payload + ": " + messageList.size();
            index = 0;

            beeper = MediaPlayer.create(PlayActivity.this, R.raw.beep);
            beeper.setLooping(false);
            beeper.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer arg0) {
                    Log.i(TAG, "End Playing sound");
                }
            });
        }

        @Override
        public void run() {
            if (index < text.length()) {
                runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        TextView textView = (TextView) findViewById(R.id.antero_play);
                        textView.setText(text.substring(0, index + 1));
                        index ++;
                        beeper.seekTo(0);
                        beeper.start();
                    }
                });
                displayingHandler.postDelayed(messagePlayer, 200);
            }
            else {
                index = 0;
                state = State.IDLE;
            }
        }
    }

    private IntentFilter getTagSensingIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ProximityService.ACTION_TAG_FOUND);
        intentFilter.addAction(ProximityService.ACTION_SCAN_ENDED);
        return intentFilter;
    }

    private IntentFilter getMessageIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(GcmIntentService.MESSAGE_AVAILABLE);
        return intentFilter;
    }

    private ServiceConnection proximityServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            ProximityService.LocalBinder binder = (ProximityService.LocalBinder)iBinder;
            proximityService = binder.getService();
            proximityBound = true;
            proximityService.initialize();
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
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            storageBound = false;
        }
    };

    private void setActivityStatus(boolean status) {
        SharedPreferences prefs = getSharedPreferences(PlayActivity.class.getSimpleName(), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(ACTIVE_ACTIVITY, status);
        editor.commit();
    }

    private class MessageData {
        String recipient;
        String payload;

        private MessageData(String recipient, String payload) {
            this.recipient = recipient;
            this.payload = payload;
        }
    }
}
