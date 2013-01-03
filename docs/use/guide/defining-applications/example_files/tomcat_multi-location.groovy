class TomcatFabricApp extends AbstractApplication {
	Closure webClusterFactory = { Map flags, Entity parent ->
		Map clusterFlags = flags + 
			[factory: { properties -> new TomcatServer(properties) }]
		return new DynamicWebAppCluster(clusterFlags, parent)
	}

	DynamicFabric fabric = new DynamicFabric(
			parent : this,
			displayName : "WebFabric",
			displayNamePrefix : "",
			displayNameSuffix : " web cluster",
			initialSize : 2,
			factory : webClusterFactory,
			httpPort : 8080, 
			war: "/path/to/booking-mvc.war")
	
	public static void main(String[] argv) {
		TomcatFabricApp demo = new TomcatFabricApp(displayName : "tomcat example")
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
		JcloudsLocation loc2 = locFactory.newLocation("eu-west-1")
		demo.start([loc, loc2])
	}
}