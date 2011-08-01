package com.cloudsoftcorp.monterey.brooklyn.entity

import com.cloudsoftcorp.monterey.node.api.PropertiesContext;
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Arrays
import java.util.Collection
import java.util.Collections
import java.util.Map
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.entity.Effector
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod
import brooklyn.entity.basic.EffectorWithExplicitImplementation
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.NoMachinesAvailableException
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.LanguageUtils

import com.cloudsoftcorp.monterey.clouds.AccountConfig
import com.cloudsoftcorp.monterey.comms.socket.SocketAddress
import com.cloudsoftcorp.monterey.control.provisioning.ProvisioningConstants
import com.cloudsoftcorp.monterey.control.workrate.api.WorkrateReport
import com.cloudsoftcorp.monterey.network.control.api.Dmn1NodeType
import com.cloudsoftcorp.monterey.network.control.api.NodeSummary
import com.cloudsoftcorp.monterey.network.control.plane.GsonSerializer
import com.cloudsoftcorp.monterey.network.control.plane.web.PlumberWebProxy
import com.cloudsoftcorp.monterey.network.control.wipapi.DmnFuture
import com.cloudsoftcorp.monterey.network.control.wipapi.NodesRolloutConfiguration
import com.cloudsoftcorp.monterey.node.api.NodeId
import com.cloudsoftcorp.monterey.node.api.PropertiesContext
import com.cloudsoftcorp.util.Loggers
import com.cloudsoftcorp.util.StringUtils
import com.cloudsoftcorp.util.exception.ExceptionUtils
import com.cloudsoftcorp.util.javalang.ClassLoadingContext
import com.cloudsoftcorp.util.javalang.JarUrlUtils
import com.cloudsoftcorp.util.text.StringEscapeHelper
import com.google.gson.Gson

/**
 * Represents a "proto node", i.e. a container that can host a LPP, MR, M, TP. 
 * 
 * When reverting/rolling out, the same MontereyContainerNode instance exists throughput. 
 * 
 * @aled
 */
public class MontereyContainerNode extends AbstractGroup implements Startable {

    // TODO Would be great if we supported a "container", aka protonode, being able to host M,TP,etc

    private static final Logger LOG = Loggers.getLogger(MontereyContainerNode.class);
    
    public static final BasicConfigKey<String> NETWORK_NODE_INSTALL_DIR = [String.class, "monterey.networknode.installdir", "Monterey network node installation directory", "/home/monterey/monterey-network-node" ]
    public static final BasicConfigKey<String> SUGGESTED_TRUST_STORE = [String.class, "monterey.networknode.truststore", "Monterey network node truststore" ]
    public static final BasicConfigKey<Integer> SUGGESTED_MONTEREY_NODE_PORT = [Integer.class, "monterey.networknode.nodeport", "Monterey network node comms port" ]
    public static final BasicConfigKey<Integer> SUGGESTED_MONTEREY_HUB_LPP_PORT = [Integer.class, "monterey.networknode.hublpp.port", "Monterey network node hub lpp port" ]
    
    public static final BasicAttributeSensor<Integer> CREATION_ID = [ String, "monterey.networknode.creationId", "Node creation id" ]
    public static final BasicAttributeSensor<NodeId> NODE_ID = [ NodeId.class, "monterey.networknode.nodeId", "Node node id" ]
    public static final BasicAttributeSensor<String> STATUS = [ String, "monterey.networknode.status", "Node status" ]
                                    
    public static final Effector<Void> REVERT = new EffectorInferredFromAnnotatedMethod<Void>(MontereyContainerNode.class, "revert", "Revert the entity");
    public static final Effector<Void> RELEASE = new EffectorInferredFromAnnotatedMethod<Void>(MontereyContainerNode.class, "release", "Release (i.e. shutdown) the entity");
    public static final Effector<Void> KILL = new EffectorInferredFromAnnotatedMethod<Void>(MontereyContainerNode.class, "kill", "Kill the entity");
    public static final Effector<Void> ROLLOUT = new EffectorWithExplicitImplementation<MontereyContainerNode, Void>("rollout", Void.TYPE,
            Arrays.<ParameterType<?>>asList(new BasicParameterType<Dmn1NodeType>("type", Dmn1NodeType.class, "The type that this node should become", null)),
            "Rollout the node as a specific type") {
        private static final long serialVersionUID = 6316740447259603273L;
        public Void invokeEffector(MontereyContainerNode entity, Map m) {
            entity.rollout((Dmn1NodeType) m.get("type"));
            return null;
        }
    };

