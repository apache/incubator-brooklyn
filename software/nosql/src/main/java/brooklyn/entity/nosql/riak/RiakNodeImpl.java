package brooklyn.entity.nosql.riak;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.webapp.WebAppServiceMethods;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;

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

    public void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .baseUri(String.format("http://%s:%s/stats", getAttribute(HOSTNAME), getWebPort()))
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
                                }))
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
    public void recoverFailedNode(String nodeName) {
        getDriver().recoverFailedNode(nodeName);
    }

    private String getWebPort() {
        return getConfig(RiakNode.RIAK_WEB_PORT);
    }

}
