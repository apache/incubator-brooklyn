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
package org.apache.brooklyn.demo;

import java.util.List;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;

import org.apache.brooklyn.entity.messaging.amqp.AmqpServer;
import org.apache.brooklyn.entity.messaging.qpid.QpidBroker;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.util.CommandLineUtil;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/** Qpid Broker Application */
public class StandaloneQpidBrokerExample extends AbstractApplication {

    public static final String CUSTOM_CONFIG_PATH = "classpath://custom-config.xml";
    public static final String PASSWD_PATH = "classpath://passwd";
    public static final String QPID_BDBSTORE_JAR_PATH = "classpath://qpid-bdbstore-0.20.jar";
    public static final String BDBSTORE_JAR_PATH = "classpath://je-5.0.34.jar";

    public static final String DEFAULT_LOCATION = "localhost";
    
    @Override
    public void initApp() {
        // Configure the Qpid broker entity
        QpidBroker broker = addChild(EntitySpec.create(QpidBroker.class)
                .configure("amqpPort", 5672)
                .configure("amqpVersion", AmqpServer.AMQP_0_10)
                .configure("runtimeFiles", ImmutableMap.builder()
                        .put(CUSTOM_CONFIG_PATH, QpidBroker.CONFIG_XML)
                        .put(PASSWD_PATH, QpidBroker.PASSWD)
                        .put(QPID_BDBSTORE_JAR_PATH, "lib/opt/qpid-bdbstore-0.20.jar")
                        .put(BDBSTORE_JAR_PATH, "lib/opt/je-5.0.34.jar")
                        .build())
                .configure("queue", "testQueue"));
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, StandaloneQpidBrokerExample.class).displayName("Qpid app"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
