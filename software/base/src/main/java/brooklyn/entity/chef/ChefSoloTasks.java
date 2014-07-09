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
package brooklyn.entity.chef;

import java.util.Map;

import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.management.TaskFactory;
import brooklyn.util.ssh.BashCommands;

import com.google.common.annotations.Beta;

@Beta
public class ChefSoloTasks {

    public static TaskFactory<?> installChef(String chefDirectory, boolean force) {
        // TODO check on entity whether it is chef _server_
        String installCmd = cdAndRun(chefDirectory, ChefBashCommands.INSTALL_FROM_OPSCODE);
        if (!force) installCmd = BashCommands.alternatives("which chef-solo", installCmd);
        return SshEffectorTasks.ssh(installCmd).summary("install chef");
    }

    public static TaskFactory<?> installCookbooks(final String chefDirectory, final Map<String,String> cookbooksAndUrls, final boolean force) {
        return ChefTasks.installCookbooks(chefDirectory, cookbooksAndUrls, force);
    }

    public static TaskFactory<?> installCookbook(String chefDirectory, String cookbookName, String cookbookArchiveUrl, boolean force) {
        return ChefTasks.installCookbook(chefDirectory, cookbookName, cookbookArchiveUrl, force);
    }

    protected static String cdAndRun(String targetDirectory, String command) {
        return BashCommands.chain("mkdir -p "+targetDirectory,
                "cd "+targetDirectory,
                command);
    }

    public static TaskFactory<?> buildChefFile(String runDirectory, String chefDirectory, String phase, Iterable<? extends String> runList,
            Map<String, Object> optionalAttributes) {
        return ChefTasks.buildChefFile(runDirectory, chefDirectory, phase, runList, optionalAttributes);
    }

    public static TaskFactory<?> runChef(String runDir, String phase) {
        return runChef(runDir, phase, false);
    }
    /** see {@link ChefConfig#CHEF_RUN_CONVERGE_TWICE} for background on why 'twice' is available */
    public static TaskFactory<?> runChef(String runDir, String phase, Boolean twice) {
        String cmd = "sudo chef-solo -c "+phase+".rb -j "+phase+".json -ldebug";
        if (twice!=null && twice) cmd = BashCommands.alternatives(cmd, cmd);

        return SshEffectorTasks.ssh(cdAndRun(runDir, cmd)).
                summary("run chef for "+phase).requiringExitCodeZero();
    }
    
}
