package com.cloudsoftcorp.monterey.brooklyn.example

import java.net.InetAddress
import java.net.URL
import java.util.Map

import org.jclouds.ec2.domain.InstanceType

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

    private static final String APP_BUNDLE_RESOURCE_PATH = "com.cloudsoftcorp.monterey.example.noapisimple.jar"
    private static final String APP_CONFIG_RESOURCE_PATH = "HelloCloud.conf"
    
    // Monterey images
    public static final Map EC2_IMAGES = [
        "eu-west-1":"ami-901323e4",
        "us-east-1":"ami-3d814754",
        "us-west-1":"ami-01e7b544",
        "ap-southeast-1":"ami-bcd1a9ee",
        "ap-northeast-1":"ami-98ce7b99",
        ]
                
    MontereyNetwork mn
    
    public MontereyExampleApp() {
        mn = new MontereyNetwork(owner:this)
    }
    
    public static void main(String[] args) {
        OsgiClassLoadingContextFromBundle classLoadingContext = new OsgiClassLoadingContextFromBundle(null, MontereyExampleApp.class.getClassLoader());
        ClassLoadingContext.Defaults.setDefaultClassLoadingContext(classLoadingContext);

        URL bundleUrl = MontereyExampleApp.class.getClassLoader().getResource(APP_BUNDLE_RESOURCE_PATH);
        URL appDescriptorUrl = MontereyExampleApp.class.getClassLoader().getResource(APP_CONFIG_RESOURCE_PATH);
        UserCredentialsConfig adminCredential = new UserCredentialsConfig("myname", "mypass", HTTP_AUTH.ADMIN_ROLE);
        
        MontereyExampleApp app = new MontereyExampleApp()
        app.mn.name = "HelloCloud"
        app.mn.setConfig(MontereyNetwork.APP_BUNDLES, [bundleUrl])
        app.mn.setConfig(MontereyNetwork.APP_DESCRIPTOR_URL, appDescriptorUrl)
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
                (MontereyManagementNode.class.getName()):[
                        imageId:"eu-west-1/"+EC2_IMAGES.get("eu-west-1"),
                        hardwareId:InstanceType.M1_SMALL,
                        securityGroups:["brooklyn-all"]],
                (MontereyContainerNode.class.getName()):[
                        imageId:"eu-west-1/"+EC2_IMAGES.get("eu-west-1"),
                        hardwareId:InstanceType.M1_SMALL,
                        securityGroups:["brooklyn-all"]]])
        useastLoc.setTagMapping([
                (MontereyManagementNode.class.getName()):[
                        imageId:"us-east-1/"+EC2_IMAGES.get("us-east-1"),
                        hardwareId:InstanceType.M1_SMALL,
                        securityGroups:["brooklyn-all"]],
                (MontereyContainerNode.class.getName()):[
                        imageId:"us-east-1/"+EC2_IMAGES.get("us-east-1"),
                        hardwareId:InstanceType.M1_SMALL,
                        securityGroups:["brooklyn-all"]]])

        app.start([euwestLoc])
//        app.start([euwestLoc,useastLoc])
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
