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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.VisibleForTesting;

public class VanillaJavaAppImpl extends SoftwareProcessImpl implements VanillaJavaApp {

    static {
        JavaAppUtils.init();
    }

    private static final Logger log = LoggerFactory.getLogger(VanillaJavaApp.class);

    @SetFromFlag
    protected long jmxPollPeriod;

    protected JmxFeed jmxFeed;

    public VanillaJavaAppImpl() {}

    @VisibleForTesting
    public VanillaJavaAppImpl(Map<?,?> properties, Entity parent) {
        super(properties, parent);
    }

    public String getMainClass() { return getConfig(MAIN_CLASS); }
    public List<String> getClasspath() { return getConfig(CLASSPATH); }
    public List<String> getClasspathFiles() { return getAttribute(CLASSPATH_FILES); }
    public Map getJvmDefines() { return getConfig(JVM_DEFINES); }
    public List getJvmXArgs() { return getConfig(JVM_XARGS); }

    public void addToClasspath(String url) {
        List<String> cp = getConfig(CLASSPATH);
        List<String> newCP = new ArrayList<String>();
        if (cp!=null) newCP.addAll(cp);
        newCP.add(url);
        setConfig(CLASSPATH, newCP);
    }

    public void addToClasspath(Collection<String> urls) {
        List<String> cp = getConfig(CLASSPATH);
        List<String> newCP = new ArrayList<String>();
        if (cp!=null) newCP.addAll(cp);
        newCP.addAll(urls);
        setConfig(CLASSPATH, newCP);
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        if (((VanillaJavaAppDriver) getDriver()).isJmxEnabled()) {
            jmxPollPeriod = (jmxPollPeriod > 0) ? jmxPollPeriod : 3000;
            jmxFeed = JavaAppUtils.connectMXBeanSensors(this, jmxPollPeriod);
        }

        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    public Class<? extends VanillaJavaAppDriver> getDriverInterface() {
        return VanillaJavaAppDriver.class;
    }

    public String getRunDir() {
        // FIXME Make this an attribute; don't assume it hsa to be ssh? What uses this?
        VanillaJavaAppSshDriver driver = (VanillaJavaAppSshDriver) getDriver();
        return (driver != null) ? driver.getRunDir() : null;
    }

    @Override
    public void kill() {
        getDriver().kill();
    }
    
}
