package antero.player.messaging;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.*;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import antero.player.R;
import antero.player.ui.StartActivity;
import com.google.android.gms.gcm.GoogleCloudMessaging;

/**
 * Created by tungtt on 2/15/14.
 */
public class GcmIntentService extends IntentService {
    public static final String MESSAGE_AVAILABLE = "antero.message.MESSAGE_AVAILABLE";
    public static final String MESSAGE_RECIPIENT = "antero.message.RECIPIENT";
    public static final String MESSAGE_PAYLOAD = "antero.message.PAYLOAD";
    public static final int NOTIFICATION_ID = 1;

    private static final String TAG = GcmIntentService.class.getName();

    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder builder;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);
        Log.i(TAG, "Received message type " + messageType);

        if (!extras.isEmpty()) {  // has effect of unparcelling Bundle
            /*
             * Filter messages based on message type. Since it is likely that GCM
             * will be extended in the future with new message types, just ignore
             * any message types you're not interested in, or that you don't
             * recognize.
             */
            if (GoogleCloudMessaging.
                    MESSAGE_TYPE_SEND_ERROR.equals(messageType)) {
                //sendNotification("Send error: " + extras.toString());
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_DELETED.equals(messageType)) {
                //sendNotification("Deleted messages on server: " + extras.toString());

                // If it's a regular GCM message, do some work.
            } else if (GoogleCloudMessaging.
                    MESSAGE_TYPE_MESSAGE.equals(messageType)) {

                // Post notification of received message.
                String recipient = extras.getString(MESSAGE_RECIPIENT);
                String payload = extras.getString(MESSAGE_PAYLOAD);

                StringBuilder msgBuilder = new StringBuilder();
                for (String key: extras.keySet())
                    msgBuilder.append(key + ":" + extras.get(key) + "\n");

                if (!isPlayActivityRunning())
                    startApp(recipient, payload);
                else
                    broadcastEvent(recipient, payload);
            }
        }
        // Release the wake lock provided by the WakefulBroadcastReceiver.
        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }

    private boolean isPlayActivityRunning() {
        SharedPreferences prefs = getSharedPreferences(PlayActivity.class.getSimpleName(), Context.MODE_PRIVATE);
        return prefs.getBoolean(PlayActivity.ACTIVE_ACTIVITY, false);
    }

    private void startApp(String recipient, String payload) {
        Intent intent = new Intent(this, PlayActivity.class);
        intent.putExtras(bundleData(recipient, payload));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void broadcastEvent(String recipient, String payload) {
        Intent intent = new Intent(MESSAGE_AVAILABLE);
        intent.putExtras(bundleData(recipient, payload));
        sendBroadcast(intent);
    }

    private Bundle bundleData(String recipient, String payload) {
        Bundle bundle = new Bundle();
        bundle.putString(MESSAGE_RECIPIENT, recipient);
        bundle.putString(MESSAGE_PAYLOAD, payload);
        return bundle;
    }

    private void sendNotification(String msg) {
        mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, StartActivity.class), 0);

        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.drawable.ant_evnt)
                        .setContentTitle("GCM Notification")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(msg))
                        .setContentText(msg)
                        .setContentIntent(contentIntent);

        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

}