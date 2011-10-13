package brooklyn.gemfire.demo;

import com.gemstone.gemfire.cache.util.GatewayQueueAttributes;

import java.io.IOException;

public interface GatewayChangeListener {

    public void gatewayAdded( String id, String endpointId, String host, int port, GatewayQueueAttributes attr) throws IOException;

    public boolean gatewayRemoved( String id ) throws IOException;

}