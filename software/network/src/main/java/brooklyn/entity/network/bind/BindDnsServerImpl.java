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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.policy.PolicySpec;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.Cidr;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.text.Strings;

import com.google.common.base.CharMatcher;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;

/**
 * This sets up a BIND DNS server.
 * <p>
 * <b>NOTE</b> This entity has been certified on <i>CentOS</i>, <i>RHEL</i>,
 * <i>Ubuntu</i> and <i>Debian</i> operating systems.
 */
public class BindDnsServerImpl extends SoftwareProcessImpl implements BindDnsServer {

    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerImpl.class);

    // As per RFC 952 and RFC 1123.
    private static final CharMatcher DOMAIN_NAME_FIRST_CHAR_MATCHER = CharMatcher.inRange('a', 'z')
                .or(CharMatcher.inRange('A', 'Z'))
                .or(CharMatcher.inRange('0', '9'));
    private static final CharMatcher DOMAIN_NAME_MATCHER = DOMAIN_NAME_FIRST_CHAR_MATCHER
            .or(CharMatcher.is('-'));

    private class HostnameTransformer implements Function<Entity, String> {
        @Override
        public String apply(Entity input) {
            String hostname = input.getAttribute(getConfig(HOSTNAME_SENSOR));
            hostname = DOMAIN_NAME_FIRST_CHAR_MATCHER.negate().trimLeadingFrom(hostname);
            hostname = DOMAIN_NAME_MATCHER.negate().trimAndCollapseFrom(hostname, '-');
            if (hostname.length() > 63) {
                hostname = hostname.substring(0, 63);
            }
            return hostname;
        }
    }

    public String getManagementCidr() {
        return getConfig(MANAGEMENT_CIDR);
    }

    public Integer getDnsPort() {
        return getAttribute(DNS_PORT);
    }

    public String getDomainName() {
        return getConfig(DOMAIN_NAME);
    }

    /**
     * @return A serial number guaranteed to be valid for use in a modified domain.zone or reverse.zone file.
     */
    public long getSerial() {
        long next = getAttribute(SERIAL) + 1;
        setAttribute(SERIAL, next);
        return next;
    }

    public Cidr getReverseLookupNetwork() {
        return getAttribute(REVERSE_LOOKUP_CIDR);
    }

    public String getReverseLookupDomain() {
        return getAttribute(REVERSE_LOOKUP_DOMAIN);
    }

    public DynamicGroup getEntities() {
        return getAttribute(ENTITIES);
    }

    @Override
    public void init() {
        super.init();
        checkNotNull(getConfig(HOSTNAME_SENSOR), "{} requires value for {}", getClass().getName(), HOSTNAME_SENSOR);
        DynamicGroup entities = addChild(EntitySpec.create(DynamicGroup.class)
                .configure("entityFilter", getConfig(ENTITY_FILTER)));
        setAttribute(ENTITIES, entities);
        setAttribute(A_RECORDS, ImmutableMap.<String, String>of());
        setAttribute(CNAME_RECORDS, ImmutableMultimap.<String, String>of());
        setAttribute(PTR_RECORDS, ImmutableMap.<String, String>of());
        setAttribute(ADDRESS_MAPPINGS, ImmutableMultimap.<String, String>of());
        setAttribute(SERIAL, System.currentTimeMillis());
    }

    @Override
    public void postRebind() {
        update();
    }

    @Override
    public Class<?> getDriverInterface() {
        return BindDnsServerDriver.class;
    }

    @Override
    public Multimap<String, String> getAddressMappings() {
        return getAttribute(ADDRESS_MAPPINGS);
    }

    @Override
    public Map<String, String> getReverseMappings() {
        return getAttribute(PTR_RECORDS);
    }

    @Override
    public BindDnsServerDriver getDriver() {
        return (BindDnsServerDriver) super.getDriver();
    }

    @Override
    public void connectSensors() {
        connectServiceUpIsRunning();
    }

    @Override
    public void disconnectSensors() {
        disconnectServiceUpIsRunning();
    }

    @Override
    protected void preStart() {
        String reverse = getConfig(REVERSE_LOOKUP_NETWORK);
        if (Strings.isBlank(reverse)) reverse = getAttribute(ADDRESS);
        setAttribute(REVERSE_LOOKUP_CIDR, new Cidr(reverse + "/24"));
        String reverseLookupDomain = Joiner.on('.').join(Iterables.skip(Lists.reverse(Lists.newArrayList(
                Splitter.on('.').split(reverse))), 1)) + ".in-addr.arpa";
        setAttribute(REVERSE_LOOKUP_DOMAIN, reverseLookupDomain);

        addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Address tracker")
                .configure("sensorsToTrack", ImmutableSet.of(getConfig(HOSTNAME_SENSOR)))
                .configure("group", getEntities()));
    }

    @Override
    public void postStart() {
        update();
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityChange(Entity member) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("State of {} on change: {}", member, member.getAttribute(Attributes.SERVICE_STATE_ACTUAL).name());
            }
            ((BindDnsServerImpl) entity).update();
        }
        @Override
        protected void onEntityAdded(Entity member) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("State of {} on added: {}", member, member.getAttribute(Attributes.SERVICE_STATE_ACTUAL).name());
            }
            ((BindDnsServerImpl) entity).configureResolver(member);
        }
    }

    private class HasHostnameAndValidLifecycle implements Predicate<Entity> {
        @Override
        public boolean apply(Entity input) {
            switch (input.getAttribute(Attributes.SERVICE_STATE_ACTUAL)) {
            case STOPPED:
            case STOPPING:
            case DESTROYED:
                return false;
            }
            return input.getAttribute(getConfig(HOSTNAME_SENSOR)) != null;
        }
    }

    public void update() {
        Lifecycle serverState = getAttribute(Attributes.SERVICE_STATE_ACTUAL);
        if (Lifecycle.STOPPED.equals(serverState) || Lifecycle.STOPPING.equals(serverState)
                || Lifecycle.DESTROYED.equals(serverState) || !getAttribute(Attributes.SERVICE_UP)) {
            LOG.debug("Skipped update of {} when service state is {} and running is {}",
                    new Object[]{this, getAttribute(Attributes.SERVICE_STATE_ACTUAL), getAttribute(SERVICE_UP)});
            return;
        }
        synchronized (this) {
            Iterable<Entity> availableEntities = FluentIterable.from(getEntities().getMembers())
                    .filter(new HasHostnameAndValidLifecycle());
            LOG.debug("{} updating with entities: {}", this, Iterables.toString(availableEntities));
            ImmutableListMultimap<String, Entity> hostnameToEntity = Multimaps.index(availableEntities,
                    new HostnameTransformer());
            Map<String, String> octetToName = Maps.newHashMap();
            BiMap<String, String> ipToARecord = HashBiMap.create();
            Multimap<String, String> aRecordToCnames = MultimapBuilder.hashKeys().arrayListValues().build();
            Multimap<String, String> ipToAllNames = MultimapBuilder.hashKeys().arrayListValues().build();

            for (Map.Entry<String, Entity> e : hostnameToEntity.entries()) {
                String domainName = e.getKey();
                Maybe<SshMachineLocation> location = Machines.findUniqueSshMachineLocation(e.getValue().getLocations());
                if (!location.isPresent()) {
                    LOG.debug("Member {} of {} does not have an SSH location so will not be configured", e.getValue(), this);
                    continue;
                }
                String address = location.get().getAddress().getHostAddress();
                ipToAllNames.put(address, domainName);
                if (!ipToARecord.containsKey(address)) {
                    ipToARecord.put(address, domainName);
                    if (getReverseLookupNetwork().contains(new Cidr(address + "/32"))) {
                        String octet = Iterables.get(Splitter.on('.').split(address), 3);
                        if (!octetToName.containsKey(octet)) octetToName.put(octet, domainName);
                    }
                } else {
                    aRecordToCnames.put(ipToARecord.get(address), domainName);
                }
            }
            setAttribute(A_RECORDS, ImmutableMap.copyOf(ipToARecord.inverse()));
            setAttribute(PTR_RECORDS, ImmutableMap.copyOf(octetToName));
            setAttribute(CNAME_RECORDS, Multimaps.unmodifiableMultimap(aRecordToCnames));
            setAttribute(ADDRESS_MAPPINGS, Multimaps.unmodifiableMultimap(ipToAllNames));

            // Update Bind configuration files and restart the service
            getDriver().updateBindConfiguration();
       }
    }

    protected void configureResolver(Entity entity) {
        Maybe<SshMachineLocation> machine = Machines.findUniqueSshMachineLocation(entity.getLocations());
        if (machine.isPresent()) {
            if (getConfig(REPLACE_RESOLV_CONF)) {
                machine.get().copyTo(new StringReader(getConfig(RESOLV_CONF_TEMPLATE)), "/etc/resolv.conf");
            } else {
                appendTemplate(getConfig(INTERFACE_CONFIG_TEMPLATE), "/etc/sysconfig/network-scripts/ifcfg-eth0", machine.get());
                machine.get().execScript("reload network", ImmutableList.of(BashCommands.sudo("service network reload")));
            }
            LOG.info("configured resolver on {}", machine);
        } else {
            LOG.debug("{} can't configure resolver at {}: no SshMachineLocation", this, entity);
        }
    }

    protected void appendTemplate(String template, String destination, SshMachineLocation machine) {
        String content = ((BindDnsServerSshDriver) getDriver()).processTemplate(template);
        String temp = "/tmp/template-" + Strings.makeRandomId(6);
        machine.copyTo(new ByteArrayInputStream(content.getBytes()), temp);
        machine.execScript("updating file", ImmutableList.of(
                BashCommands.sudo(String.format("tee -a %s < %s", destination, temp)),
                String.format("rm -f %s", temp)));
    }

    public Map<String, String> getAddressRecords() {
        return getAttribute(A_RECORDS);
    }

    public Multimap<String, String> getCanonicalNameRecords() {
        return getAttribute(CNAME_RECORDS);
    }

    public Map<String, Collection<String>> getCnamesForTemplates() {
        return getAttribute(CNAME_RECORDS).asMap();
    }

    public Map<String, String> getPointerRecords() {
        return getAttribute(PTR_RECORDS);
    }

}
