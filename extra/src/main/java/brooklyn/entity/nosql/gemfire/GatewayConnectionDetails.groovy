package brooklyn.entity.nosql.gemfire

public class GatewayConnectionDetails {

    final String clusterAbbreviatedName
    final String host;
    final int port;
    
    public GatewayConnectionDetails(String clusterAbbreviatedName, String host, int port) {
        this.clusterAbbreviatedName = clusterAbbreviatedName
        this.host = host
        this.port = port
    }
}