    private final MontereyNetworkConnectionDetails connectionDetails;
    private String creationId;
    private NodeId nodeId;
    private Location location;
    private final AtomicBoolean running = new AtomicBoolean(false)
    
    private AbstractMontereyNode node;
    private MachineProvisioningLocation provisioningLoc;
    private final Gson gson;

    MontereyContainerNode(MontereyNetworkConnectionDetails connectionDetails) {
        this(connectionDetails, LanguageUtils.newUid());
    }
    
    MontereyContainerNode(MontereyNetworkConnectionDetails connectionDetails, String creationId) {
        this.connectionDetails = connectionDetails;
        this.creationId = creationId;
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();
    }

    @Override
    public String getDisplayName() {
        return "Container node "+(nodeId ? "("+nodeId+")" : "<uninitialized>")
    }

    public NodeId getNodeId() {
        return nodeId;
    }
    
    public AbstractMontereyNode getContainedMontereyNode() {
        return node;
    }

    protected List<Integer> getRequiredOpenPorts() {
        List<Integer> result = [22]
        
        Integer montereyNodePort = getConfig(SUGGESTED_MONTEREY_NODE_PORT)
        Integer montereyHubLppPort = getConfig(SUGGESTED_MONTEREY_HUB_LPP_PORT)
        if (montereyNodePort != null && montereyNodePort > 0) result.add(montereyNodePort) 
        if (montereyHubLppPort > 0) result.add(montereyHubLppPort)
        return result 
    }
    
    public void dispose() {
        synchronized (running) {
            running.set(false);
            running.notifyAll();
        }
    }
    
    public void connectToExisting(NodeSummary nodeSummary, Location loc) {
        this.nodeId = nodeSummary.getNodeId();
        this.creationId = nodeSummary.getCreationUid();
        this.location = loc;
        this.locations.add(location);
        
        LOG.info("Connected to existing node "+nodeId+" in location "+location);        
    }
    
    void onStarted(NodeSummary nodeSummary) {
        this.nodeId = nodeSummary.getNodeId();
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }
        
    @Override
    public void stop() {
        release();
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        startInLocation(locs)
    }
    
    public void startInLocation(Collection<? extends Location> locs) {
        MachineProvisioningLocation provisioningLoc = locs.find { it instanceof MachineProvisioningLocation }
        SshMachineLocation machineLoc = locs.find { it instanceof SshMachineLocation }
        if (provisioningLoc) {
            startInLocation((MachineProvisioningLocation)provisioningLoc)
        } else if (machineLoc) {
            startInLocation((SshMachineLocation)machineLoc)
        } else {
            throw new UnsupportedOperationException("No supported location type in "+locs);
        }
    }

    public void startInLocation(MachineProvisioningLocation location) {
        Map<String,Object> flags = location.getProvisioningFlags([getClass().getName()])
        flags.inboundPorts = getRequiredOpenPorts()
        
        provisioningLoc = location
        SshMachineLocation machine = location.obtain(flags)
        if (machine == null) throw new NoMachinesAvailableException(location)
        startInLocation(machine)
    }

