package brooklyn.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import javax.annotation.Nullable;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

public class EntityTestUtils {

	// TODO Delete methods from TestUtils, to just have them here (or switch so TestUtils delegates here,
	// and deprecate methods in TestUtils until deleted).
	
    public static <T> void assertAttributeEquals(Entity entity, AttributeSensor<T> attribute, T expected) {
        assertEquals(entity.getAttribute(attribute), expected);
    }
    
    public static <T> void assertConfigEquals(Entity entity, ConfigKey<T> configKey, T expected) {
        assertEquals(entity.getConfig(configKey), expected);
    }
    
    public static <T> void assertAttributeEqualsEventually(final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        assertAttributeEqualsEventually(Maps.newLinkedHashMap(), entity, attribute, expected);
    }

    public static <T> void assertAttributeEqualsEventually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        TestUtils.executeUntilSucceeds(flags, new Runnable() {
                public void run() {
                    assertAttributeEquals(entity, attribute, expected);
                }});
    }

    public static <T> void assertAttributeEventuallyNonNull(final Entity entity, final AttributeSensor<T> attribute) {
        assertAttributeEventuallyNonNull(Maps.newLinkedHashMap(), entity, attribute);
    }

    public static <T> void assertAttributeEventuallyNonNull(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute) {
        assertPredicateEventuallyTrue(flags, entity, new Predicate<Entity>() {
            @Override public boolean apply(Entity input) {
                return entity.getAttribute(attribute) != null;
            }
        });
    }

    public static <T extends Entity> void assertPredicateEventuallyTrue(final T entity, final Predicate<? super T> predicate) {
        assertPredicateEventuallyTrue(Maps.newLinkedHashMap(), entity, predicate);
    }

    public static <T extends Entity> void assertPredicateEventuallyTrue(Map<?,?> flags, final T entity, final Predicate<? super T> predicate) {
        TestUtils.executeUntilSucceeds(flags, new Runnable() {
                public void run() {
                    assertTrue(predicate.apply(entity));
                }});
    }

    public static <T> void assertAttributeEqualsContinually(final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        assertAttributeEqualsContinually(Maps.newLinkedHashMap(), entity, attribute, expected);
    }
    
    public static <T> void assertAttributeEqualsContinually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        TestUtils.assertSucceedsContinually(new Runnable() {
                public void run() {
                    assertAttributeEquals(entity, attribute, expected);
                }});
    }
}
