package me.toolify.backbone.bus.events;

/**
 * Created by Brandon on 9/25/13.
 */
public abstract class BusEvent {
    @Override
    public String toString() {
        return new StringBuilder("(bus event: ")
                .append(this.getClass().toString())
                .append(")").toString();
    }
}
