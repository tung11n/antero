package antero.player.tag;

import java.io.Serializable;

/**
 * Created by tungtt on 3/1/14.
 */
public class Tag implements Serializable {
    private String id;
    private String name;
    boolean interactive;

    public Tag() {
        id = null;
        name = null;
        interactive = false;
    }

    public Tag(String id, String name, boolean interactive) {
        this.id = id;
        this.name = name;
        this.interactive = interactive;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isInteractive() {
        return interactive;
    }

    @Override
    public String toString() {
        return name + " (" + id + ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Tag tag = (Tag) o;

        if (id != null ? !id.equals(tag.id) : tag.id != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}