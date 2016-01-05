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

import java.io.Reader;
import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.camp.spi.Assembly;
import org.apache.brooklyn.camp.spi.AssemblyTemplate;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.internal.BrooklynShutdownHooks;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;

/** convenience for launching YAML files directly */
@Beta
public abstract class YamlLauncherAbstract {

    private static final Logger log = LoggerFactory.getLogger(YamlLauncherAbstract.class);
       
    protected final BrooklynCampPlatformLauncherAbstract platformLauncher;

    protected final BrooklynCampPlatform platform;
    protected final ManagementContext brooklynMgmt;
    protected boolean shutdownAppsOnExit = false;

    public YamlLauncherAbstract() {
        this.platformLauncher = newPlatformLauncher();
        platformLauncher.launch();
        this.platform = platformLauncher.getCampPlatform();
        this.brooklynMgmt = platformLauncher.getBrooklynMgmt();
    }

    public ManagementContext getManagementContext() {
        return brooklynMgmt;
    }

    public boolean getShutdownAppsOnExit() {
        return shutdownAppsOnExit;
    }
    
    public void setShutdownAppsOnExit(boolean shutdownAppsOnExit) {
        this.shutdownAppsOnExit = shutdownAppsOnExit;
    }
    
    protected abstract BrooklynCampPlatformLauncherAbstract newPlatformLauncher();

    public Application launchAppYaml(String url) {
        return launchAppYaml(url, true);
    }

    public Application launchAppYaml(String url, boolean waitForTasksToComplete) {
        try {
            Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl(url));
            Application app = launchAppYaml(input, waitForTasksToComplete);
            log.info("Application started from YAML file "+url+": "+app);
            return app;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    public Application launchAppYaml(Reader input) {
        return launchAppYaml(input, true);
    }

    public Application launchAppYaml(Reader input, boolean waitForTasksToComplete) {
        try {
            AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

            Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
            Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
            log.info("Launching "+app);

            if (getShutdownAppsOnExit()) BrooklynShutdownHooks.invokeStopOnShutdown(app);

            if (waitForTasksToComplete) {
                Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
                log.info("Waiting on "+tasks.size()+" task(s)");
                for (Task<?> t: tasks) {
                    t.blockUntilEnded();
                }
            }

            log.info("Application started from YAML: "+app);
            Entities.dumpInfo(app);
            return (Application)app;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    /** kills all apps, web servers, and mgmt context
     * <p>
     * this launcher does not support being used again subsequently */
    public void destroyAll() {
        Entities.destroyAll(getManagementContext());
        try {
            platformLauncher.stopServers();
        } catch (Exception e) {
            log.warn("Unable to stop servers (ignoring): "+e);
        }
    }

}
