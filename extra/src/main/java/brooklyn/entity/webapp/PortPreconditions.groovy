package brooklyn.entity.webapp

class PortPreconditions {

	public static void checkPortValid(String name, int port) {
		if (port>0 && port<65536) return;
		throw new IllegalArgumentException("Invalid value $port for $name");
	}
	public static void checkPortsValid(Map ports) {
		ports.each { k,v -> checkPortValid(k,v) }
	}
	
}
