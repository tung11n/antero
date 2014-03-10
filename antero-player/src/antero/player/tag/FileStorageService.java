package antero.player.tag;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tungtt on 3/1/14.
 */
public class FileStorageService extends Service implements StorageService {

    private static final String TAG = FileStorageService.class.getName();

    private static final String STORAGE_FILE = "antero_tags";

    private IBinder binder = new LocalBinder() {
        @Override
        public StorageService getService() {
            return FileStorageService.this;
        }
    };

    private Map<String, Tag> tags;

    @Override
    public void onCreate() {
        super.onCreate();
        tags = new HashMap<String, Tag>();

        try {
            FileInputStream in = openFileInput(STORAGE_FILE);
            BufferedReader buff = new BufferedReader(new InputStreamReader(in));

            for (String line; (line = buff.readLine()) != null; ) {
                String[] components = line.split(",");
                if (components.length != 3) {
                    Log.d(TAG, "Not a valid tag: " + line);
                    continue;
                }
                Tag tag = new Tag(components[0], components[1], Boolean.parseBoolean(components[2]));
                tags.put(components[0], tag);

            }

        } catch (FileNotFoundException e) {
            Log.i(TAG, "Storage not yet exists. It will be created when addTag() is invoked");
           // tags.put("1234", new Tag("1234", "Antero Tag", true));
        } catch (IOException e) {
            Log.e(TAG, "Error reading from storage", e);
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public Tag getTag(String id) {
        return tags.get(id);
    }

    @Override
    public List<Tag> getTagList() {
        return new ArrayList<Tag>(tags.values());
    }

    @Override
    public Tag createTag(String anteroId, String name, boolean interactive) {
        return saveTag(new Tag(anteroId, name, interactive));
    }

    @Override
    public Tag persistTag(Tag tag) {
        return saveTag(tag);
    }

    private Tag saveTag(Tag tag) {
        if (tags.get(tag.getId()) != null) {
            throw new RuntimeException("Tag exists for " + tag.getId());
        }

        BufferedWriter buff = null;
        try {
            FileOutputStream out = openFileOutput(STORAGE_FILE, MODE_APPEND);
            buff = new BufferedWriter(new OutputStreamWriter(out));

            tags.put(tag.getId(), tag);
            String raw = tag.getId() + "," + tag.getName() + "," + tag.isInteractive();
            buff.write(raw.toCharArray());
            return tag;

        } catch (Exception e) {
            Log.e(TAG, "Error writing to storage", e);
            throw new RuntimeException(e);
        } finally {
            try {
                buff.close();
            } catch (IOException e) {
                //
            }
        }
    }
}