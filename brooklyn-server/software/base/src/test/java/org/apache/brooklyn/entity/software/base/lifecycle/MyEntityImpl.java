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
package org.apache.brooklyn.entity.software.base.lifecycle;

import java.util.List;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.entity.java.JavaSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.SoftwareProcessDriver;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.text.Identifiers;

public class MyEntityImpl extends SoftwareProcessImpl implements MyEntity {
    @Override
    public Class<?> getDriverInterface() {
        return MyEntityDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
    }
    
    public interface MyEntityDriver extends SoftwareProcessDriver {}

    public static class MyEntitySshDriver extends JavaSoftwareProcessSshDriver implements MyEntityDriver {

        @SetFromFlag("version")
        public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "0.1");

        public MyEntitySshDriver(MyEntityImpl entity, SshMachineLocation machine) {
            super(entity, machine);
        }

        @Override
        protected String getLogFileLocation() {
            return getRunDir()+"/nohup.out";
        }
        
        @Override
        public void install() {
            String resourceName = "/"+MyEntityApp.class.getName().replace(".", "/")+".class";
            ResourceUtils r = ResourceUtils.create(this);
            if (r.getResourceFromUrl(resourceName) == null) 
                throw new IllegalStateException("Cannot find resource "+resourceName);
            String tmpFile = "/tmp/brooklyn-test-MyEntityApp-"+Identifiers.makeRandomId(6)+".class";
            int result = getMachine().installTo(resourceName, tmpFile);
            if (result!=0) throw new IllegalStateException("Cannot install "+resourceName+" to "+tmpFile);
            String saveAs = "classes/"+MyEntityApp.class.getPackage().getName().replace(".", "/")+"/"+MyEntityApp.class.getSimpleName()+".class";
            newScript(INSTALLING).
                failOnNonZeroResultCode().
                body.append(
                    "curl -L \"file://"+tmpFile+"\" --create-dirs -o "+saveAs+" || exit 9"
                ).execute();
        }

        @Override
        public void customize() {
            newScript(CUSTOMIZING)
                .execute();
        }
        
        @Override
        public void launch() {
            newScript(MutableMap.of("usePidFile", true), LAUNCHING)
                .body.append(
                    String.format("nohup java -classpath %s/classes $JAVA_OPTS %s &", getInstallDir(), MyEntityApp.class.getName())
                ).execute();
        }
        
        @Override
        public boolean isRunning() {
            //TODO use PID instead
            return newScript(MutableMap.of("usePidFile", true), CHECK_RUNNING)
                .execute() == 0;
        }
        
        @Override
        public void stop() {
            newScript(MutableMap.of("usePidFile", true), STOPPING)
                .execute();
        }

        @Override
        public void kill() {
            newScript(MutableMap.of("usePidFile", true), KILLING)
                .execute();
        }

        @Override
        protected List<String> getCustomJavaConfigOptions() {
            return MutableList.<String>builder().addAll(super.getCustomJavaConfigOptions()).add("-Dabc=def").build();
        }
    }
}
