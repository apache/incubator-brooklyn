package brooklyn.entity.webapp

import brooklyn.util.NetworkUtils

class PortPreconditions {

    //TODO move to NetworkUtils

    /**
     * @deprecated will be deleted in 0.5. Use NetworkUtils.checkPortValid.
     */
    @Deprecated
    public static int checkPortValid(Integer port, String errorMessage) {
        return NetworkUtils.checkPortValid(port, errorMessage);
    }

    /**
     * @deprecated will be deleted in 0.5. Use NetworkUtils.checkPortsValid.
     */
    @Deprecated
    public static void checkPortsValid(Map ports) {
        NetworkUtils.checkPortsValid(ports);
    }
    
}
