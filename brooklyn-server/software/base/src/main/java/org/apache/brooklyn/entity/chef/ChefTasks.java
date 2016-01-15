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
package org.apache.brooklyn.entity.chef;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.TaskAdaptable;
import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.file.ArchiveTasks;
import org.apache.brooklyn.util.core.file.ArchiveUtils.ArchiveType;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Beta
public class ChefTasks {

    private static final Logger log = LoggerFactory.getLogger(ChefTasks.class);
    
    public static TaskFactory<?> installChef(String chefDirectory, boolean force) {
        // TODO check on entity whether it is chef _server_
        String installCmd = cdAndRun(chefDirectory, ChefBashCommands.INSTALL_FROM_OPSCODE);
        if (!force) installCmd = BashCommands.alternatives("which chef-solo", installCmd);
        return SshEffectorTasks.ssh(installCmd).summary("install chef");
    }

    public static TaskFactory<?> installCookbooks(final String chefDirectory, final Map<String,String> cookbooksAndUrls, final boolean force) {
        return Tasks.<Void>builder().displayName("install "+(cookbooksAndUrls==null ? "0" : cookbooksAndUrls.size())+" cookbook"+Strings.s(cookbooksAndUrls)).body(
                new Runnable() {
                    public void run() {
                        Entity e = EffectorTasks.findEntity();
                        if (cookbooksAndUrls==null)
                            throw new IllegalStateException("No cookbooks defined to install at "+e);
                        for (String cookbook: cookbooksAndUrls.keySet())
                            DynamicTasks.queue(installCookbook(chefDirectory, cookbook, cookbooksAndUrls.get(cookbook), force));
                    }
                }).buildFactory();
    }

    public static TaskFactory<?> installCookbook(final String chefDirectory, final String cookbookName, final String cookbookArchiveUrl, final boolean force) {
        return new TaskFactory<TaskAdaptable<?>>() {
            @Override
            public TaskAdaptable<?> newTask() {
                TaskBuilder<Void> tb = Tasks.<Void>builder().displayName("install cookbook "+cookbookName);
                
                String cookbookDir = Urls.mergePaths(chefDirectory, cookbookName);
                String privateTmpDirContainingUnpackedCookbook = 
                    Urls.mergePaths(chefDirectory, "tmp-"+Strings.makeValidFilename(cookbookName)+"-"+Identifiers.makeRandomId(4));

                // TODO - skip the install earlier if it exists and isn't forced
//                if (!force) {
//                    // in builder.body, check 
//                    // "ls "+cookbookDir
//                    // and stop if it's zero
//                    // remove reference to 'force' below
//                }
                
                String destName = null;
                if (ArchiveType.of(cookbookArchiveUrl)==ArchiveType.UNKNOWN) {
                    destName = cookbookName + ".tgz";
                    log.debug("Assuming TGZ type for chef cookbook url "+cookbookArchiveUrl+"; it will be downloaded as "+destName);
                }
                tb.add(ArchiveTasks.deploy(null, null, cookbookArchiveUrl, EffectorTasks.findSshMachine(), privateTmpDirContainingUnpackedCookbook,
                    false, null, destName).newTask());
                
                String installCmd = BashCommands.chain(
                    "cd "+privateTmpDirContainingUnpackedCookbook,  
                    "COOKBOOK_EXPANDED_DIR=`ls`",
                    BashCommands.requireTest("`ls | wc -w` -eq 1", 
                            "The deployed archive "+cookbookArchiveUrl+" must contain exactly one directory"),
                    "mv $COOKBOOK_EXPANDED_DIR '../"+cookbookName+"'",
                    "cd ..",
                    "rm -rf '"+privateTmpDirContainingUnpackedCookbook+"'");
                
                installCmd = force ? BashCommands.alternatives("rm -rf "+cookbookDir, installCmd) : BashCommands.alternatives("ls "+cookbookDir+" > /dev/null 2> /dev/null", installCmd);
                tb.add(SshEffectorTasks.ssh(installCmd).summary("renaming cookbook dir").requiringExitCodeZero().newTask());
                
                return tb.build();
            }
        };
    }

    protected static String cdAndRun(String targetDirectory, String command) {
        return BashCommands.chain("mkdir -p '"+targetDirectory+"'",
                "cd '"+targetDirectory+"'",
                command);
    }

    public static TaskFactory<?> buildChefFile(String runDirectory, String chefDirectory, String phase, Iterable<? extends String> runList,
            Map<String, Object> optionalAttributes) {
        // TODO if it's server, try knife first
        // TODO configure add'l properties
        String phaseRb = 
            "root = "
            + "'"+runDirectory+"'" 
            // recommended alternate to runDir is the following, but it is not available in some rubies
            //+ File.absolute_path(File.dirname(__FILE__))"+
            + "\n"+
            "file_cache_path root\n"+
//            "cookbook_path root + '/cookbooks'\n";
            "cookbook_path '"+chefDirectory+"'\n";

        Map<String,Object> phaseJsonMap = MutableMap.of();
        if (optionalAttributes!=null)
            phaseJsonMap.putAll(optionalAttributes);
        if (runList!=null)
            phaseJsonMap.put("run_list", ImmutableList.copyOf(runList));
        Gson json = new GsonBuilder().create();
        String phaseJson = json.toJson(phaseJsonMap);

        return Tasks.sequential("build chef files for "+phase,
                    SshEffectorTasks.put(Urls.mergePaths(runDirectory)+"/"+phase+".rb").contents(phaseRb).createDirectory(),
                    SshEffectorTasks.put(Urls.mergePaths(runDirectory)+"/"+phase+".json").contents(phaseJson));
    }

    public static TaskFactory<?> runChef(String runDir, String phase) {
        // TODO chef server
        return SshEffectorTasks.ssh(cdAndRun(runDir, "sudo chef-solo -c "+phase+".rb -j "+phase+".json -ldebug")).
                summary("run chef for "+phase).requiringExitCodeZero();
    }
    
}
