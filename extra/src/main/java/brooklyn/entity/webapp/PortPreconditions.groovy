package brooklyn.entity.webapp

import brooklyn.util.NetworkUtils

class PortPreconditions {

    //TODO move to NetworkUtils

    public static int checkPortValid(Integer port, String errorMessage) {
        if (port==null || port<NetworkUtils.MIN_PORT_NUMBER || port>NetworkUtils.MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid port value $port: $errorMessage");
        }
        return port
    }

	public static void checkPortsValid(Map ports) {
		ports.each { k,v -> checkPortValid(v,k) }
	}
	
}
