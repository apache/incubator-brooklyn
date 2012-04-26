package brooklyn.extras.whirr.core

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity
import org.apache.whirr.ClusterController
import org.apache.whirr.ClusterControllerFactory
import brooklyn.entity.trait.Startable
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.flags.SetFromFlag
import brooklyn.location.Location
import brooklyn.location.basic.FixedListMachineProvisioningLocation
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.JcloudsLocation
import org.apache.commons.configuration.PropertiesConfiguration
import org.apache.whirr.ClusterSpec
import org.apache.whirr.Cluster
import brooklyn.entity.basic.AbstractGroup
import static com.google.common.collect.Iterables.getOnlyElement
import brooklyn.extras.whirr.core.WhirrRole

import org.jclouds.scriptbuilder.domain.OsFamily;

/**
 * Generic entity that can be used to deploy clusters that are
 * managed by Apache Whirr.
 *
 */
public class WhirrCluster extends AbstractEntity implements Startable {

	public static final Logger log = LoggerFactory.getLogger(WhirrCluster.class);

	@SetFromFlag("recipe")
	public static final BasicConfigKey<String> RECIPE =
		[String, "whirr.recipe", "Apache Whirr cluster recipe"]

	protected ClusterController controller = null
	protected ClusterSpec clusterSpec = null
	protected Cluster cluster = null
	protected Location location = null

	/**
	 * General entity initialisation
	 */
	public WhirrCluster(Map flags = [:], Entity owner = null) {
		super(flags, owner)
	}

	/**
	 * Apache Whirr can only start and manage a cluster in a single location
	 *
	 * @param locations
	 */
	void start(Collection<? extends Location> locations) {
		location = getOnlyElement(locations)
		startInLocation(location)
	}

	/**
	* Start a cluster as specified in the recipe on localhost
	*
	* @param location corresponding to localhost
	*/
	void startInLocation(LocalhostMachineProvisioningLocation location) {
		
		PropertiesConfiguration config = new PropertiesConfiguration()
		config.load(new StringReader(getConfig(RECIPE)))
   		
		StringBuilder nodes = []
		nodes.with {
			append "nodes:\n"
			append "    - id: localhost\n"
			append "      name: local machine\n"
			append "      hostname: 127.0.0.1\n"
			append "      os_arch: "+System.getProperty("os.arch")+"\n"
			append "      os_family: "+OsFamily.UNIX+"\n"
			append "      os_description: "+System.getProperty("os.name")+"\n"
			append "      os_version: "+System.getProperty("os.version")+"\n"
			append "      group: whirr\n"
			append "      tags:\n"
			append "          - local\n"
			append "      username: "+System.getProperty("user.name")+"\n" //NOTE: needs passwordless sudo!!!
			append "      credential_url: file://"+System.getProperty("user.home")+"/.ssh/id_rsa\n"
		}
		
		//provide the BYON nodes to whirr
		config.setProperty("jclouds.byon.nodes", nodes.toString())
   
		clusterSpec = new ClusterSpec(config)
   
		clusterSpec.setServiceName("byon")
		clusterSpec.setProvider("byon")
		clusterSpec.setIdentity("notused")
		clusterSpec.setCredential("notused")
		clusterSpec.setLocationId("byon");
   
		log.info("Starting cluster with roles " + config.getProperty("whirr.instance-templates")
						   + " in location " + location)
   
		startWithClusterSpec(clusterSpec);
	}
	
	/**
	 * Start a cluster as specified in the recipe in a given location
	 *
	 * @param location jclouds location spec
	 */
	void startInLocation(JcloudsLocation location) {
		PropertiesConfiguration config = new PropertiesConfiguration()
		config.load(new StringReader(getConfig(RECIPE)))

		clusterSpec = new ClusterSpec(config)
		clusterSpec.setProvider(location.getConf().provider)
		clusterSpec.setIdentity(location.getConf().identity)
		clusterSpec.setCredential(location.getConf().credential)
		clusterSpec.setLocationId(location.getConf().providerLocationId)
		clusterSpec.setPrivateKey((File)location.getPrivateKeyFile());
		clusterSpec.setPublicKey((File)location.getPublicKeyFile());
		// TODO: also add security groups when supported in the Whirr trunk
		
		log.info("Starting cluster with roles " + config.getProperty("whirr.instance-templates")
				+ " in location " + location)

		startWithClusterSpec(clusterSpec);
	}
	
	private void startWithClusterSpec(ClusterSpec clusterSpec) {
		if(controller==null){
			controller = new ClusterControllerFactory().create(clusterSpec.getServiceName());
		}
		cluster = controller.launchCluster(clusterSpec)
		
		for (Cluster.Instance instance : cluster.getInstances()) {
			log.info("Creating group for instance " + instance.id)
			def rolesGroup = new AbstractGroup(displayName: "Instance:" + instance.id, this) {}
			for (String role: instance.roles) {
				log.info("Creating entity for '" + role + "' on instance " + instance.id)
				rolesGroup.addOwnedChild(new WhirrRole(displayName: "Role:" + role, role: role, rolesGroup))
			}
			addGroup(rolesGroup)
		}
	}

	void stop() {
		if (clusterSpec != null) {
			controller.destroyCluster(clusterSpec)
		}
		clusterSpec = null
		cluster = null
	}

	void restart() {
		// TODO better would be to restart the software instances, not the machines ?
		stop();
		start([location]); 
	}
}
