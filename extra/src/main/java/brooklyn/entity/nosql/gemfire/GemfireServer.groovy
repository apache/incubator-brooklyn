package brooklyn.entity.nosql.gemfire

import java.net.URL
import java.util.Collection
import java.util.Map

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.EffectorWithExplicitImplementation
import brooklyn.event.Sensor;
import brooklyn.event.adapter.HttpSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.MachineLocation
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

class GemfireServer extends AbstractService {

    public static final BasicConfigKey<File> CONFIG_FILE = [ File, "gemfire.server.configFile", "Gemfire configuration file" ]
    public static final BasicConfigKey<File> JAR_FILE = [ File, "gemfire.server.jarFile", "Gemfire jar file" ]
    public static final BasicConfigKey<Integer> SUGGESTED_HUB_PORT = [ Integer, "gemfire.server.suggestedHubPort", "Gemfire gateway hub port", 11111 ]
    public static final BasicConfigKey<File> LICENSE = [ File, "gemfire.server.license", "Gemfire license file" ]
    public static final BasicConfigKey<Integer> WEB_CONTROLLER_PORT = [ Integer, "gemfire.server.controllerWebPort", "Gemfire controller web port", 8084 ]
    public static final BasicAttributeSensor<Integer> HUB_PORT = [ Integer, "gemfire.server.hubPort", "Gemfire gateway hub port" ]
    public static final BasicAttributeSensor<String> CONTROL_URL = [ String, "gemfire.server.controlUrl", "URL for perfoming management actions" ]
	public static final BasicAttributeSensor<Collection> REGION_LIST = new BasicAttributeSensor<Collection>(Collection.class, "gemfire.server.regions.list", 
		"List of fully-pathed regions on this gemfire server");

