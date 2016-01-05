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
package org.apache.brooklyn.test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * A utility class containing tests on Entities.
 *
 * @deprecated since 0.9.0. Prefer core assertions class org.apache.brooklyn.core.entity.EntityAsserts.
 */
@Deprecated
public class EntityTestUtils {

//    public static LocalManagementContext newManagementContext() { return new LocalManagementContextForTests(); }
    
    // TODO Delete methods from TestUtils, to just have them here (or switch so TestUtils delegates here,
    // and deprecate methods in TestUtils until deleted).
    
    public static <T> void assertAttributeEquals(Entity entity, AttributeSensor<T> attribute, T expected) {
        assertEquals(entity.getAttribute(attribute), expected, "entity=" + entity + "; attribute=" + attribute);
    }
    
    public static <T> void assertConfigEquals(Entity entity, ConfigKey<T> configKey, T expected) {
        assertEquals(entity.getConfig(configKey), expected, "entity=" + entity + "; configKey=" + configKey);
    }
    
    public static <T> void assertAttributeEqualsEventually(final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        assertAttributeEqualsEventually(Maps.newLinkedHashMap(), entity, attribute, expected);
    }

    public static <T> void assertAttributeEqualsEventually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        // Not using assertAttributeEventually(predicate) so get nicer error message
        Asserts.succeedsEventually((Map) flags, new Runnable() {
            @Override
            public void run() {
                assertAttributeEquals(entity, attribute, expected);
            }
        });
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
        Asserts.succeedsEventually((Map) flags, new Runnable() {
            @Override
            public void run() {
                assertTrue(predicate.apply(entity));
            }
        });
    }

    public static <T> void assertAttributeEqualsContinually(final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        assertAttributeEqualsContinually(Maps.newLinkedHashMap(), entity, attribute, expected);
    }
    
    public static <T> void assertAttributeEqualsContinually(Map<?,?> flags, final Entity entity, final AttributeSensor<T> attribute, final T expected) {
        Asserts.succeedsContinually(flags, new Runnable() {
            @Override
            public void run() {
                assertAttributeEquals(entity, attribute, expected);
            }
        });
    }

    public static void assertGroupSizeEqualsEventually(final Group group, int expected) {
        assertGroupSizeEqualsEventually(ImmutableMap.of(), group, expected);
    }
    
    public static void assertGroupSizeEqualsEventually(Map<?,?> flags, final Group group, final int expected) {
        Asserts.succeedsEventually((Map) flags, new Runnable() {
            @Override
            public void run() {
                Collection<Entity> members = group.getMembers();
                assertEquals(members.size(), expected, "members=" + members);
            }
        });
    }

    /** checks that the entity's value for this attribute changes, by registering a subscription and checking the value */
    public static void assertAttributeChangesEventually(final Entity entity, final AttributeSensor<?> attribute) {
        final Object origValue = entity.getAttribute(attribute);
        final AtomicBoolean changed = new AtomicBoolean();
        SubscriptionHandle handle = entity.subscriptions().subscribe(entity, attribute, new SensorEventListener<Object>() {
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
            entity.subscriptions().unsubscribe(entity, handle);
        }
    }
    
    /** alternate version of {@link #assertAttributeChangesEventually(Entity, AttributeSensor)} not using subscriptions and 
     * with simpler code, for comparison */
    @Beta
    public static <T> void assertAttributeChangesEventually2(final Entity entity, final AttributeSensor<T> attribute) {
        assertAttributeEventually(entity, attribute,
                Predicates.not(Predicates.equalTo(entity.getAttribute(attribute))));
    }

    @Beta
    public static <T> void assertAttributeNever(final Entity entity, final AttributeSensor<T> attribute, T... disallowed) {
        final Set<T> reject = Sets.newHashSet(disallowed);
        Asserts.succeedsContinually(new Runnable() {
            @Override
            public void run() {
                T val = entity.getAttribute(attribute);
                assertFalse(reject.contains(val),
                        "Attribute " + attribute + " on " + entity + " has disallowed value " + val);
            }
        });
    }

}
