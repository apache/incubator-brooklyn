package brooklyn.entity.webapp

import brooklyn.util.NetworkUtils

class PortPreconditions {

    //TODO move to NetworkUtils

    @Deprecated // use NetworkUtils.checkPortValid
    public static int checkPortValid(Integer port, String errorMessage) {
        return NetworkUtils.checkPortValid(port, errorMessage);
    }

    @Deprecated // use NetworkUtils.checkPortsValid
    public static void checkPortsValid(Map ports) {
        NetworkUtils.checkPortsValid(ports);
    }
    
}
