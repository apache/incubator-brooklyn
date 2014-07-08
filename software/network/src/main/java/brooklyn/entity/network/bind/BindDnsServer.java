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
package brooklyn.entity.network.bind;

import java.util.Map;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

/**
 * This sets up a BIND DNS server.
 */
@Catalog(name="BIND", description="BIND is an Internet Domain Name Server.", iconUrl="classpath:///isc-logo.png")
@ImplementedBy(BindDnsServerImpl.class)
public interface BindDnsServer extends SoftwareProcess {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("filter")
    ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = new BasicConfigKey(Predicate.class,
            "bind.entity.filter", "Filter for entities which will use the BIND DNS service for name resolution",
            Predicates.instanceOf(SoftwareProcess.class));

    @SetFromFlag("domainName")
    ConfigKey<String> DOMAIN_NAME = new BasicConfigKey<String>(String.class,
            "bind.domain.name", "The DNS domain name to serve", "brooklyn.local");

    @SetFromFlag("reverseLookupNetwork")
    ConfigKey<String> REVERSE_LOOKUP_NETWORK = new BasicConfigKey<String>(String.class,
            "bind.reverse-lookup.address", "Network address for reverse lookup zone");

    @SetFromFlag("subnet")
    ConfigKey<String> MANAGEMENT_CIDR = new BasicConfigKey<String>(String.class,
            "bind.access.cidr", "Subnet CIDR or ACL allowed to access DNS", "0.0.0.0/0");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("hostnameSensor")
    ConfigKey<AttributeSensor<String>> HOSTNAME_SENSOR = new BasicConfigKey(AttributeSensor.class,
            "bind.sensor.hostname", "Sensor on managed entities that reports the hostname", Attributes.HOSTNAME);

    PortAttributeSensorAndConfigKey DNS_PORT =
            new PortAttributeSensorAndConfigKey("bind.port", "BIND DNS port for TCP and UDP", PortRanges.fromString("53"));

    @SetFromFlag("zoneFileTemplate")
    ConfigKey<String> DOMAIN_ZONE_FILE_TEMPLATE = new BasicConfigKey<String>(String.class,
            "bind.template.domain-zone", "The BIND domain zone file to serve (as FreeMarker template)",
            "classpath://brooklyn/entity/network/bind/domain.zone");

    @SetFromFlag("reverseZoneFileTemplate")
    ConfigKey<String> REVERSE_ZONE_FILE_TEMPLATE = new BasicConfigKey<String>(String.class,
            "bind.template.reverse-zone", "The BIND reverse lookup zone file to serve (as FreeMarker template)",
            "classpath://brooklyn/entity/network/bind/reverse.zone");

    @SetFromFlag("namedConfTemplate")
    ConfigKey<String> NAMED_CONF_TEMPLATE = new BasicConfigKey<String>(String.class,
            "bind.template.named-conf", "The BIND named configuration file (as FreeMarker template)",
            "classpath://brooklyn/entity/network/bind/named.conf");

    /* Reverse lookup attributes. */

    AttributeSensor<Cidr> REVERSE_LOOKUP_CIDR = new BasicAttributeSensor<Cidr>(Cidr.class,
            "bind.reverse-lookup.cidr", "The network CIDR that hosts must have for reverse lookup entriers to be added (default uses server address /24)");

    AttributeSensor<String> REVERSE_LOOKUP_DOMAIN = new BasicAttributeSensor<String>(String.class,
            "bind.reverse-lookup.domain", "The in-addr.arpa reverse lookup domain name");

    /* Configuration applicable to clients of the BIND DNS service. */

    @SetFromFlag("replaceResolvConf")
    ConfigKey<Boolean> REPLACE_RESOLV_CONF = new BasicConfigKey<Boolean>(Boolean.class,
            "bind.resolv-conf.replce", "Set to replace resolv.conf with the template (default is to use eth0 script)", Boolean.FALSE);

    @SetFromFlag("interfaceConfigTemplate")
    ConfigKey<String> INTERFACE_CONFIG_TEMPLATE = new BasicConfigKey<String>(String.class,
            "bind.template.interface-cfg", "The network interface configuration file for clients (as FreeMarker template)",
            "classpath://brooklyn/entity/network/bind/ifcfg");

    @SetFromFlag("interfaceConfigTemplate")
    ConfigKey<String> RESOLV_CONF_TEMPLATE = new BasicConfigKey<String>(String.class,
            "bind.template.resolv-conf", "The resolver configuration file for clients (as FreeMarker template)",
            "classpath://brooklyn/entity/network/bind/resolv.conf");

    @Effector(description="Gets the Hostname->IP mappings stored in this DNS server's conf file")
    public Map<String,String> getAddressMappings();

    public Map<String,String> getReverseMappings();

}
