package antero.player.tag;

import android.os.Binder;

import java.util.List;

/**
 * Created by tungtt on 3/1/14.
 */
public interface StorageService {
    /**
     *
     * @param id
     * @return
     */
    public Tag getTag(String id);

    /**
     *
     * @param anteroId
     * @param name
     * @param interactive
     * @return
     */
    public Tag createTag(String anteroId, String name, boolean interactive);

    /**
     *
     * @param tag
     * @return
     */
    public Tag persistTag(Tag tag);
    /**
     *
     * @return
     */
    public List<Tag> getTagList();

    public abstract class LocalBinder extends Binder {
        public abstract StorageService getService();
    }

    public static class Factory {
        public static Class newInstance() {
            return FileStorageService.class;
        }
    }
}
