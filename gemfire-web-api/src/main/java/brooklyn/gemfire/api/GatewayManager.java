package brooklyn.gemfire.api;

import com.gemstone.gemfire.cache.util.GatewayQueueAttributes;

import java.io.IOException;

public interface GatewayManager {

    public void addGateway(String id, String endpointId, String host, int port, GatewayQueueAttributes attr) throws IOException;

    public boolean removeGateway(String id) throws IOException;

}