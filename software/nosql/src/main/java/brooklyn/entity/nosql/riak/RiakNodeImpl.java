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
package brooklyn.entity.nosql.riak;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.webapp.WebAppServiceMethods;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.config.ConfigBag;

public class RiakNodeImpl extends SoftwareProcessImpl implements RiakNode {

    private volatile HttpFeed httpFeed;

    @Override
    public RiakNodeDriver getDriver() {
        return (RiakNodeDriver) super.getDriver();
    }

    @Override
    public Class<RiakNodeDriver> getDriverInterface() {
        return RiakNodeDriver.class;
    }

    @Override
    public void init() {
        super.init();
        // fail fast if config files not avail
        Entities.getRequiredUrlConfig(this, RIAK_VM_ARGS_TEMPLATE_URL);
        Entities.getRequiredUrlConfig(this, RIAK_APP_CONFIG_TEMPLATE_URL);
    }

    @Override
    protected Map<String, Object> obtainProvisioningFlags(@SuppressWarnings("rawtypes") MachineProvisioningLocation location) {
        ConfigBag result = ConfigBag.newInstance(super.obtainProvisioningFlags(location));
        result.configure(CloudLocationConfig.OS_64_BIT, true);
        return result.getAllConfig();
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        // TODO this creates a huge list of inbound ports; much better to define on a security group using range syntax!
        int erlangRangeStart = getConfig(ERLANG_PORT_RANGE_START).iterator().next();
        int erlangRangeEnd = getConfig(ERLANG_PORT_RANGE_END).iterator().next();

        Set<Integer> newPorts = MutableSet.<Integer>copyOf(super.getRequiredOpenPorts());
        newPorts.remove(erlangRangeStart);
        newPorts.remove(erlangRangeEnd);
        for (int i = erlangRangeStart; i <= erlangRangeEnd; i++)
            newPorts.add(i);
        return newPorts;
    }

    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(String.format("http://%s:%d/stats", getAttribute(HOSTNAME), getRiakWebPort()))
                .poll(new HttpPollConfig<Integer>(NODE_GETS)
                        .onSuccess(HttpValueFunctions.jsonContents("node_gets", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(NODE_GETS_TOTAL)
                        .onSuccess(HttpValueFunctions.jsonContents("node_gets_total", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(NODE_PUTS)
                        .onSuccess(HttpValueFunctions.jsonContents("node_puts", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(NODE_PUTS_TOTAL)
                        .onSuccess(HttpValueFunctions.jsonContents("node_puts_total", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(VNODE_GETS)
                        .onSuccess(HttpValueFunctions.jsonContents("vnode_gets", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(VNODE_GETS_TOTAL)
                        .onSuccess(HttpValueFunctions.jsonContents("vnode_gets_total", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(VNODE_PUTS)
                        .onSuccess(HttpValueFunctions.jsonContents("vnode_puts", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(VNODE_PUTS_TOTAL)
                        .onSuccess(HttpValueFunctions.jsonContents("vnode_puts_total", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(READ_REPAIRS_TOTAL)
                        .onSuccess(HttpValueFunctions.jsonContents("read_repairs_total", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(COORD_REDIRS_TOTAL)
                        .onSuccess(HttpValueFunctions.jsonContents("coord_redirs_total", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(MEMORY_PROCESSES_USED)
                        .onSuccess(HttpValueFunctions.jsonContents("memory_processes_used", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(SYS_PROCESS_COUNT)
                        .onSuccess(HttpValueFunctions.jsonContents("sys_process_count", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(PBC_CONNECTS)
                        .onSuccess(HttpValueFunctions.jsonContents("pbc_connects", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<Integer>(PBC_ACTIVE)
                        .onSuccess(HttpValueFunctions.jsonContents("pbc_active", Integer.class))
                        .onFailureOrException(Functions.constant(-1)))
                .poll(new HttpPollConfig<List<String>>(RING_MEMBERS)
                        .onSuccess(HttpValueFunctions.chain(
                                HttpValueFunctions.jsonContents("ring_members", String[].class),
                                new Function<String[], List<String>>() {
                                    @Nullable
                                    @Override
                                    public List<String> apply(@Nullable String[] strings) {
                                        return Arrays.asList(strings);
                                    }
                                }
                        ))
                        .onFailureOrException(Functions.constant(Arrays.asList(new String[0]))))
                .build();

        WebAppServiceMethods.connectWebAppServerPolicies(this);
    }

    public void disconnectSensors() {
        super.disconnectSensors();
        if (httpFeed != null) {
            httpFeed.stop();
        }
        disconnectServiceUpIsRunning();
    }

    @Override
    public void joinCluster(String nodeName) {
        getDriver().joinCluster(nodeName);
    }

    @Override
    public void leaveCluster() {
        getDriver().leaveCluster();
    }

    @Override
    public void commitCluster() {
        getDriver().commitCluster();
    }

    @Override
    public boolean hasJoinedCluster() {
        return Boolean.TRUE.equals(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER);
    }

    @Override
    public void recoverFailedNode(String nodeName) {
        getDriver().recoverFailedNode(nodeName);
    }

    public Integer getRiakWebPort() {
        return getAttribute(RiakNode.RIAK_WEB_PORT);
    }

    public Integer getRiakPbPort() {
        return getAttribute(RiakNode.RIAK_PB_PORT);
    }

    public Integer getHandoffListenerPort() {
        return getAttribute(RiakNode.HANDOFF_LISTENER_PORT);
    }

    public Integer getEpmdListenerPort() {
        return getAttribute(RiakNode.EPMD_LISTENER_PORT);
    }

    public Integer getErlangPortRangeStart() {
        return getAttribute(RiakNode.ERLANG_PORT_RANGE_START);
    }

    public Integer getErlangPortRangeEnd() {
        return getAttribute(RiakNode.ERLANG_PORT_RANGE_END);
    }

}