    public void startInLocation(SshMachineLocation host) {
        running.set(true);
        location = host;
        locations.add(host);
        
        LOG.info("Creating new monterey node "+creationId+" on "+host);

        setAttribute(STATUS, "starting")
        setAttribute(CREATION_ID, creationId)
        
        PropertiesContext nodeProperties = new PropertiesContext();
        String locationId = host.getParentLocation()?.getName() ?: host.getName();
        String accountId = "accid";
        int montereyNodePort = getConfig(SUGGESTED_MONTEREY_NODE_PORT) ?: 0
        int montereyHubLppPort = getConfig(SUGGESTED_MONTEREY_HUB_LPP_PORT) ?: 0
        try {
            SocketAddress address = new SocketAddress(new InetSocketAddress(host.getAddress().getHostName(), montereyNodePort));
            nodeProperties.getProperties().add(ProvisioningConstants.NODE_LOCATION_PROPERTY, locationId);
            nodeProperties.getProperties().add(ProvisioningConstants.NODE_ACCOUNT_PROPERTY, accountId);
            nodeProperties.getProperties().add(ProvisioningConstants.NODE_CREATION_UID_PROPERTY, creationId);
            nodeProperties.getProperties().add(ProvisioningConstants.PREFERRED_HOSTNAME_PROPERTY, host.getAddress().getHostName());
            nodeProperties.getProperties().add(ProvisioningConstants.PREFERRED_SOCKET_ADDRESS_PROPERTY,address.getConstructionString());
            nodeProperties.getProperties().add(ProvisioningConstants.MONITOR_ADDRESS_PROPERTY, JarUrlUtils.toStringUsingDefaultClassloadingContext(connectionDetails.monitorAddress));
            nodeProperties.getProperties().add(ProvisioningConstants.MANAGER_ADDRESS_PROPERTY, JarUrlUtils.toStringUsingDefaultClassloadingContext(connectionDetails.managerAddress));
            nodeProperties.getProperties().add(ProvisioningConstants.LPP_HUB_LISTENER_PORT_PROPERTY, ""+montereyHubLppPort);
        
            // TODO Set comms class/bundle (and location latencies); see ManagementNodeConfig.getDefaultNodeProperties
        
            String networkHome = getConfig(NETWORK_NODE_INSTALL_DIR)
            String truststorePath = getConfig(SUGGESTED_TRUST_STORE)
            File truststore = truststorePath ? new File(truststorePath) : null
            
            if (truststore != null) nodeProperties.getProperties().add(ProvisioningConstants.JAVAX_NET_SSL_TRUSTSTORE, networkHome+"/"+AccountConfig.NETWORK_NODE_SSL_TRUSTSTORE_RELATIVE_PATH);
            
            String args = networkHome+"/"+MontereyNetworkConfig.NETWORK_NODE_START_SCRIPT_RELATIVE_PATH+
                    " -key "+creationId+
                    " "+StringEscapeHelper.wrapBash(StringUtils.join(nodeProperties.getProperties(), "\n"));
            
            try {
                if (truststore != null) {
                    host.copyTo(truststore, networkHome+"/"+AccountConfig.NETWORK_NODE_SSL_TRUSTSTORE_RELATIVE_PATH);
                }
                host.run(out: System.out, args);
                
                LOG.info("Launched and waiting for new monterey node "+creationId+" on "+host);
                waitForStartOrFailed();
                
                setAttribute(STATUS, (running.get() ? "running" : "failed"))
                setAttribute(NODE_ID, nodeId)
                
            } catch (IllegalStateException e) {
              throw e; // TODO throw as something nicer?
            } catch (IOException e) {
                // TODO Should just rethrow without logging; but exception wasn't being logged anywhere
                LOG.log(Level.WARNING, "Failed to start monterey container node; rethrowing", e);
                throw ExceptionUtils.throwRuntime(e);
            }

            LOG.info("Created new monterey node on host $host, creation id $creationId");
            
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Error starting node("+nodeProperties+") on "+host, e);
            killInLocation(host);
            throw e;
        }
    }

    private void waitForStartOrFailed() {
        synchronized (running) {
            while (running.get() && nodeId == null) {
                running.wait();
            }
        }
    }
    
    public void kill() {
        synchronized (running) {
            running.set(false);
            running.notifyAll();
            setAttribute(STATUS, "stopped")
        }
        
        Location loc = (locations) ? locations.iterator().next() : null;
        if (provisioningLoc) {
            provisioningLoc.release(loc)
        }
        
        if (loc instanceof SshMachineLocation) {
            killInLocation((SshMachineLocation)loc);
        } else if (loc == null) {
            LOG.info("No-op killing monterey network node; no location: creationId=$creationId");
        } else {
            LOG.warning("No-op killing monterey network node; unsupported location type $locations, creationId=$creationId");
        }
        
        setOwner(null)
    }
    
