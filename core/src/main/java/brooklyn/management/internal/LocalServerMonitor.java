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
package brooklyn.management.internal;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.BrooklynServerPaths;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.os.Os;
import brooklyn.util.task.ScheduledTask;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.ByteSizeStrings;
import brooklyn.util.time.Duration;

import com.google.api.client.repackaged.com.google.common.base.Joiner;
import com.google.api.client.util.Sets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class LocalServerMonitor implements ServerMonitor {

    private static final Logger LOG = LoggerFactory.getLogger(LocalServerMonitor.class);
    private final ManagementContext mgmt;
    private Map<Warning, String> warnings = Maps.newHashMap();

    @Override
    public Collection<String> getWarnings() {
        return ImmutableList.copyOf(warnings.values());
    }

    private enum Warning {
        DISK_FREE
    }

    public LocalServerMonitor(ManagementContext mgmt) {
        this.mgmt = mgmt;

        Callable<Task<?>> taskFactory = new Callable<Task<?>>() {
            @Override public Task<?> call() {
                return Tasks.builder().dynamic(false).body(new Runnable() {
                        @Override
                        public void run() {
                            checkServer();
                        }
                    })
                    .name("Server monitor task")
                    .tag(BrooklynTaskTags.TRANSIENT_TASK_TAG)
                    .description("Periodically checks disk space against the configured threshold")
                    .build();
            }
        };

        ScheduledTask checkServerSchedule = new ScheduledTask(taskFactory)
                .period(Duration.ONE_SECOND);
        getManagementContext().getServerExecutionContext().submit(checkServerSchedule);
    }

    protected ManagementContext getManagementContext() {
        return mgmt;
    }

    private void checkServer() {
        // File.listRoots() may contain roots that we're not interested in
        long threshold = ByteSizeStrings.parse(getManagementContext().getConfig().getConfig(BrooklynServerConfig.DISK_FREE_WARN_THRESHOLD), "Mb");
        Set<File> rootsToCheck = Sets.newHashSet();
        Collection<String> drivesBelowThreshold = Sets.newHashSet();
        addFile(rootsToCheck, BrooklynServerPaths.getMgmtBaseDir(mgmt));
        addFile(rootsToCheck, Os.newTempDir("checkServer").getAbsolutePath());
        // TODO: Persistence directory iff we're persisting to file
        for (File root : rootsToCheck) {
            if (root.getFreeSpace() <= threshold) {
                drivesBelowThreshold.add(root.getAbsolutePath());
            }
        }
        if (drivesBelowThreshold.size() > 0) {
            String drives = Joiner.on(", ").join(drivesBelowThreshold);
            String message = String.format("Drive space on one of the following drive(s) is below the configured threshold (%s): %s",
                ByteSizeStrings.java().makeSizeString(threshold), drives);
            warnings.put(Warning.DISK_FREE, message);
            LOG.warn(message);
        } else {
            warnings.remove(Warning.DISK_FREE);
        }
    }

    private void addFile(Set<File> roots, String path) {
        roots.add(Paths.get(path).getRoot().toFile());
    }
}
