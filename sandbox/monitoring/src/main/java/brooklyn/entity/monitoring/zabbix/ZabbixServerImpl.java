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
package brooklyn.entity.monitoring.zabbix;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.policy.PolicySpec;

import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

public class ZabbixServerImpl extends AbstractEntity implements ZabbixServer {

    private static final Logger log = LoggerFactory.getLogger(ZabbixServerImpl.class);

    private Object[] mutex = new Object[0];
    private DynamicGroup monitoredEntities;
    private AgentTrackingPolicy policy;
    private Multimap<Location, Entity> entityLocations = HashMultimap.create();

    private transient HttpFeed login;

    @Override
    public void init() {
        super.init();
        Predicate<? super Entity> filter = getConfig(ENTITY_FILTER);
        monitoredEntities = addChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, filter)
                .displayName("agents"));
    }

    @Override
    public void onManagementStarted() {
        final byte[] jsonData = ZabbixFeed.JSON_USER_LOGIN
                .replace("{{username}}", getConfig(ZABBIX_SERVER_USERNAME))
                .replace("{{password}}", getConfig(ZABBIX_SERVER_PASSWORD))
                .getBytes();
        login = HttpFeed.builder()
                .entity(this)
                .baseUri(getConfig(ZABBIX_SERVER_API_URL))
                .headers(ImmutableMap.of("Content-Type", "application/json"))
                .poll(new HttpPollConfig<String>(ZABBIX_TOKEN)
                        .method("POST")
                        .body(jsonData)
                        .onFailure(Functions.constant(""))
                        .onSuccess(HttpValueFunctions.jsonContents("result", String.class)))
                .build();

        policy = addPolicy(PolicySpec.create(AgentTrackingPolicy.class)
                .displayName("Zabbix Agent Tracker")
                .configure("group", monitoredEntities));

        for (Entity each : monitoredEntities.getMembers()) {
            added(each);
        }

        setAttribute(Startable.SERVICE_UP, true);
    }

    public static class AgentTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityChange(Entity member) {
            ((ZabbixServerImpl)entity).added(member); }
        @Override
        protected void onEntityAdded(Entity member) {
        } // Ignore
        @Override
        protected void onEntityRemoved(Entity member) {
            ((ZabbixServerImpl)entity).removed(member);
        }
    }
    
    public void added(Entity member) {
        synchronized (mutex) {
            Optional<Location> location = Iterables.tryFind(member.getLocations(), Predicates.instanceOf(SshMachineLocation.class));
            if (location.isPresent() && member.getAttribute(Startable.SERVICE_UP)) {
                SshMachineLocation machine = (SshMachineLocation) location.get();
                if (!entityLocations.containsKey(machine)) {
                    entityLocations.put(machine, member);
                    // Configure the Zabbix agent
                    List<String> commands = ImmutableList.<String>builder()
                            .add("sed -i.bk 's/\\$HOSTNAME/" + machine.getDisplayName() + "/' /etc/zabbix/zabbix_agentd.conf")
                            .add("zabbix_agentd")
                            .build();
                    int result = machine.execCommands("configuring zabbix_agentd", commands);
                    if (result == 0) {
                        log.info("zabbix_agentd configured on {} at {}", member, machine);
                    } else {
                        log.warn("failed to configure zabbix_agentd on {}, status {}", machine, result);
                    }
                }
            } else {
                log.warn("zabbix added({}) called but no location or service not started", member);
            }
        }
    }

    public void removed(Entity member) {
        synchronized (mutex) {
            for (Location location : member.getLocations()) {
                entityLocations.remove(location, member);
            }
        }
    }

}
