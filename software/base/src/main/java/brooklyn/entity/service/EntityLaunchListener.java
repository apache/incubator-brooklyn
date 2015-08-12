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
package brooklyn.entity.service;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.management.ExecutionManager;
import org.apache.brooklyn.management.Task;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.BrooklynTaskTags.EffectorCallTag;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.util.task.Tasks;

public class EntityLaunchListener implements Runnable, SensorEventListener<Lifecycle> {
    private static final String SSH_LAUNCH_TASK_PREFIX = "ssh: launching";
    private static final String LAUNCH_CHECK_SKIP_TAG = "system-service-update";

    private final AtomicReference<Task<?>> launchTaskRef = new AtomicReference<Task<?>>();
    private final SystemServiceEnricher enricher;

    public EntityLaunchListener(SystemServiceEnricher enricher) {
        this.enricher = checkNotNull(enricher, "enricher");
    }

    @Override
    public void onEvent(SensorEvent<Lifecycle> event) {
        if (event.getValue() == Lifecycle.RUNNING) {
            Task<?>launchTask = getLatestLaunchTask(enricher.getEntity());
            if (launchTask != null) {
                launchTaskRef.set(launchTask);
                if (!launchTask.isDone()) {
                    launchTask.addListener(this, enricher.getEntityExecutionContext());
                }
                if (launchTask.isDone()) {
                    run();
                }
            }
        }
    }

    @Override
    public void run() {
        Task<?> launchTask = launchTaskRef.getAndSet(null);
        if (launchTask == null) return;
        if (launchTask.isError()) return;
        enricher.onLaunched(launchTask);
    }

    private Task<?> getLatestLaunchTask(Entity entity) {
        Task<?> startEffector = null;
        ExecutionManager executionmgr = enricher.getManagementContext().getExecutionManager();
        Set<Task<?>> entityTasks = BrooklynTaskTags.getTasksInEntityContext(executionmgr, entity);
        for (Task<?> t : entityTasks) {
            if (BrooklynTaskTags.isEffectorTask(t)) {
                EffectorCallTag effectorTag = BrooklynTaskTags.getEffectorCallTag(t, false);
                if (SystemServiceEnricher.LAUNCH_EFFECTOR_NAMES.contains(effectorTag.getEffectorName()) &&
                        !BrooklynTaskTags.hasTag(t, LAUNCH_CHECK_SKIP_TAG)) {
                    if (startEffector == null || startEffector.getStartTimeUtc() < t.getStartTimeUtc()) {
                        startEffector = t;
                    }
                    BrooklynTaskTags.addTagDynamically(t, LAUNCH_CHECK_SKIP_TAG);
                }
            }
        }
        if (startEffector != null) {
            Task<?> launchTask = findSshLaunchChild(startEffector);
            if (launchTask != null) {
                return launchTask;
            }
        }
        return null;
    }

    private Task<?> findSshLaunchChild(Task<?> t) {
        Iterable<Task<?>> children = Tasks.children(t);
        for (Task<?> c : children) {
            if (c.getDisplayName().startsWith(SSH_LAUNCH_TASK_PREFIX)) {
                return c;
            }
        }
        for (Task<?> c : children) {
            Task<?> launchTask = findSshLaunchChild(c);
            if (launchTask != null) {
                return launchTask;
            }
        }
        return null;
    }
}