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
package brooklyn.entity.proxy;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

/**
 * A load balancer that routes requests to set(s) of servers.
 * 
 * There is an optional "serverPool" that will have requests routed to it (e.g. as round-robin). 
 * This is a group whose members are appropriate servers; membership of that group will be tracked 
 * to automatically update the load balancer's configuration as appropriate.
 * 
 * There is an optional urlMappings group for defining additional mapping rules. Members of this
 * group (of type UrlMapping) will be tracked, to automatically update the load balancer's configuration.
 * The UrlMappings can give custom routing rules so that specific urls are routed (and potentially re-written)
 * to particular sets of servers. 
 * 
 * @author aled
 */
public interface LoadBalancer extends Entity, Startable {

    @SetFromFlag("serverPool")
    ConfigKey<Group> SERVER_POOL = new BasicConfigKey<Group>(
            Group.class, "loadbalancer.serverpool", "The default servers to route messages to");

    @SetFromFlag("urlMappings")
    ConfigKey<Group> URL_MAPPINGS = new BasicConfigKey<Group>(
            Group.class, "loadbalancer.urlmappings", "Special mapping rules (e.g. for domain/path matching, rewrite, etc); not supported by all load balancers");
    
    /** sensor for port to forward to on target entities */
    @SetFromFlag("portNumberSensor")
    public static final BasicAttributeSensorAndConfigKey<AttributeSensor> PORT_NUMBER_SENSOR = new BasicAttributeSensorAndConfigKey<AttributeSensor>(
            AttributeSensor.class, "member.sensor.portNumber", "Port number sensor on members (defaults to http.port; not supported in all implementations)", Attributes.HTTP_PORT);

    /** sensor for hostname to forward to on target entities */
    @SetFromFlag("hostnameSensor")
    public static final BasicAttributeSensorAndConfigKey<AttributeSensor> HOSTNAME_SENSOR = new BasicAttributeSensorAndConfigKey<AttributeSensor>(
            AttributeSensor.class, "member.sensor.hostname", "Hostname/IP sensor on members (defaults to host.name; not supported in all implementations)", Attributes.HOSTNAME);

    /** sensor for hostname to forward to on target entities */
    @SetFromFlag("hostAndPortSensor")
    public static final BasicAttributeSensorAndConfigKey<AttributeSensor> HOST_AND_PORT_SENSOR = new BasicAttributeSensorAndConfigKey<AttributeSensor>(
            AttributeSensor.class, "member.sensor.hostandport", "host:port sensor on members (invalid to configure this and the portNumber or hostname sensors)", null);
    
    @SetFromFlag("port")
    /** port where this controller should live */
    public static final PortAttributeSensorAndConfigKey PROXY_HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "proxy.http.port", "Main HTTP port where this proxy listens", ImmutableList.of(8000, "8001+"));
    
    @SetFromFlag("protocol")
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.protocol", "Main URL protocol this proxy answers (typically http or https)", null);
    
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;
    
    public static final AttributeSensor<String> ROOT_URL = WebAppService.ROOT_URL;

    public static final AttributeSensor<Map<Entity, String>> SERVER_POOL_TARGETS = Sensors.newSensor(
            new TypeToken<Map<Entity, String>>() {},
            "proxy.serverpool.targets", 
            "The downstream targets in the server pool");
    
    public static final MethodEffector<Void> RELOAD = new MethodEffector<Void>(LoadBalancer.class, "reload");
    
    public static final MethodEffector<Void> UPDATE = new MethodEffector<Void>(LoadBalancer.class, "update");

    @Effector(description="Forces reload of the configuration")
    public void reload();

    @Effector(description="Updates the entities configuration, and then forces reload of that configuration")
    public void update();
    
    /**
     * Opportunity to do late-binding of the cluster that is being controlled. Must be called before start().
     * Can pass in the 'serverPool'.
     */
    public void bind(Map flags);
}