    public static final Effector<Void> ADD_GATEWAYS =
        new EffectorWithExplicitImplementation<GemfireServer, Void>("addGateways", Void.TYPE,
            Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("gateways", Collection.class,"Gateways to be added", Collections.emptyList())),
            "Add gateways to this server, to replicate to/from other clusters") {
        public Void invokeEffector(GemfireServer entity, Map m) {
            entity.addGateways((Collection<GatewayConnectionDetails>) m.get("gateways"));
            return null;
        }
    };

	public static final Effector<Void> REMOVE_GATEWAYS =
		new EffectorWithExplicitImplementation<GemfireServer, Void>("removeGateways", Void.TYPE,
			Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("gateways", Collection.class,"Gateways to be removed", Collections.emptyList())),
			"Remove decomissioned gateways from this server") {
		public Void invokeEffector(GemfireServer entity, Map m) {
			entity.removeGateways((Collection<GatewayConnectionDetails>) m.get("gateways"));
			return null;
		}
	};

	public static final Effector<Void> ADD_REGIONS =
		new EffectorWithExplicitImplementation<GemfireServer, Void>("addRegions", Void.TYPE,
			Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("regions", Collection.class,"Regions to be added", Collections.emptyList())),
			"Add regions to this server- will replicate and stay in sync if the region already exists elsewhere") {
		public Void invokeEffector(GemfireServer entity, Map m) {
			entity.addRegions((Collection<GatewayConnectionDetails>) m.get("regions"));
			return null;
		}
	};

	public static final Effector<Void> REMOVE_REGIONS =
		new EffectorWithExplicitImplementation<GemfireServer, Void>("removeGateways", Void.TYPE,
			Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("regions", Collection.class,"Regions to be removed", Collections.emptyList())),
			"Locally destroy a region on this server- will continue to exist elsewhere") {
		public Void invokeEffector(GemfireServer entity, Map m) {
			entity.removeRegions((Collection<GatewayConnectionDetails>) m.get("regions"));
			return null;
		}
	};

    private static final int CONTROL_PORT_VAL = 8084    
    transient HttpSensorAdapter httpAdapter

    public GemfireServer(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    protected void initSensors() {
        int hubPort = getConfig(SUGGESTED_HUB_PORT)
        setAttribute(HUB_PORT, hubPort)
        setAttribute(CONTROL_URL, "http://${setup.machine.address.hostName}:"+CONTROL_PORT_VAL+"/")
        
        httpAdapter = new HttpSensorAdapter(this)
        attributePoller.addSensor(SERVICE_UP, { computeNodeUp() } as ValueProvider)
		attributePoller.addSensor(REGION_LIST, { listRegions() } as ValueProvider)
    }
    
    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation loc) {
        return GemfireSetup.newInstance(this, loc)
    }
    
    private boolean computeNodeUp() {
        String url = getAttribute(CONTROL_URL)
        ValueProvider<Integer> provider = httpAdapter.newStatusValueProvider(url)
        try {
            Integer statusCode = provider.compute()
            return (statusCode >= 200 && statusCode <= 299)
        } catch (IOException ioe) {
            return false
        }
    }
	
	private Collection<String> listRegions() {
		String url = getAttribute(CONTROL_URL)+"region/list"
		ValueProvider<String> provider = httpAdapter.newStringBodyProvider(url)
		try {
			return Arrays.asList(provider.compute().split(","))
		} catch (IOException ioe) {
			return new String[0]
		}
	}

    public void addGateways(Collection<GatewayConnectionDetails> gateways) {
		int counter = 0
        gateways.each { GatewayConnectionDetails gateway ->
            String clusterId = gateway.clusterAbbreviatedName
            String endpointId = clusterId+"-"+(++counter)
            int port = gateway.port
            String hostname = gateway.host
            String controlUrl = getAttribute(CONTROL_URL)
            
            String urlstr = controlUrl+"/gateway/add?id="+clusterId+"&endpointId="+endpointId+"&port="+port+"&host="+hostname
            URL url = new URL(urlstr)
            HttpURLConnection connection = url.openConnection()
            connection.connect()
            int responseCode = connection.getResponseCode()
            if (responseCode < 200 || responseCode >= 300) {
                throw new IllegalStateException("Failed to add gateway to server, response code $responseCode for using $url")
            }
        }
    }
	
	public void removeGateways(Collection<GatewayConnectionDetails> gateways) {
		gateways.each { GatewayConnectionDetails gateway ->
			String clusterId = gateway.clusterAbbreviatedName
			String controlUrl = getAttribute(CONTROL_URL)
			
			String urlstr = controlUrl+"/gateway/remove?id="+clusterId
			URL url = new URL(urlstr)
			HttpURLConnection connection = url.openConnection()
			connection.connect()
			int responseCode = connection.getResponseCode()
			if (responseCode < 200 || responseCode >= 300) {
				throw new IllegalStateException("Failed to remove gateway from server, response code $responseCode for using $url")
			}
		}
	}
	
	public void addRegions(Collection<String> regions) {
		regions.each { String region ->
			String controlUrl = getAttribute(CONTROL_URL)
			
			String urlstr = controlUrl+"/region/add?name="+region
			URL url = new URL(urlstr)
			HttpURLConnection connection = url.openConnection()
			connection.connect()
			int responseCode = connection.getResponseCode()
			if (responseCode < 200 || responseCode >= 300) {
				throw new IllegalStateException("Failed to add region to server, response code $responseCode for using $url")
			}
		}
	}
	
	public void removeRegions(Collection<String> regions) {
		regions.each { String region ->
			String controlUrl = getAttribute(CONTROL_URL)
			
			String urlstr = controlUrl+"/region/remove?name="+region
			URL url = new URL(urlstr)
			HttpURLConnection connection = url.openConnection()
			connection.connect()
			int responseCode = connection.getResponseCode()
			if (responseCode < 200 || responseCode >= 300) {
				throw new IllegalStateException("Failed to remove region from server, response code $responseCode for using $url")
			}
		}
	}
}
