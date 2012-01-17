package brooklyn.entity.webapp

import brooklyn.util.NetworkUtils;

class PortPreconditions {

    //TODO move to NetworkUtils
    
	public static void checkPortValid(String name, Integer port) {
		if (port!=null && port>=NetworkUtils.MIN_PORT_NUMBER && port<=NetworkUtils.MAX_PORT_NUMBER) return;
		throw new IllegalArgumentException("Invalid value $port for $name");
	}
	public static void checkPortsValid(Map ports) {
		ports.each { k,v -> checkPortValid(k,v) }
	}
	
}
