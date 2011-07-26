package com.cloudsoftcorp.monterey.brooklyn.example

import java.net.InetAddress
import java.net.URL
import java.util.Map

import brooklyn.entity.basic.AbstractApplication
import brooklyn.launcher.WebAppRunner
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.aws.AWSCredentialsFromEnv
import brooklyn.location.basic.aws.AwsLocation
import brooklyn.location.basic.aws.AwsLocationFactory
import brooklyn.management.internal.AbstractManagementContext

import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyContainerNode
import com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode
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
    
    public static final Map EC2_IMAGES = [
        "eu-west-1":"ami-89def4fd",
        "us-east-1":"ami-2342a94a",
        "us-west-1":"ami-25df8e60",
        "ap-southeast-1":"ami-21c2bd73",
        "ap-northeast-1":"ami-f0e842f1",
        ]
                
    MontereyNetwork mn
    
    public MontereyExampleApp() {
        mn = new MontereyNetwork(owner:this)
    }
    
    public static void main(String[] args) {
        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, MontereyExampleApp.class.getClassLoader());
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);

        FixedListMachineProvisioningLocation loc = new FixedListMachineProvisioningLocation<SshMachineLocation>(
                machines:[],name:"localhost-microcloud", latitude : 55.94944, longitude : -3.16028, streetAddress:"York, UK")
        for (i in 1..10) {
            new SshMachineLocation([address:InetAddress.getByName(SSH_HOST_NAME), userName:SSH_USERNAME]).setParentLocation(loc)
        }

        URL bundleUrl = MontereyExampleApp.class.getClassLoader().getResource(APP_BUNDLE_RESOURCE_PATH);
        URL appDescriptorUrl = MontereyExampleApp.class.getClassLoader().getResource(APP_CONFIG_RESOURCE_PATH);
        UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);
        
        MontereyExampleApp app = new MontereyExampleApp()
        app.mn.name = "HelloCloud"
        app.mn.setConfig(MontereyNetwork.APP_BUNDLES, [bundleUrl])
        app.mn.setConfig(MontereyNetwork.APP_DESCRIPTOR_URL, appDescriptorUrl)
        app.mn.setConfig(MontereyManagementNode.SUGGESTED_MANAGEMENT_NODE_INSTALL_DIR, MONTEREY_MANAGEMENT_NODE_PATH)
        app.mn.setConfig(MontereyManagementNode.SUGGESTED_WEB_USERS_CREDENTIAL, [adminCredential])
        app.mn.setConfig(MontereyNetwork.INITIAL_TOPOLOGY_PER_LOCATION, [(Dmn1NodeType.LPP):1,(Dmn1NodeType.MR):1,(Dmn1NodeType.M):1,(Dmn1NodeType.TP):1,(Dmn1NodeType.SPARE):1])
        
        //app.mm.policy << new MontereyLatencyOptimisationPolicy()
        
        AbstractManagementContext context = app.getManagementContext()
        context.manage(app)

        // Start the web console service
        WebAppRunner web
        try {
            web = new WebAppRunner(context)
            web.start()
        } catch (Exception e) {
            LOG.warn("Failed to start web-console", e)
        }
        
        AwsLocation euwestLoc = newAwsLocationFactory().newLocation("eu-west-1")
        AwsLocation useastLoc = newAwsLocationFactory().newLocation("us-east-1")
        euwestLoc.setTagMapping([
                (MontereyManagementNode.class.getName()):[imageId:"abc"],
                (MontereyContainerNode.class.getName()):[imageId:"abc"]])
        useastLoc.setTagMapping([
                (MontereyManagementNode.class.getName()):[imageId:"abc"],
                (MontereyContainerNode.class.getName()):[imageId:"abc"]])

        app.start([euwestLoc,useastLoc])
    }
    
    private static final AwsLocationFactory newAwsLocationFactory() {
        File sshPrivateKey = new File("src/test/resources/jclouds/id_rsa.private")
        File sshPublicKey = new File("src/test/resources/jclouds/id_rsa.pub")

        AWSCredentialsFromEnv creds = new AWSCredentialsFromEnv();
        return new AwsLocationFactory([
                identity : creds.getAWSAccessKeyId(),
                credential : creds.getAWSSecretKey(),
                sshPrivateKey : sshPrivateKey,
                sshPublicKey : sshPublicKey
            ])
    }
}
