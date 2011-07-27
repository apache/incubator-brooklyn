package brooklyn.entity.nosql.gemfire

public class GatewayConnectionDetails {

    final String host;
    final int port;
    
    public GatewayConnectionDetails(String host, int port) {
        this.host = host
        this.port = port
    }
}
