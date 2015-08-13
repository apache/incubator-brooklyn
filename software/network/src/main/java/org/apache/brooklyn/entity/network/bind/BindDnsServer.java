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
package org.apache.brooklyn.entity.network.bind;

import java.util.Map;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Multimap;
import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.ImplementedBy;
import org.apache.brooklyn.api.event.AttributeSensor;

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import org.apache.brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.net.Cidr;

/**
 * This sets up a BIND DNS server.
 */
@Catalog(name="BIND", description="BIND is an Internet Domain Name Server.", iconUrl="classpath:///isc-logo.png")
@ImplementedBy(BindDnsServerImpl.class)
public interface BindDnsServer extends SoftwareProcess {

    @SetFromFlag("filter")
    ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = ConfigKeys.newConfigKey(new TypeToken<Predicate<? super Entity>>() {},
            "bind.entity.filter", "Filter for entities which will use the BIND DNS service for name resolution." +
                    "Default is all instances of SoftwareProcess in the application.",
            Predicates.instanceOf(SoftwareProcess.class));

    @SetFromFlag("domainName")
    ConfigKey<String> DOMAIN_NAME = ConfigKeys.newStringConfigKey(
            "bind.domain.name", "The DNS domain name to serve", "brooklyn.local");

    @SetFromFlag("reverseLookupNetwork")
    ConfigKey<String> REVERSE_LOOKUP_NETWORK = ConfigKeys.newStringConfigKey(
            "bind.reverse-lookup.address", "Network address for reverse lookup zone");

    @SetFromFlag("subnet")
    ConfigKey<String> MANAGEMENT_CIDR = ConfigKeys.newStringConfigKey(
            "bind.access.cidr", "Subnet CIDR or ACL allowed to access DNS", "0.0.0.0/0");

    @SetFromFlag("hostnameSensor")
    ConfigKey<AttributeSensor<String>> HOSTNAME_SENSOR = ConfigKeys.newConfigKey(new TypeToken<AttributeSensor<String>>() {},
            "bind.sensor.hostname", "Sensor on managed entities that reports the hostname");

    PortAttributeSensorAndConfigKey DNS_PORT =
            new PortAttributeSensorAndConfigKey("bind.port", "BIND DNS port for TCP and UDP", PortRanges.fromString("53"));

    @SetFromFlag("zoneFileTemplate")
    ConfigKey<String> DOMAIN_ZONE_FILE_TEMPLATE = ConfigKeys.newStringConfigKey(
            "bind.template.domain-zone", "The BIND domain zone file to serve (as FreeMarker template)",
            "classpath://org/apache/brooklyn/entity/network/bind/domain.zone");

    @SetFromFlag("reverseZoneFileTemplate")
    ConfigKey<String> REVERSE_ZONE_FILE_TEMPLATE = ConfigKeys.newStringConfigKey(
            "bind.template.reverse-zone", "The BIND reverse lookup zone file to serve (as FreeMarker template)",
            "classpath://org/apache/brooklyn/entity/network/bind/reverse.zone");

    @SetFromFlag("namedConfTemplate")
    ConfigKey<String> NAMED_CONF_TEMPLATE = ConfigKeys.newStringConfigKey(
            "bind.template.named-conf", "The BIND named configuration file (as FreeMarker template)",
            "classpath://org/apache/brooklyn/entity/network/bind/named.conf");

    @SetFromFlag("updateRootZonesFile")
    ConfigKey<Boolean> UPDATE_ROOT_ZONES_FILE = ConfigKeys.newBooleanConfigKey(
            "bind.updateRootZones", "Instructs the entity to fetch the latest root zones file from ftp.rs.internic.net.",
            Boolean.FALSE);


    /* Reverse lookup attributes. */

    AttributeSensor<Cidr> REVERSE_LOOKUP_CIDR = Sensors.newSensor(Cidr.class,
            "bind.reverse-lookup.cidr", "The network CIDR that hosts must have for reverse lookup entries " +
            "to be added (default uses server address /24)");

    AttributeSensor<String> REVERSE_LOOKUP_DOMAIN = Sensors.newStringSensor(
            "bind.reverse-lookup.domain", "The in-addr.arpa reverse lookup domain name");


    /* Configuration applicable to clients of the BIND DNS service. */

    @SetFromFlag("replaceResolvConf")
    ConfigKey<Boolean> REPLACE_RESOLV_CONF = ConfigKeys.newBooleanConfigKey(
            "bind.resolv-conf.replce", "Set to replace resolv.conf with the template (default is to use eth0 script)", Boolean.FALSE);

    @SetFromFlag("interfaceConfigTemplate")
    ConfigKey<String> INTERFACE_CONFIG_TEMPLATE = ConfigKeys.newStringConfigKey(
            "bind.template.interface-cfg", "The network interface configuration file for clients (as FreeMarker template)",
            "classpath://org/apache/brooklyn/entity/network/bind/ifcfg");

    @SetFromFlag("interfaceConfigTemplate")
    ConfigKey<String> RESOLV_CONF_TEMPLATE = ConfigKeys.newStringConfigKey(
            "bind.template.resolv-conf", "The resolver configuration file for clients (as FreeMarker template)",
            "classpath://org/apache/brooklyn/entity/network/bind/resolv.conf");

    AttributeSensor<DynamicGroup> ENTITIES = Sensors.newSensor(DynamicGroup.class,
            "bind.entities", "The entities being managed by this server");

    AttributeSensor<Multimap<String, String>> ADDRESS_MAPPINGS = Sensors.newSensor(new TypeToken<Multimap<String, String>>() {},
            "bind.mappings", "All address mappings maintained by the server, in form address -> [names]");

    AttributeSensor<Map<String, String>> A_RECORDS = Sensors.newSensor(new TypeToken<Map<String, String>>() {},
            "bind.records.a", "All A records for the server, in form name -> address");

    AttributeSensor<Multimap<String, String>> CNAME_RECORDS = Sensors.newSensor(new TypeToken<Multimap<String, String>>() {},
            "bind.records.cname", "All CNAME records for the server, in form name -> [names]");

    AttributeSensor<Map<String, String>> PTR_RECORDS = Sensors.newSensor(new TypeToken<Map<String, String>>() {},
            "bind.records.ptr", "All PTR records for the server, in form address -> name. Entries will be in REVERSE_LOOKUP_CIDR. " +
                    "Entries are guaranteed to have an inverse mapping in A_RECORDS.");

    AttributeSensor<Long> SERIAL = Sensors.newLongSensor(
            "bind.serial", "A serial number guaranteed to be valid for use in a modified domain.zone or reverse.zone file");

    public Multimap<String, String> getAddressMappings();

    /**
     * @return the IP to hostname mappings stored in this DNS server's conf file
     * @deprecated since 0.7.0 use {@link #PTR_RECORDS} instead.
     */
    @Deprecated
    @Effector(description="Gets the IP to hostname mappings stored in this DNS server's conf file")
    public Map<String, String> getReverseMappings();

    /**
     * @return the predicate used to filter entities for the Bind server to manage.
     */
    Predicate<? super Entity> getEntityFilter();

}
