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
package org.apache.brooklyn.entity.database.mysql;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.entity.database.mysql.MySqlNode.ChangePasswordEffector;
import org.apache.brooklyn.entity.database.mysql.MySqlNode.ExportDumpEffector;
import org.apache.brooklyn.entity.database.mysql.MySqlNode.ImportDumpEffector;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

public class MySqlNodeEffectors {
    public static class ExportDumpEffectoryBody extends EffectorBody<Void> implements ExportDumpEffector {
        @Override
        public Void call(ConfigBag parameters) {
            String path = parameters.get(PATH);
            String additionalOptions = Strings.nullToEmpty(parameters.get(ADDITIONAL_OPTIONS));
            //TODO additionalOptions, path are not sanitized and are coming from the user.
            //Should we try to sanitize (potentially limiting the range of possible inputs),
            //or just assume the user has full machine access anyway?
            ((MySqlNodeImpl)entity()).getDriver().dumpDatabase(additionalOptions, path);
            return null;
        }
    }
    public static Effector<Void> EXPORT_DUMP = Effectors.effector(ExportDumpEffector.EXPORT_DUMP)
            .impl(new ExportDumpEffectoryBody())
            .build();

    public static class ImportDumpEffectorBody extends EffectorBody<Void> implements ImportDumpEffector {
        @Override
        public Void call(ConfigBag parameters) {
            String path = Preconditions.checkNotNull(parameters.get(PATH), "path is required");
            // TODO sanitize path?
            ((MySqlNodeImpl)entity()).getDriver().executeScriptFromInstalledFileAsync(path);
            return null;
        }
    }
    public static Effector<Void> IMPORT_DUMP = Effectors.effector(ImportDumpEffector.IMPORT_DUMP)
            .impl(new ImportDumpEffectorBody())
            .build();

    public static class ChangePasswordEffectorBody extends EffectorBody<Void> implements ChangePasswordEffector {
        @Override
        public Void call(ConfigBag parameters) {
            String newPass = Preconditions.checkNotNull(parameters.get(PASSWORD), "password is required");
            String oldPass = entity().getAttribute(MySqlNode.PASSWORD);
            entity().sensors().set(MySqlNode.PASSWORD, newPass);
            MySqlDriver driver = ((MySqlNodeImpl)entity()).getDriver();
            driver.changePassword(oldPass, newPass);
            SshMachineLocation machine = EffectorTasks.getSshMachine(entity());
            DynamicTasks.queue(
                    SshTasks.newSshExecTaskFactory(machine, 
                                    "cd "+entity().getAttribute(MySqlNode.RUN_DIR),
                                    "sed -i'' -e 's@^\\(\\s*password\\s*=\\s*\\).*$@\\1" + newPass.replace("\\", "\\\\") + "@g' mymysql.cnf")
                            .requiringExitCodeZero()
                            .summary("Change root password"));
            return null;
        }
    }
    public static Effector<Void> CHANGE_PASSWORD = Effectors.effector(ChangePasswordEffector.CHANGE_PASSWORD)
            .impl(new ChangePasswordEffectorBody())
            .build();
}
