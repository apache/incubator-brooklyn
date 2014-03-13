package brooklyn.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
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

    public static <T> void assertAttributeEqualsEventually(Map<?,?> flags, Entity entity, AttributeSensor<T> attribute, T expected) {
        assertAttributeEventually(flags, entity, attribute, Predicates.equalTo(expected));
    }

    public static <T> void assertAttributeEventuallyNonNull(final Entity entity, final AttributeSensor<T> attribute) {
        assertAttributeEventuallyNonNull(Maps.newLinkedHashMap(), entity, attribute);
    }

    public static <T> void assertAttributeEventuallyNonNull(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute) {
        assertAttributeEventually(flags, entity, attribute, Predicates.notNull());
    }

    public static <T> void assertAttributeEventually(final Entity entity, final AttributeSensor<T> attribute, Predicate<? super T> predicate) {
        assertAttributeEventually(ImmutableMap.of(), entity, attribute, predicate);
    }
    
    public static <T> void assertAttributeEventually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final Predicate<? super T> predicate) {
        assertPredicateEventuallyTrue(flags, entity, new Predicate<Entity>() {
            @Override public boolean apply(Entity input) {
                return predicate.apply(entity.getAttribute(attribute));
            }
        });
    }

    public static <T extends Entity> void assertPredicateEventuallyTrue(final T entity, final Predicate<? super T> predicate) {
        assertPredicateEventuallyTrue(Maps.newLinkedHashMap(), entity, predicate);
    }

    public static <T extends Entity> void assertPredicateEventuallyTrue(Map<?,?> flags, final T entity, final Predicate<? super T> predicate) {
        Asserts.succeedsEventually((Map)flags, new Runnable() {
                @Override public void run() {
                    assertTrue(predicate.apply(entity));
                }});
    }

    public static <T> void assertAttributeEqualsContinually(final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        assertAttributeEqualsContinually(Maps.newLinkedHashMap(), entity, attribute, expected);
    }
    
    public static <T> void assertAttributeEqualsContinually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        Asserts.succeedsContinually(flags, new Runnable() {
                @Override public void run() {
                    assertAttributeEquals(entity, attribute, expected);
                }});
    }

    public static void assertGroupSizeEqualsEventually(final Group group, int expected) {
        assertGroupSizeEqualsEventually(ImmutableMap.of(), group, expected);
    }
    
    public static void assertGroupSizeEqualsEventually(Map<?,?> flags, final Group group, final int expected) {
        assertPredicateEventuallyTrue(flags, group, new Predicate<Group>() {
            @Override public boolean apply(Group input) {
                return group.getMembers().size() == expected;
            }
        });
    }
}
