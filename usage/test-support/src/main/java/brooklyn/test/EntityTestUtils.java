/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionHandle;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

public class EntityTestUtils {

    // TODO would be nice to have this... perhaps moving this class, or perhaps this whole project, to core/src/test ?
//    public static LocalManagementContext newManagementContext() { return new LocalManagementContextForTests(); }
    
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
        // Not using assertAttributeEventually(predicate) so get nicer error message
        Asserts.succeedsEventually((Map)flags, new Runnable() {
            @Override public void run() {
                assertAttributeEquals(entity, attribute, expected);
            }});
    }

    public static <T> T assertAttributeEventuallyNonNull(final Entity entity, final AttributeSensor<T> attribute) {
        return assertAttributeEventuallyNonNull(Maps.newLinkedHashMap(), entity, attribute);
    }

    public static <T> T assertAttributeEventuallyNonNull(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute) {
        return assertAttributeEventually(flags, entity, attribute, Predicates.notNull());
    }

    public static <T> T assertAttributeEventually(final Entity entity, final AttributeSensor<T> attribute, Predicate<? super T> predicate) {
        return assertAttributeEventually(ImmutableMap.of(), entity, attribute, predicate);
    }
    
    public static <T> T assertAttributeEventually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final Predicate<? super T> predicate) {
        final AtomicReference<T> result = new AtomicReference<T>();
        Asserts.succeedsEventually((Map)flags, new Runnable() {
            @Override public void run() {
                T val = entity.getAttribute(attribute);
                assertTrue(predicate.apply(val), "val="+val);
                result.set(val);
            }});
        return result.get();
    }

    public static <T> T assertAttribute(final Entity entity, final AttributeSensor<T> attribute, final Predicate<? super T> predicate) {
        T val = entity.getAttribute(attribute);
        assertTrue(predicate.apply(val), "val="+val);
        return val;
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
        Asserts.succeedsEventually((Map)flags, new Runnable() {
            @Override public void run() {
                Collection<Entity> members = group.getMembers();
                assertEquals(members.size(), expected, "members="+members);
            }});
    }

    /** checks that the entity's value for this attribute changes, by registering a subscription and checking the value */
    public static void assertAttributeChangesEventually(final Entity entity, final AttributeSensor<?> attribute) {
        final Object origValue = entity.getAttribute(attribute);
        final AtomicBoolean changed = new AtomicBoolean();
        SubscriptionHandle handle = ((EntityLocal)entity).subscribe(entity, attribute, new SensorEventListener<Object>() {
            @Override public void onEvent(SensorEvent<Object> event) {
                if (!Objects.equal(origValue, event.getValue())) {
                    changed.set(true);
                }
            }});
        try {
            Asserts.succeedsEventually(new Runnable() {
                @Override public void run() {
                    assertTrue(changed.get(), entity+" -> "+attribute+" not changed");
                }});
        } finally {
            ((EntityLocal)entity).unsubscribe(entity, handle);
        }
    }
    
    /** alternate version of {@link #assertAttributeChangesEventually(Entity, AttributeSensor)} not using subscriptions and 
     * with simpler code, for comparison */
    @Beta
    public static <T> void assertAttributeChangesEventually2(final Entity entity, final AttributeSensor<T> attribute) {
        assertAttributeEventually(entity, attribute, 
            Predicates.not(Predicates.equalTo(entity.getAttribute(attribute))));
    }
    
}
