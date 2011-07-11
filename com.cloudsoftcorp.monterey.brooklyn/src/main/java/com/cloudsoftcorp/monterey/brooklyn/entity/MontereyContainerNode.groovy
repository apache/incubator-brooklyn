package com.cloudsoftcorp.monterey.brooklyn.entity

import com.cloudsoftcorp.monterey.node.api.PropertiesContext;
import java.io.IOException
import java.net.InetSocketAddress
import java.util.Collection
import java.util.logging.Level
import java.util.logging.Logger

import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
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
        
    private final MontereyNetworkConnectionDetails connectionDetails;
    private String creationId;
    private NodeId nodeId;
    private Location location;
    
    // TODO use these or delete them?
    private File truststore = null;
    private int montereyNodePort = 0;
    private int montereyHubLppPort = 0;
    private String locationId;
    private String accountId;
    private String networkHome = "~/monterey-network-node-copy1";
    
    private AbstractMontereyNode node;
    
    private final Gson gson;

    MontereyContainerNode(MontereyNetworkConnectionDetails connectionDetails) {
        this.connectionDetails = connectionDetails;
        this.creationId = LanguageUtils.newUid();
        ClassLoadingContext classloadingContext = ClassLoadingContext.Defaults.getDefaultClassLoadingContext();
        GsonSerializer gsonSerializer = new GsonSerializer(classloadingContext);
        gson = gsonSerializer.getGson();
    }
    
    public void setTruststore(File val) {
        this.truststore = val;
    }
    
    public void setMontereyNodePort(int val) {
        this.montereyNodePort = val;
    }
    
    public NodeId getNodeId() {
        return nodeId;
    }
    
    public AbstractMontereyNode getContainedMontereyNode() {
        return node;
    }

    public void connectToExisting(NodeSummary nodeSummary, Location loc) {
        this.nodeId = nodeSummary.getNodeId();
        this.creationId = nodeSummary.getCreationUid();
        this.location = location;
        this.locations.add(location);
        
        LOG.info("Connected to existing node "+nodeId+" in location "+location);        
    }
    
    void onStarted(NodeSummary nodeSummary) {
        this.nodeId = nodeSummary.getNodeId();
    }
    
    public void start(Collection<? extends Location> locs) {
        if (locs.isEmpty()) throw new IllegalArgumentException("Locations empty; cannot start monterey node");
        Location loc = locs.iterator().next();
        
        if (loc instanceof SshMachineLocation) {
            startOnHost((SshMachineLocation)loc);
        } else {
            throw new UnsupportedOperationException("Unsupported location type "+loc);
        }
    }

    public void stop() {
        release();
    }

    // FIXME Work in progress; untested code that won't work because fields aren't initialized!
    public void startOnHost(SshMachineLocation host) {
        locations.add(host);
        
        LOG.info("Creating new monterey node "+creationId+" on "+host);

        PropertiesContext nodeProperties = new PropertiesContext();
        try {
            SocketAddress address = new SocketAddress(new InetSocketAddress(host.getAddress().getHostName(), montereyNodePort));
            nodeProperties.getProperties().add(ProvisioningConstants.NODE_LOCATION_PROPERTY, locationId);
            nodeProperties.getProperties().add(ProvisioningConstants.NODE_ACCOUNT_PROPERTY, accountId);
            nodeProperties.getProperties().add(ProvisioningConstants.NODE_CREATION_UID_PROPERTY, creationId);
            nodeProperties.getProperties().add(ProvisioningConstants.PREFERRED_HOSTNAME_PROPERTY, host.getAddress().getHostName());
            nodeProperties.getProperties().add(ProvisioningConstants.PREFERRED_SOCKET_ADDRESS_PROPERTY,address.getConstructionString());
            nodeProperties.getProperties().add(ProvisioningConstants.LPP_HUB_LISTENER_PORT_PROPERTY, ""+montereyHubLppPort);
            nodeProperties.getProperties().add(ProvisioningConstants.MONITOR_ADDRESS_PROPERTY, JarUrlUtils.toStringUsingDefaultClassloadingContext(connectionDetails.getMonitorAddress()));
            nodeProperties.getProperties().add(ProvisioningConstants.MANAGER_ADDRESS_PROPERTY, JarUrlUtils.toStringUsingDefaultClassloadingContext(connectionDetails.getManagerAddress()));

            // TODO Set comms class/bundle (and location latencies); see ManagementNodeConfig.getDefaultNodeProperties
        
            if (truststore != null) nodeProperties.getProperties().add(ProvisioningConstants.JAVAX_NET_SSL_TRUSTSTORE, networkHome+"/"+AccountConfig.NETWORK_NODE_SSL_TRUSTSTORE_RELATIVE_PATH);
            
            String args = networkHome+"/"+MontereyNetworkConfig.NETWORK_NODE_START_SCRIPT_RELATIVE_PATH+
                    " -key "+creationId+
                    " "+StringEscapeHelper.wrapBash(StringUtils.join(nodeProperties.getProperties(), "\n"));
            
            try {
                if (truststore != null) {
                    host.copyTo(truststore, networkHome+"/"+AccountConfig.NETWORK_NODE_SSL_TRUSTSTORE_RELATIVE_PATH);
                }
                host.run(out: System.out, args);
                
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
            kill(host);
            throw e;
        }
    }

    public void kill() {
        Location loc = (locations) ? locations.iterator().next() : null;
        if (loc instanceof SshMachineLocation) {
            kill((SshMachineLocation)loc);
        } else if (loc == null) {
            LOG.info("No-op killing monterey network node; no location: creationId=$creationId");
        } else {
            throw new UnsupportedOperationException("Unsupported location type, $locations");
        }
    }
    
    private void kill(SshMachineLocation host) {
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
        PlumberWebProxy plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
        DmnFuture<Collection<NodeId>> future = plumber.rolloutNodes(new NodesRolloutConfiguration.Builder()
                .nodesToUse(Collections.singleton(nodeId))
                .ofType(type, 1)
                .build());
    }
    
    public void revert() {
        if (nodeId != null) {
            PlumberWebProxy plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
            DmnFuture<?> future = plumber.revert(nodeId);
            future.get();
        } else {
            LOG.info("Cannot revert monterey network node; unknown node id: creationId="+creationId+"; location="+locations);
        }
    }
    
    public void release() {
        if (nodeId != null) {
            PlumberWebProxy plumber = new PlumberWebProxy(connectionDetails.getManagementUrl(), gson, connectionDetails.getWebApiAdminCredential());
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
        updateNodeType(nodeSummary);
        node?.updateTopology(nodeSummary, (downstreamNodes ?: []));
    }
    
    private void updateNodeType(NodeSummary nodeSummary) {
        if (nodeSummary.getType() == node?.getNodeType()) {
            // already has correct type; nothing to do
            return;
        }
        
        if (node != null) {
            node.dispose();
        }
        
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

        if (node != null) {
            addOwnedChild(node)
        }
        
        LOG.info("Node "+nodeId+" changed type to "+nodeSummary.getType());        
    }   
}
