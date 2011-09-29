package brooklyn.example

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.webapp.JavaWebApp
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory


class TomcatInAwsApp extends AbstractApplication {
    
    public static void main(String[] argv) {
        TomcatInAwsApp demo = new TomcatInAwsApp(displayName : "tomcat server example")
        demo.init()
        BrooklynLauncher.manage(demo)
        
        JcloudsLocationFactory locFactory = new JcloudsLocationFactory([
                provider : "aws-ec2",
                identity : "12345678901234567890",
                credential : "098765432109876543210/09876543+210987654",
                sshPrivateKey : new File("/home/bob/.ssh/id_rsa.private"),
                sshPublicKey : new File("/home/bob/.ssh/id_rsa.pub")
            ])

        JcloudsLocation loc = locFactory.newLocation("eu-west-1")
        
        loc.setTagMapping([
                (TomcatServer.class.getName()):[
                    imageId:"ami-89def4fd",
                    securityGroups:["my-security-group"]]])
        
        demo.start([loc])
    }

    public void init() {
        def tomcat = new TomcatServer(owner:this)
        tomcat.setConfig(JavaWebApp.WAR, "/path/to/booking-mvc.war")
    }
}
