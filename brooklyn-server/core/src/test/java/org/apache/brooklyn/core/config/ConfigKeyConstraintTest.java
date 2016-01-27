/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.core.config;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.objs.BrooklynObjectPredicate;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.apache.brooklyn.core.test.policy.TestPolicy;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.javalang.JavaClassNames;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Range;

public class ConfigKeyConstraintTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(ConfigKeyConstraintTest.class);
    
    // ----------- Setup -----------------------------------------------------------------------------------------------

    @ImplementedBy(EntityWithNonNullConstraintImpl.class)
    public static interface EntityWithNonNullConstraint extends TestEntity {
        ConfigKey<Object> NON_NULL_CONFIG = ConfigKeys.builder(Object.class)
                .name("test.conf.non-null.without-default")
                .description("Configuration key that must not be null")
                .constraint(Predicates.notNull())
                .build();
    }
    public static class EntityWithNonNullConstraintImpl extends TestEntityImpl implements EntityWithNonNullConstraint {
    }

    @ImplementedBy(EntityWithNonNullConstraintWithNonNullDefaultImpl.class)
    public static interface EntityWithNonNullConstraintWithNonNullDefault extends TestEntity {
        ConfigKey<Object> NON_NULL_WITH_DEFAULT = ConfigKeys.builder(Object.class)
                .name("test.conf.non-null.with-default")
                .description("Configuration key that must not be null")
                .defaultValue(new Object())
                .constraint(Predicates.notNull())
                .build();
    }
    public static class EntityWithNonNullConstraintWithNonNullDefaultImpl extends TestEntityImpl implements EntityWithNonNullConstraintWithNonNullDefault {
    }

    @ImplementedBy(EntityRequiringConfigKeyInRangeImpl.class)
    public static interface EntityRequiringConfigKeyInRange extends TestEntity {
        ConfigKey<Integer> RANGE = ConfigKeys.builder(Integer.class)
                .name("test.conf.range")
                .description("Configuration key that must not be between zero and nine")
                .defaultValue(0)
                .constraint(Range.closed(0, 9))
                .build();
    }
    public static class EntityRequiringConfigKeyInRangeImpl extends TestEntityImpl implements EntityRequiringConfigKeyInRange {
    }

    @ImplementedBy(EntityProvidingDefaultValueForConfigKeyInRangeImpl.class)
    public static interface EntityProvidingDefaultValueForConfigKeyInRange extends EntityRequiringConfigKeyInRange {
        ConfigKey<Integer> REVISED_RANGE = ConfigKeys.newConfigKeyWithDefault(RANGE, -1);
    }
    public static class EntityProvidingDefaultValueForConfigKeyInRangeImpl extends TestEntityImpl implements EntityProvidingDefaultValueForConfigKeyInRange {
    }

    @ImplementedBy(EntityWithContextAwareConstraintImpl.class)
    public static interface EntityWithContextAwareConstraint extends TestEntity {
        ConfigKey<String> MUST_BE_DISPLAY_NAME = ConfigKeys.builder(String.class)
                .name("must-be-display-name")
                .description("Configuration key that must not be null")
                .constraint(new MatchesEntityDisplayNamePredicate())
                .build();
    }
    public static class EntityWithContextAwareConstraintImpl extends TestEntityImpl implements EntityWithContextAwareConstraint {
    }

    public static class PolicyWithConfigConstraint extends AbstractPolicy {
        public static final ConfigKey<Object> NON_NULL_CONFIG = ConfigKeys.builder(Object.class)
                .name("test.policy.non-null")
                .description("Configuration key that must not be null")
                .constraint(Predicates.notNull())
                .build();
    }

    public static class EnricherWithConfigConstraint extends AbstractEnricher {
        public static final ConfigKey<String> PATTERN = ConfigKeys.builder(String.class)
                .name("test.enricher.regex")
                .description("Must match a valid IPv4 address")
                .constraint(Predicates.containsPattern(Networking.VALID_IP_ADDRESS_REGEX))
                .build();
    }

    private static class MatchesEntityDisplayNamePredicate implements BrooklynObjectPredicate<String> {
        @Override
        public boolean apply(String input) {
            return false;
        }

        @Override
        public boolean apply(String input, BrooklynObject context) {
            return context != null && context.getDisplayName().equals(input);
        }
    }

    // ----------- Tests -----------------------------------------------------------------------------------------------

    @Test
    public void testExceptionWhenEntityHasNullConfig() {
        try {
            app.createAndManageChild(EntitySpec.create(EntityWithNonNullConstraint.class));
            fail("Expected exception when managing entity with missing config");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t);
        }
    }

    @Test
    public void testNoExceptionWhenEntityHasValueForRequiredConfig() {
        app.createAndManageChild(EntitySpec.create(EntityWithNonNullConstraint.class)
                .configure(EntityWithNonNullConstraint.NON_NULL_CONFIG, new Object()));
    }

    @Test
    public void testNoExceptionWhenDefaultValueIsValid() {
        app.createAndManageChild(EntitySpec.create(EntityRequiringConfigKeyInRange.class));
    }

    @Test
    public void testExceptionWhenSubclassSetsInvalidDefaultValue() {
        try {
            app.createAndManageChild(EntitySpec.create(EntityProvidingDefaultValueForConfigKeyInRange.class));
            fail("Expected exception when managing entity setting invalid default value");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t, "Original exception was: " + Exceptions.collapseText(e));
        }
    }

    @Test
    public void testExceptionIsThrownWhenUserNullsConfigWithNonNullDefault() {
        try {
            app.createAndManageChild(EntitySpec.create(EntityWithNonNullConstraintWithNonNullDefault.class)
                    .configure(EntityWithNonNullConstraintWithNonNullDefault.NON_NULL_WITH_DEFAULT, (Object) null));
            fail("Expected exception when config key set to null");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t, "Original exception was: " + Exceptions.collapseText(e));
        }
    }

    @Test
    public void testExceptionWhenValueSetByName() {
        try {
            app.createAndManageChild(EntitySpec.create(EntityRequiringConfigKeyInRange.class)
                    .configure(ImmutableMap.of("test.conf.range", -1)));
            fail("Expected exception when managing entity with invalid config");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t, "Original exception was: " + Exceptions.collapseText(e));
        }
    }

    @Test
    public void testExceptionWhenAppGrandchildHasInvalidConfig() {
        app.start(ImmutableList.of(app.newSimulatedLocation()));
        TestEntity testEntity = app.addChild(EntitySpec.create(TestEntity.class));
        try {
            testEntity.addChild(EntitySpec.create(EntityRequiringConfigKeyInRange.class)
                .configure(EntityRequiringConfigKeyInRange.RANGE, -1));
            fail("Expected exception when managing child with invalid config");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t, "Original exception was: " + Exceptions.collapseText(e));
        }
    }

    // Test fails because config keys that are not on an object's interfaces cannot be checked automatically.
    @Test(enabled = false)
    public void testExceptionWhenPolicyHasNullForeignConfig() {
        Policy p = mgmt.getEntityManager().createPolicy(PolicySpec.create(TestPolicy.class)
                .configure(EntityWithNonNullConstraint.NON_NULL_CONFIG, (Object) null));
        try {
            ConfigConstraints.assertValid(p);
            fail("Expected exception when validating policy with missing config");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t, "Original exception was: " + Exceptions.collapseText(e));
        }
    }

    @Test
    public void testExceptionWhenPolicyHasInvalidConfig() {
        try {
            mgmt.getEntityManager().createPolicy(PolicySpec.create(PolicyWithConfigConstraint.class)
                    .configure(PolicyWithConfigConstraint.NON_NULL_CONFIG, (Object) null));
            fail("Expected exception when creating policy with missing config");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t, "Original exception was: " + Exceptions.collapseText(e));
        }
    }

    @Test
    public void testExceptionWhenEnricherHasInvalidConfig() {
        try {
            mgmt.getEntityManager().createEnricher(EnricherSpec.create(EnricherWithConfigConstraint.class)
                    .configure(EnricherWithConfigConstraint.PATTERN, "123.123.256.10"));
            fail("Expected exception when creating enricher with invalid config");
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t, "Original exception was: " + Exceptions.collapseText(e));
        }
    }

    @Test
    public void testDefaultValueDoesNotNeedToObeyConstraint() {
        ConfigKeys.builder(String.class)
                .name("foo")
                .defaultValue("a")
                .constraint(Predicates.equalTo("b"))
                .build();
    }

    @Test
    public void testIsValidWithBadlyBehavedPredicate() {
        ConfigKey<String> key = ConfigKeys.builder(String.class)
                .name("foo")
                .constraint(new Predicate<String>() {
                    @Override
                    public boolean apply(String input) {
                        throw new RuntimeException("It's my day off");
                    }
                })
                .build();
        // i.e. no exception.
        assertFalse(key.isValueValid("abc"));
    }

    @Test
    public void testContextAwarePredicateInformedOfEntity() {
        try {
            app.createAndManageChild(EntitySpec.create(EntityWithContextAwareConstraint.class)
                    .displayName("Mr. Big")
                    .configure("must-be-display-name", "Mr. Bag"));
            fail("Expected exception when managing entity with incorrect config");
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, ConstraintViolationException.class);
        }
    }

    @Test
    public void testQuickFutureResolved() {
        // Result of task is -1, outside of the range specified by the config key.
        try {
            EntityRequiringConfigKeyInRange child = app.createAndManageChild(EntitySpec.create(EntityRequiringConfigKeyInRange.class)
                    .configure(EntityRequiringConfigKeyInRange.RANGE, sleepingTask(Duration.ZERO, -1)));
            // may or may not fail above, depending on speed, but should fail if assert after forcing resolution
            Object value = child.getConfig(EntityRequiringConfigKeyInRange.RANGE);
            // NB the call above does not currently/necessarily apply validation
            log.debug(JavaClassNames.niceClassAndMethod()+" got "+value+" for "+EntityRequiringConfigKeyInRange.RANGE+", now explicitly validating");
            ConfigConstraints.assertValid(child);
            fail("Expected exception when managing entity with incorrect config; instead passed assertion and got: "+value);
        } catch (Exception e) {
            Asserts.expectedFailureOfType(e, ConstraintViolationException.class);
        }
    }

    @Test
    public void testSlowFutureNotResolved() {
        // i.e. no exception because task is too slow to resolve.
        app.createAndManageChild(EntitySpec.create(EntityRequiringConfigKeyInRange.class)
                .configure(EntityRequiringConfigKeyInRange.RANGE, sleepingTask(Duration.PRACTICALLY_FOREVER, -1)));
    }

    private static <T> Task<T> sleepingTask(final Duration delay, final T result) {
        return Tasks.<T>builder()
                .body(new Callable<T>() {
                    @Override public T call() throws Exception {
                        Time.sleep(delay);
                        return result;
                    }
                })
                .build();
    }

    // Supplies an entity, a policy and a location.
    @DataProvider(name = "brooklynObjects")
    public Object[][] createBrooklynObjects() throws Exception {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
            .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, shouldSkipOnBoxBaseDirResolution());
        setUp();
        TestApplication app = mgmt.getEntityManager().createEntity(appSpec);

        EntityRequiringConfigKeyInRange entity = app.createAndManageChild(EntitySpec.create(EntityRequiringConfigKeyInRange.class)
                .configure(EntityRequiringConfigKeyInRange.RANGE, 5));
        Policy policy = entity.policies().add(PolicySpec.create(TestPolicy.class));
        Location location = app.newSimulatedLocation();
        return new Object[][]{{entity}, {policy}, {location}};
    }

    @Test(dataProvider = "brooklynObjects")
    public void testCannotUpdateConfigToInvalidValue(BrooklynObject object) {
        try {
            object.config().set(EntityRequiringConfigKeyInRange.RANGE, -1);
            fail("Expected exception when calling config().set with invalid value on " + object);
        } catch (Exception e) {
            Throwable t = Exceptions.getFirstThrowableOfType(e, ConstraintViolationException.class);
            assertNotNull(t, "Original exception was: " + Exceptions.collapseText(e));
        }
    }

}
