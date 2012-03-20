class TomcatServerApp extends AbstractApplication {
	def tomcat = new TomcatServer(owner: this,
					httpPort: 8080,
					war: "/path/to/booking-mvc.war")
	
	public static void main(String... args) {
		TomcatServerApp demo = new TomcatServerApp(
			displayName : "tomcat server example")
			
		BrooklynLauncher.manage(demo)
		demo.start([new LocalhostMachineProvisioningLocation(count: 1)])
	}
}