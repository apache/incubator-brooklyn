class TomcatClusterWithNginxApp extends AbstractApplication {
	NginxController nginxController = new NginxController(
		domain : "brooklyn.geopaas.org",
		port : 8000,
		portNumberSensor : Attributes.HTTP_PORT)

	ControlledDynamicWebAppCluster cluster = new ControlledDynamicWebAppCluster(
		owner : this,
		controller : nginxController,
		webServerFactory : { properties -> new TomcatServer(properties) },
		initialSize: 2,
		httpPort: 8080, war: "/path/to/booking-mvc.war")

	public static void main(String[] argv) {
		TomcatClusterWithNginxApp demo = new TomcatClusterWithNginxApp(
			displayName : "tomcat cluster with nginx example")
		BrooklynLauncher.manage(demo)
		
		JcloudsLocationFactory locFactory = new JcloudsLocationFactory([
					provider : "aws-ec2",
					identity : "xxxxxxxxxxxxxxxxxxxxxxxxxxx",
					credential : "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
					sshPrivateKey : new File("/home/bob/.ssh/id_rsa.private"),
					sshPublicKey : new File("/home/bob/.ssh/id_rsa.pub"),
					securityGroups:["my-security-group"]
				])

		JcloudsLocation loc = locFactory.newLocation("us-west-1")

		demo.start([loc])
	}
}