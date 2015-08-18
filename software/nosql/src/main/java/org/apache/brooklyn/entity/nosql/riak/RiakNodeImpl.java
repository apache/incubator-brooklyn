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
package org.apache.brooklyn.entity.nosql.riak;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.entity.core.Entities;
import org.apache.brooklyn.entity.webapp.WebAppServiceMethods;

import brooklyn.entity.basic.SoftwareProcessImpl;

import org.apache.brooklyn.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.sensor.core.AttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.enricher.Enrichers;
import org.apache.brooklyn.sensor.feed.http.HttpFeed;
import org.apache.brooklyn.sensor.feed.http.HttpPollConfig;
import org.apache.brooklyn.sensor.feed.http.HttpValueFunctions;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Preconditions;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.google.common.net.HostAndPort;

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
        
        Integer defaultMaxOpenFiles = RIAK_MAX_OPEN_FILES.getDefaultValue();
        Integer maxOpenFiles = getConfig(RiakNode.RIAK_MAX_OPEN_FILES);
        Preconditions.checkArgument(maxOpenFiles >= defaultMaxOpenFiles , "Specified number of open files : %s : is less than the required minimum",
                maxOpenFiles, defaultMaxOpenFiles);
    }

    @SuppressWarnings("rawtypes")
    public boolean isPackageDownloadUrlProvided() {
        AttributeSensorAndConfigKey[] downloadProperties = { DOWNLOAD_URL_RHEL_CENTOS, DOWNLOAD_URL_UBUNTU, DOWNLOAD_URL_DEBIAN };
        for (AttributeSensorAndConfigKey property : downloadProperties) {
            if (!((ConfigurationSupportInternal) config()).getRaw(property).isAbsent()) {
                return true;
            }
        }
        return false;
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

        Set<Integer> ports = MutableSet.copyOf(super.getRequiredOpenPorts());
        Set<Integer> erlangPorts = ContiguousSet.create(Range.open(erlangRangeStart, erlangRangeEnd), DiscreteDomain.integers());
        ports.addAll(erlangPorts);

        return ports;
    }

    @Override
    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
        HostAndPort accessible = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, getRiakWebPort());

        HttpFeed.Builder httpFeedBuilder = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(String.format("http://%s/stats", accessible.toString()))
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
                        .onSuccess(Functionals.chain(
                                HttpValueFunctions.jsonContents("ring_members", String[].class),
                                new Function<String[], List<String>>() {
                                    @Nullable
                                    @Override
                                    public List<String> apply(@Nullable String[] strings) {
                                        return Arrays.asList(strings);
                                    }
                                }
                        ))
                        .onFailureOrException(Functions.constant(Arrays.asList(new String[0]))));

        for (AttributeSensor<Integer> sensor : ONE_MINUTE_SENSORS) {
            httpFeedBuilder.poll(new HttpPollConfig<Integer>(sensor)
                    .period(Duration.ONE_MINUTE)
                    .onSuccess(HttpValueFunctions.jsonContents(sensor.getName().substring(5), Integer.class))
                    .onFailureOrException(Functions.constant(-1)));
        }

        httpFeed = httpFeedBuilder.build();

        addEnricher(Enrichers.builder().combining(NODE_GETS, NODE_PUTS).computingSum().publishing(NODE_OPS).build());
        addEnricher(Enrichers.builder().combining(NODE_GETS_TOTAL, NODE_PUTS_TOTAL).computingSum().publishing(NODE_OPS_TOTAL).build());
        WebAppServiceMethods.connectWebAppServerPolicies(this);
    }

    @Override
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
    public void removeNode(String nodeName) {
        getDriver().removeNode(nodeName);
    }

    @Override
    public void bucketTypeCreate(String bucketTypeName, String bucketTypeProperties) {
        getDriver().bucketTypeCreate(bucketTypeName, bucketTypeProperties);
    }

    @Override
    public List<String> bucketTypeList() {
        return getDriver().bucketTypeList();
    }

    @Override
    public List<String> bucketTypeStatus(String bucketTypeName) {
        return getDriver().bucketTypeStatus(bucketTypeName);
    }

    @Override
    public void bucketTypeUpdate(String bucketTypeName, String bucketTypeProperties) {
        getDriver().bucketTypeUpdate(bucketTypeName, bucketTypeProperties);
    }

    @Override
    public void bucketTypeActivate(String bucketTypeName) {
        getDriver().bucketTypeActivate(bucketTypeName);
    }

    @Override
    public void recoverFailedNode(String nodeName) {
        getDriver().recoverFailedNode(nodeName);
    }

    @Override
    public Integer getRiakWebPort() {
        return getAttribute(RiakNode.RIAK_WEB_PORT);
    }

    @Override
    public Integer getRiakPbPort() {
        return getAttribute(RiakNode.RIAK_PB_PORT);
    }

    @Override
    public Integer getHandoffListenerPort() {
        return getAttribute(RiakNode.HANDOFF_LISTENER_PORT);
    }

    @Override
    public Integer getEpmdListenerPort() {
        return getAttribute(RiakNode.EPMD_LISTENER_PORT);
    }

    @Override
    public Integer getErlangPortRangeStart() {
        return getAttribute(RiakNode.ERLANG_PORT_RANGE_START);
    }

    @Override
    public Integer getErlangPortRangeEnd() {
        return getAttribute(RiakNode.ERLANG_PORT_RANGE_END);
    }

    @Override
    public Boolean isSearchEnabled() {
        return getConfig(RiakNode.SEARCH_ENABLED);
    }

    @Override
    public Integer getSearchSolrPort() {
        return getConfig(RiakNode.SEARCH_SOLR_PORT);
    }

    @Override
    public Integer getSearchSolrJmxPort() {
        return getConfig(RiakNode.SEARCH_SOLR_JMX_PORT);
    }

    @Override
    public String getMajorVersion() {
        return getFullVersion().substring(0, 3);
    }

    @Override
    public String getFullVersion() {
        return getConfig(RiakNode.SUGGESTED_VERSION);
    }

    @Override
    public String getOsMajorVersion() {
        return getDriver().getOsMajorVersion();
    }

}
