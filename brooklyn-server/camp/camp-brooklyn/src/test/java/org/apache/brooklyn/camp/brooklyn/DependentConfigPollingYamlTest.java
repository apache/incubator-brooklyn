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
package org.apache.brooklyn.camp.brooklyn;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

@Test
public class DependentConfigPollingYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(DependentConfigPollingYamlTest.class);
    
    private ExecutorService executor;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() {
        super.setUp();
        executor = Executors.newCachedThreadPool();
    }
            
    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() {
        if (executor != null) executor.shutdownNow();
        super.tearDown();
    }
            
    // Test for BROOKLYN-214. Previously, the brief Tasks.resolving would cause a thread to be
    // leaked. This was because it would call into BrooklynDslDeferredSupplier.get, which would
    // wait on a synchronized block and thus not be interruptible - the thread would be consumed
    // forever, until the attributeWhenReady returned true!
    //
    // Integration test, because takes several seconds.
    @Test(groups="Integration")
    public void testResolveAttributeWhenReadyWithTimeoutDoesNotLeaveThreadRunning() throws Exception {
        String yaml = Joiner.on("\n").join(
                "services:",
                "- type: org.apache.brooklyn.core.test.entity.TestEntity",
                "  id: myentity",
                "  brooklyn.config:",
                "    test.confName: $brooklyn:entity(\"myentity\").attributeWhenReady(\"mysensor\")");
        
        final Entity app = createAndStartApplication(yaml);
        final TestEntity entity = (TestEntity) Iterables.getOnlyElement(app.getChildren());

        // Cause a thread to block, getting the config - previousy (before fixing 214) this would be in
        // the synchronized block if BrooklynDslDeferredSupplier.get().
        // The sleep is to ensure we really did get into the locking code.
        executor.submit(new Callable<Object>() {
            public Object call() {
                return entity.config().get(TestEntity.CONF_NAME);
            }});
        Thread.sleep(100);
        
        // Try to resolve the value many times, each in its own task, but with a short timeout for each.
        final int numIterations = 20;
        final int preNumThreads = Thread.activeCount();
        
        for (int i = 0; i < numIterations; i++) {
            // Same as RestValueResolver.getImmediateValue
            Tasks.resolving(entity.config().getRaw(TestEntity.CONF_NAME).get())
                    .as(Object.class)
                    .defaultValue("UNRESOLVED")
                    .timeout(Duration.millis(100))
                    .context(entity)
                    .swallowExceptions()
                    .get();
        }

        // Confirm we haven't left threads behind.
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                int postNumThreads = Thread.activeCount();
                String msg = "pre="+preNumThreads+"; post="+postNumThreads+"; iterations="+numIterations;
                log.info(msg);
                assertTrue(postNumThreads < preNumThreads + (numIterations / 2), msg);
            }});
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
