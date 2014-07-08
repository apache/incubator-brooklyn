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
package brooklyn.entity.java;

import java.util.List;
import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.collections.MutableList;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@ImplementedBy(VanillaJavaAppImpl.class)
public interface VanillaJavaApp extends SoftwareProcess, UsesJava, UsesJmx, UsesJavaMXBeans {

    // TODO Make jmxPollPeriod @SetFromFlag easier to use: currently a confusion over long and TimeDuration, and 
    // no ability to set default value (can't just set field because config vals read/set in super-constructor :-(

    @SetFromFlag("args")
    ConfigKey<List> ARGS = ConfigKeys.newConfigKey(List.class,
            "vanillaJavaApp.args", "Arguments for launching the java app", Lists.newArrayList());
    
    @SetFromFlag(value="main", nullable=false)
    ConfigKey<String> MAIN_CLASS = ConfigKeys.newStringConfigKey("vanillaJavaApp.mainClass", "class to launch");

    @SetFromFlag("classpath")
    ConfigKey<List> CLASSPATH = ConfigKeys.newConfigKey(List.class,
            "vanillaJavaApp.classpath", "classpath to use, as list of URL entries; " +
            "these URLs are copied to lib/ and expanded in the case of tar/tgz/zip",
            Lists.newArrayList());

    AttributeSensor<List> CLASSPATH_FILES = Sensors.newSensor(List.class,
            "vanillaJavaApp.classpathFiles", "classpath used, list of files");

    @SetFromFlag("jvmXArgs")
    ConfigKey<List> JVM_XARGS = ConfigKeys.newConfigKey(List.class,
            "vanillaJavaApp.jvmXArgs", "JVM -X args for the java app (e.g. memory)", 
            MutableList.of("-Xms128m", "-Xmx512m", "-XX:MaxPermSize=512m"));

    @SetFromFlag("jvmDefines")
    ConfigKey<Map> JVM_DEFINES = ConfigKeys.newConfigKey(Map.class,
            "vanillaJavaApp.jvmDefines", "JVM system property definitions for the app",
            Maps.newLinkedHashMap());

    public String getMainClass();
    public List<String> getClasspath();
    public List<String> getClasspathFiles();
    public Map getJvmDefines();
    public List getJvmXArgs();
    public String getRunDir();

    public void kill();

}
