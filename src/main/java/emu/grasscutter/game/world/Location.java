package emu.grasscutter.game.world;

import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Transient;
import lombok.*;

@Entity
public class Location extends Position {
    private static final long serialVersionUID = 1L;

    @Transient @Getter @Setter private transient Scene scene;

    private Location() {
        super();
    }

    public Location(Scene scene, Position position) {
        super(position);
        this.scene = scene;
    }

    public Location(Scene scene, float x, float y) {
        super(x, y);
        this.scene = scene;
    }

    public Location(Scene scene, float x, float y, float z) {
        super(x, y, z);
        this.scene = scene;
    }

    @Override
    public Location clone() {
        return new Location(this.scene, super.clone());
    }

    @Override
    public String toString() {
        return String.format("%s:%s,%s,%s", this.scene.getId(), this.getX(), this.getY(), this.getZ());
    }
}
