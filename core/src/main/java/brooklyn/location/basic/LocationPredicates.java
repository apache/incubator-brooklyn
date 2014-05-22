package brooklyn.location.basic;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;

public class LocationPredicates {

    public static <T> Predicate<Location> idEqualTo(final T val) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return Objects.equal(input.getId(), val);
            }
        };
    }
    
    public static <T> Predicate<Location> displayNameEqualTo(final T val) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return Objects.equal(input.getDisplayName(), val);
            }
        };
    }
    
    public static <T> Predicate<Location> configEqualTo(final ConfigKey<T> configKey, final T val) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(Location input) {
                return Objects.equal(input.getConfig(configKey), val);
            }
        };
    }

    public static <T> Predicate<Location> configEqualTo(final HasConfigKey<T> configKey, final T val) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(Location input) {
                return Objects.equal(input.getConfig(configKey), val);
            }
        };
    }

    public static <T> Predicate<Location> isChildOf(final Location parent) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return Objects.equal(input.getParent(), parent);
            }
        };
    }

    public static <T> Predicate<Location> managed() {
        return new Predicate<Location>() {
            @Override
            public boolean apply(@Nullable Location input) {
                return input != null && Entities.isManaged(input);
            }
        };
    }

}
