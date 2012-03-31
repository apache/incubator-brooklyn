class TomcatClusterApp extends AbstractApplication {
	DynamicWebAppCluster cluster = new DynamicWebAppCluster(
		owner : this,
		initialSize: 2,
		factory: { properties -> new TomcatServer(properties) },
		httpPort: 8080, 
		war: "/path/to/booking-mvc.war")

	public static void main(String[] argv) {
		TomcatClusterApp demo = new TomcatClusterApp(
			displayName : "tomcat cluster example")
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