    private void killInLocation(SshMachineLocation host) {
        String networkHome = getConfig(NETWORK_NODE_INSTALL_DIR)
        
        String args = networkHome+"/"+MontereyNetworkConfig.NETWORK_NODE_KILL_SCRIPT_RELATIVE_PATH+
                " -key $creationId";

        try {
            host.run(out: System.out, args);
        } catch (IllegalStateException e) {
            if (e.toString().contains("No such process")) {
                // the process hadn't started or was killed externally? Our work is done.
                LOG.info("Network node process not running; termination is a no-op: creationId="+creationId+"; machine="+host);
            } else {
                throw e;
            }
        } catch (IOException e) {
            throw ExceptionUtils.throwRuntime(e);
        }
    }

    public void rollout(Dmn1NodeType type) {
        LOG.info("Rolling out node $nodeId in $locations as $type")
        PlumberWebProxy plumber = new PlumberWebProxy(connectionDetails.managementUrl, gson, connectionDetails.webApiAdminCredential);
        DmnFuture<Collection<NodeId>> future = plumber.rolloutNodes(new NodesRolloutConfiguration.Builder()
                .nodesToUse(Collections.singleton(nodeId))
                .ofType(type, 1)
                .build());
        future.get();
    }
    
    public void revert() {
        if (nodeId != null) {
            PlumberWebProxy plumber = new PlumberWebProxy(connectionDetails.managementUrl, gson, connectionDetails.webApiAdminCredential);
            DmnFuture<?> future = plumber.revert(nodeId);
            future.get();
        } else {
            LOG.info("Cannot revert monterey network node; unknown node id: creationId="+creationId+"; location="+locations);
        }
    }
    
    public void release() {
        if (nodeId != null) {
            PlumberWebProxy plumber = new PlumberWebProxy(connectionDetails.managementUrl, gson, connectionDetails.webApiAdminCredential);
            DmnFuture<?> future = plumber.release(nodeId);
            future.get();
        } else {
            LOG.info("Cannot release monterey network node; unknown node id, will attempt to kill: creationId="+creationId+"; location="+locations);
        }
        
        kill();
    }
    
    void updateWorkrate(WorkrateReport report) {
        node?.updateWorkrate(report)
    }

    void updateContents(NodeSummary nodeSummary, Collection<NodeId> downstreamNodes) {
        synchronized (running) {
            if (!nodeId) {
                nodeId = nodeSummary.nodeId
                running.notifyAll();
            }
        }
        
        updateNodeType(nodeSummary);
        node?.updateTopology(nodeSummary, (downstreamNodes ?: []));
    }
    
    private void updateNodeType(NodeSummary nodeSummary) {
        if (nodeSummary.getType() == node?.getNodeType()) {
            // already has correct type; nothing to do
            return;
        }
        AbstractMontereyNode oldNode = node
        
        switch (nodeSummary.getType()) {
            case Dmn1NodeType.M:
                node = new MediatorNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.SPARE:
                node = new SpareNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.LPP:
                node = new LppNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.MR:
                node = new MrNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.TP:
                node = new TpNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.SATELLITE_BOT:
                node = new SatelliteLppNode(connectionDetails, nodeId, location);
                break;
            case Dmn1NodeType.CHANGING:
                // no-op; will change type again shortly
                // TODO How to handle "changing"? Should we have no child until it changes?
                break;
            default: 
                throw new IllegalStateException("Cannot create entity for mediator node type "+nodeSummary.getType()+" at "+nodeId);
        }

        if (node == oldNode) {
            // no change;
        } else {
            if (oldNode != null) {
                oldNode.dispose();
                removeOwnedChild(oldNode)
                getManagementContext().unmanage(oldNode)
            }
            if (node != null) {
                addOwnedChild(node)
                getManagementContext().manage(node)
            }
        }
        
        LOG.info("Node "+nodeId+" changed type to "+nodeSummary.getType());        
    }   
}
