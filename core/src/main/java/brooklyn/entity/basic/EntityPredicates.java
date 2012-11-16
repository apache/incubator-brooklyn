package brooklyn.entity.basic;

import javax.annotation.Nullable;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;

public class EntityPredicates {

    public static <T> Predicate<Entity> attributeEqualTo(final AttributeSensor<T> attribute, final T val) {
        return new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return Objects.equal(input.getAttribute(attribute), val);
            }
        };
    }
    
    /**
     * Create a predicate that matches any entity who has an exact match for the given location
     * (i.e. <code>entity.getLocations().contains(location)</code>).
     */
    public static <T> Predicate<Entity> withLocation(final Location location) {
        return new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && input.getLocations().contains(location);
            }
        };
    }
}
