package brooklyn.demo.todo;

import java.util.Collection
import java.util.List
import java.util.Map

import org.jclouds.ec2.domain.InstanceType

import brooklyn.entity.messaging.activemq.ActiveMQBroker
import brooklyn.entity.messaging.qpid.QpidBroker
import brooklyn.entity.nosql.gemfire.GemfireServer
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.basic.CommandLineLocations
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory



public class PreBuiltImages {

    public static final Map EC2_VANILLA_IMAGES = [
            "eu-west-1":"ami-89def4fd",
            "us-east-1":"ami-2342a94a",
            "us-west-1":"ami-25df8e60",
            "ap-southeast-1":"ami-21c2bd73",
            "ap-northeast-1":"ami-f0e842f1",
        ]
    public static final Map EC2_MONTEREY_IMAGES = [
            "eu-west-1":"ami-7867540c",
            "us-east-1":"ami-735d9c1a",
            "us-west-1":"ami-71451834",
            "ap-southeast-1":"ami-d07d0682",
            "ap-northeast-1":"ami-0e8c390f",
        ]
    public static final Map EC2_GEMFIRE_IMAGES = [
            "eu-west-1":"ami-ca2f1fbe",
            "us-east-1":"ami-837fb8ea",
            "us-west-1":"ami-not-available",
            "ap-southeast-1":"ami-not-available",
            "ap-northeast-1":"ami-not-available",
        ]

    //TODO set up a cleaner way to inject preferred images for well-known locations
    //?config?
    //method below is one way but not particularly good 
    public static JcloudsLocation lookupAwsRegion_sample(String regionName) {
        String imageIdVanilla = regionName+"/"+EC2_VANILLA_IMAGES.get(regionName)
        String imageIdMonterey = regionName+"/"+EC2_MONTEREY_IMAGES.get(regionName)
        String imageIdGemfire = regionName+"/"+EC2_GEMFIRE_IMAGES.get(regionName)
        JcloudsLocationFactory locationFactory = newAwsLocationFactory()
        JcloudsLocation region = locationFactory.newLocation(regionName)
        region.setTagMapping([
            (TomcatServer.class.getName()):[
                imageId:imageIdVanilla,
                securityGroups:["brooklyn-all"]],
            (JBoss6Server.class.getName()):[
                imageId:imageIdVanilla,
                hardwareId:InstanceType.M1_SMALL,
                securityGroups:["brooklyn-all"]],
            (JBoss7Server.class.getName()):[
                imageId:imageIdVanilla,
                hardwareId:InstanceType.M1_SMALL,
                securityGroups:["brooklyn-all"]],
            (NginxController.class.getName()):[
                imageId:imageIdVanilla,
                securityGroups:["brooklyn-all"]],
            ("com.cloudsoftcorp.monterey.brooklyn.example.MontereyTomcatServer"):[
                imageId:imageIdVanilla,
                securityGroups:["brooklyn-all"]],
            ("com.cloudsoftcorp.monterey.brooklyn.entity.MontereyManagementNode"):[
                imageId:imageIdMonterey,
                hardwareId:InstanceType.M1_SMALL,
                securityGroups:["brooklyn-all"]],
            ("com.cloudsoftcorp.monterey.brooklyn.entity.MontereyContainerNode"):[
                imageId:imageIdMonterey,
                hardwareId:InstanceType.M1_SMALL,
                securityGroups:["brooklyn-all"]],
            (GemfireServer.class.getName()):[
                imageId:imageIdGemfire,
                securityGroups:["brooklyn-all"]],
            (ActiveMQBroker.class.getName()):[
                imageId:imageIdVanilla,
                hardwareId:InstanceType.M1_SMALL,
                securityGroups:["brooklyn-all"]],
            (QpidBroker.class.getName()):[
                imageId:imageIdVanilla,
                hardwareId:InstanceType.M1_SMALL,
                securityGroups:["brooklyn-all"]],
            ("monterey.brooklyn.Venue"):[
                imageId:imageIdVanilla,
                securityGroups:["brooklyn-all"]]])
        
        return region
    }
    

}
