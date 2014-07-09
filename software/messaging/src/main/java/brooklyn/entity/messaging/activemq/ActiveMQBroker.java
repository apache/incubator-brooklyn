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
package brooklyn.entity.messaging.activemq;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.java.UsesJmx;
import brooklyn.entity.messaging.MessageBroker;
import brooklyn.entity.messaging.jms.JMSBroker;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;
/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
@Catalog(name="ActiveMQ Broker", description="ActiveMQ is an open source message broker which fully implements the Java Message Service 1.1 (JMS)", iconUrl="classpath:///activemq-logo.png")
@ImplementedBy(ActiveMQBrokerImpl.class)
public interface ActiveMQBroker extends SoftwareProcess, MessageBroker, UsesJmx, JMSBroker<ActiveMQQueue, ActiveMQTopic> {

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = SoftwareProcess.START_TIMEOUT;
    
    @SetFromFlag("version")
    public static final ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "5.7.0");

    @SetFromFlag("downloadUrl")
    public static final AttributeSensorAndConfigKey<String,String> DOWNLOAD_URL = new StringAttributeSensorAndConfigKey(
            Attributes.DOWNLOAD_URL, "${driver.mirrorUrl}/${version}/apache-activemq-${version}-bin.tar.gz");

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    public static final BasicConfigKey<String> MIRROR_URL = new BasicConfigKey<String>(String.class, "activemq.install.mirror.url", "URL of mirror",
        "http://www.mirrorservice.org/sites/ftp.apache.org/activemq/apache-activemq");

    @SetFromFlag("openWirePort")
	public static final PortAttributeSensorAndConfigKey OPEN_WIRE_PORT = new PortAttributeSensorAndConfigKey("openwire.port", "OpenWire port", "61616+");

    @SetFromFlag("jettyPort")
    public static final PortAttributeSensorAndConfigKey AMQ_JETTY_PORT = new PortAttributeSensorAndConfigKey("activemq.jetty.port", "jetty port", "8161+");

    @SetFromFlag("jmxUser")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(UsesJmx.JMX_USER, "admin");
    
    @SetFromFlag("jmxPassword")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(UsesJmx.JMX_PASSWORD, "admin");
    
    @SetFromFlag("templateConfigurationUrl")
    public static final BasicAttributeSensorAndConfigKey<String> TEMPLATE_CONFIGURATION_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "activemq.templateConfigurationUrl", "Template file (in freemarker format) for the conf/activemq.xml file", 
            "classpath://brooklyn/entity/messaging/activemq/activemq.xml");
}
