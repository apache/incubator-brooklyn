package brooklyn.entity.basic;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Function;

// TODO Move to core
public class EntityFunctions {

    public static <T> Function<Entity, T> attribute(final AttributeSensor<T> attribute) {
        return new Function<Entity, T>() {
            @Override public T apply(Entity input) {
                return (input == null ? null : input.getAttribute(attribute));
            }
        };
    }
    
    public static <T> Function<Entity, T> config(final ConfigKey<T> key) {
        return new Function<Entity, T>() {
            @Override public T apply(Entity input) {
                return (input == null ? null : input.getConfig(key));
            }
        };
    }
    
    public static Function<Entity, String> displayName() {
        return new Function<Entity, String>() {
            @Override public String apply(Entity input) {
                return (input == null ? null : input.getDisplayName());
            }
        };
    }
    
    public static Function<Entity, String> id() {
        return new Function<Entity, String>() {
            @Override public String apply(Entity input) {
                return (input == null ? null : input.getId());
            }
        };
    }
}
