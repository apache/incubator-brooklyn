package brooklyn.location.jclouds;

import brooklyn.location.cloud.CloudMachineNamer;
import brooklyn.util.config.ConfigBag;

public class JcloudsMachineNamer extends CloudMachineNamer {

    public JcloudsMachineNamer(ConfigBag setup) {
        super(setup);
    }
    
    /** returns the max length of a VM name for the cloud specified in setup;
     * this value is typically decremented by 9 to make room for jclouds labels */
    public Integer getCustomMaxNameLength() {
        // otherwise, for some known clouds which only allow a short name, use that length
        if ("vcloud".equals( setup.peek(JcloudsLocationConfig.CLOUD_PROVIDER) )) 
            return 24;
        if ("abiquo".equals( setup.peek(JcloudsLocationConfig.CLOUD_PROVIDER) )) 
            return 39;
        if ("google-compute-engine".equals( setup.peek(JcloudsLocationConfig.CLOUD_PROVIDER) ))
            return 39;
        // TODO other cloud max length rules
        
        return null;  
    }
    
}
