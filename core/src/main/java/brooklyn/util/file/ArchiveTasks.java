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
package brooklyn.util.file;

import java.util.Map;

import org.apache.brooklyn.management.TaskAdaptable;
import org.apache.brooklyn.management.TaskFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.net.Urls;
import brooklyn.util.task.Tasks;

public class ArchiveTasks {

    /** as {@link #deploy(ResourceUtils, Map, String, SshMachineLocation, String, String, String)} with the most common parameters */
    public static TaskFactory<?> deploy(final ResourceUtils optionalResolver, final String archiveUrl, final SshMachineLocation machine, final String destDir) {
        return deploy(optionalResolver, null, archiveUrl, machine, destDir, false, null, null);
    }
    
    /** returns a task which installs and unpacks the given archive, as per {@link ArchiveUtils#deploy(ResourceUtils, Map, String, SshMachineLocation, String, String, String)};
     * if allowNonarchivesOrKeepArchiveAfterDeploy is false, this task will fail if the item is not an archive;
     * in cases where the download type is not clear in the URL but is known by the caller, supply a optionalDestFile including the appropriate file extension */
    public static TaskFactory<?> deploy(final ResourceUtils resolver, final Map<String, ?> props, final String archiveUrl, final SshMachineLocation machine, final String destDir, final boolean allowNonarchivesOrKeepArchiveAfterDeploy, final String optionalTmpDir, final String optionalDestFile) {
        return new TaskFactory<TaskAdaptable<?>>() {
            @Override
            public TaskAdaptable<?> newTask() {
                return Tasks.<Void>builder().name("deploying "+Urls.getBasename(archiveUrl)).description("installing "+archiveUrl+" and unpacking to "+destDir).body(new Runnable() {
                    @Override
                    public void run() {
                        boolean unpacked = ArchiveUtils.deploy(resolver, props, archiveUrl, machine, destDir, allowNonarchivesOrKeepArchiveAfterDeploy, optionalTmpDir, optionalDestFile);
                        if (!unpacked && !allowNonarchivesOrKeepArchiveAfterDeploy) {
                            throw new IllegalStateException("Unable to unpack archive from "+archiveUrl+"; not able to infer archive type");
                        }
                    }
                }).build();
            }
        };
    }
    
}
