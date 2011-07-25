package com.cloudsoftcorp.monterey.brooklyn.example

import java.net.InetAddress
import java.net.URL

import brooklyn.entity.basic.AbstractApplication
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation

import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyNetwork
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.plane.web.UserCredentialsConfig
import com.cloudsoftcorp.monterey.network.control.plane.web.api.ControlPlaneWebConstants.HTTP_AUTH
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.cloudsoftcorp.util.javalang.OsgiClassLoadingContextFromBundle

public class MontereyExampleApp extends AbstractApplication {

    // FIXME Use MONTEREY_NETWORK_NODE_PATH
    
    private static final String MONTEREY_MANAGEMENT_NODE_PATH = "~/monterey-management-node"
    private static final String MONTEREY_NETWORK_NODE_PATH = "~/monterey-network-node-copy1"
    private static final String SSH_HOST_NAME = "localhost"
    private static final String SSH_USERNAME = "aled"
    private static final String APP_BUNDLE_RESOURCE_PATH = "com.cloudsoftcorp.monterey.example.noapisimple.jar"
    private static final String APP_CONFIG_RESOURCE_PATH = "HelloCloud.conf"
    
    MontereyNetwork mn
    
    public MontereyExampleApp() {
        mn = new MontereyNetwork(owner:this)
    }
    
    public static void main(String[] args) {
        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, MontereyExampleApp.class.getClassLoader());
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);

        FixedListMachineProvisioningLocation loc = new FixedListMachineProvisioningLocation<SshMachineLocation>(
            machines:[],name:"localhost-microcloud")
        for (i in 0..5) {
            loc.addChildLocation(new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]))
            loc.addChildLocation(new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]))
            loc.addChildLocation(new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]))
            loc.addChildLocation(new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]))
            loc.addChildLocation(new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]))
            loc.addChildLocation(new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]))
        }

        URL bundleUrl = MontereyExampleApp.class.getClassLoader().getResource(APP_BUNDLE_RESOURCE_PATH);
        URL appDescriptorUrl = MontereyExampleApp.class.getClassLoader().getResource(APP_CONFIG_RESOURCE_PATH);
        UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);
        
        MontereyExampleApp app = new MontereyExampleApp()
        app.mn.name = "HelloCloud"
        app.mn.appBundles = [bundleUrl]
        app.mn.appDescriptorUrl = appDescriptorUrl
        app.mn.managementNodeInstallDir = MONTEREY_MANAGEMENT_NODE_PATH
        app.mn.webUsersCredentials = [adminCredential]
        app.mn.initialTopologyPerLocation = [(Dmn1NodeType.LPP):1,(Dmn1NodeType.MR):1,(Dmn1NodeType.M):1,(Dmn1NodeType.TP):1,(Dmn1NodeType.SPARE):1]
        //app.mm.policy << new MontereyLatencyOptimisationPolicy()
        app.start([loc])
    }
}
