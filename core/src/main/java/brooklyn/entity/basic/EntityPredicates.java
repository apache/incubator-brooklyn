package brooklyn.entity.basic;

import javax.annotation.Nullable;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;

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
}
