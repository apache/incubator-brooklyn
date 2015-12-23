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
package org.apache.brooklyn.test.entity;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic;
import org.apache.brooklyn.entity.java.VanillaJavaApp;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcessDriverLifecycleEffectorTasks;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mock web application server entity for testing.
 */
@ImplementedBy(TestJavaWebAppEntityImpl.class)
public interface TestJavaWebAppEntity extends VanillaJavaApp, WebAppService, EntityLocal {

    /**
     * Injects the test entity's customised lifecycle tasks.
     */
    ConfigKey<SoftwareProcessDriverLifecycleEffectorTasks> LIFECYCLE_EFFECTOR_TASKS = ConfigKeys.newConfigKeyWithDefault(
            SoftwareProcess.LIFECYCLE_EFFECTOR_TASKS,
            new TestJavaWebAppEntityLifecycleTasks());

    void spoofRequest();
    int getA();
    int getB();
    int getC();

    static class TestJavaWebAppEntityLifecycleTasks extends SoftwareProcessDriverLifecycleEffectorTasks {
        private static final Logger LOG = LoggerFactory.getLogger(TestJavaWebAppEntityLifecycleTasks.class);

        @Override
        public void start(java.util.Collection<? extends Location> locations) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STARTING);
            LOG.trace("Starting {}", this);
            entity().sensors().set(SERVICE_PROCESS_IS_RUNNING, true);
            entity().sensors().set(Attributes.SERVICE_UP, true);
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.RUNNING);
        }

        @Override
        public void stop(ConfigBag parameters) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPING);
            LOG.trace("Stopping {}", this);
            entity().sensors().set(Attributes.SERVICE_UP, false);
            entity().sensors().set(SERVICE_PROCESS_IS_RUNNING, false);
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPED);
        }
    }

}
