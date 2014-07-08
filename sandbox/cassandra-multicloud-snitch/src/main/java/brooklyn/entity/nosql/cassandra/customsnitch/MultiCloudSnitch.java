/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.cassandra.customsnitch;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Properties;

import org.apache.cassandra.exceptions.ConfigurationException;
import org.apache.cassandra.gms.ApplicationState;
import org.apache.cassandra.gms.EndpointState;
import org.apache.cassandra.gms.Gossiper;
import org.apache.cassandra.gms.IEndpointStateChangeSubscriber;
import org.apache.cassandra.gms.VersionedValue;
import org.apache.cassandra.io.util.FileUtils;
import org.apache.cassandra.locator.AbstractNetworkTopologySnitch;
import org.apache.cassandra.net.MessagingService;
import org.apache.cassandra.service.StorageService;
import org.apache.cassandra.utils.FBUtilities;
import org.apache.cassandra.utils.ResourceWatcher;
import org.apache.cassandra.utils.WrappedRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A snitch that can be configured to work across clouds. It uses and
 * extends the cassandra-rackdc.properties (which is used by the 
 * GossipingPropertyFileSnitch) to add a publicip and privateip 
 * configuration.
 * <p>
 * The code is very similar to Ec2MultiRegionSnitch, except that it uses
 * the the config file rather than querying EC2 to get the IPs.
 * <p>
 * If two nodes are in the same datacenter, they will attempt to communicate
 * using the privateip. If they are in different datacenters, they will use
 * the publicip.
 */
public class MultiCloudSnitch extends AbstractNetworkTopologySnitch implements IEndpointStateChangeSubscriber
{
    // FIXME Need to submit a pull request to Cassandra (1.2.x branch) for this snitch.
    // Or could enhance GossipingPropertyFileSnitch instead, and submit PR for that.
    
    protected static final Logger logger = LoggerFactory.getLogger(MultiCloudSnitch.class);
    
    public static final String SNITCH_PROPERTIES_FILENAME = "cassandra-rackdc.properties";

    private static final String DEFAULT_DC = "UNKNOWN-DC";
    private static final String DEFAULT_RACK = "UNKNOWN-RACK";
    
    protected String rack;
    protected String datacenter;
    protected InetAddress public_ip;
    protected String private_ip;

    private volatile boolean gossipStarted;

    public MultiCloudSnitch() throws ConfigurationException
    {
        reloadConfiguration();
        logger.info("CustomSnitch using datacenter: " + datacenter + ", rack: " + rack + ", publicip: " + public_ip + ", privateip: " + private_ip);

        try
        {
            FBUtilities.resourceToFile(SNITCH_PROPERTIES_FILENAME);
            Runnable runnable = new WrappedRunnable()
            {
                protected void runMayThrow() throws ConfigurationException
                {
                    reloadConfiguration();
                }
            };
            ResourceWatcher.watch(SNITCH_PROPERTIES_FILENAME, runnable, 60 * 1000);
        }
        catch (ConfigurationException ex)
        {
            logger.debug(SNITCH_PROPERTIES_FILENAME + " found, but does not look like a plain file. Will not watch it for changes");
        }
    }

    public void reloadConfiguration() throws ConfigurationException
    {
        HashMap<InetAddress, String[]> reloadedMap = new HashMap<InetAddress, String[]>();
        String DC_PROPERTY = "dc";
        String RACK_PROPERTY = "rack";
        String PUBLIC_IP_PROPERTY = "publicip";
        String PRIVATE_IP_PROPERTY = "privateip";

        Properties properties = new Properties();
        InputStream stream = null;
        try
        {
            stream = getClass().getClassLoader().getResourceAsStream(SNITCH_PROPERTIES_FILENAME);
            properties.load(stream);
        }
        catch (Exception e)
        {
            throw new ConfigurationException("Unable to read " + SNITCH_PROPERTIES_FILENAME, e);
        }
        finally
        {
            FileUtils.closeQuietly(stream);
        }

        datacenter = properties.getProperty(DC_PROPERTY);
        rack = properties.getProperty(RACK_PROPERTY);
        private_ip = checkNotNull(properties.getProperty(PRIVATE_IP_PROPERTY), "%s in %s", PRIVATE_IP_PROPERTY, SNITCH_PROPERTIES_FILENAME);
        String public_ip_str = checkNotNull(properties.getProperty(PUBLIC_IP_PROPERTY), "%s in %s", PUBLIC_IP_PROPERTY, SNITCH_PROPERTIES_FILENAME);
        try {
            public_ip = InetAddress.getByName(public_ip_str);
        }
        catch (UnknownHostException e)
        {
            throw new ConfigurationException("Unknown host " + public_ip_str, e);
        }
        
        logger.debug("CustomSnitch reloaded, using datacenter: " + datacenter + ", rack: " + rack + ", publicip: " + public_ip + ", privateip: " + private_ip);

        if (StorageService.instance != null) // null check tolerates circular dependency; see CASSANDRA-4145
            StorageService.instance.getTokenMetadata().invalidateCaches();

        if (gossipStarted)
            StorageService.instance.gossipSnitchInfo();
    }


    public String getRack(InetAddress endpoint)
    {
        if (endpoint.equals(FBUtilities.getBroadcastAddress()))
            return rack;
        EndpointState state = Gossiper.instance.getEndpointStateForEndpoint(endpoint);
        if (state == null || state.getApplicationState(ApplicationState.RACK) == null)
            return DEFAULT_RACK;
        return state.getApplicationState(ApplicationState.RACK).value;
    }

    public String getDatacenter(InetAddress endpoint)
    {
        if (endpoint.equals(FBUtilities.getBroadcastAddress()))
            return datacenter;
        EndpointState state = Gossiper.instance.getEndpointStateForEndpoint(endpoint);
        if (state == null || state.getApplicationState(ApplicationState.DC) == null)
            return DEFAULT_DC;
        return state.getApplicationState(ApplicationState.DC).value;
    }
    
    public void onJoin(InetAddress endpoint, EndpointState epState)
    {
        if (epState.getApplicationState(ApplicationState.INTERNAL_IP) != null)
            reConnect(endpoint, epState.getApplicationState(ApplicationState.INTERNAL_IP));
    }

    public void onChange(InetAddress endpoint, ApplicationState state, VersionedValue value)
    {
        if (state == ApplicationState.INTERNAL_IP)
            reConnect(endpoint, value);
    }

    public void onAlive(InetAddress endpoint, EndpointState state)
    {
        if (state.getApplicationState(ApplicationState.INTERNAL_IP) != null)
            reConnect(endpoint, state.getApplicationState(ApplicationState.INTERNAL_IP));
    }

    public void onDead(InetAddress endpoint, EndpointState state)
    {
        // do nothing
    }

    public void onRestart(InetAddress endpoint, EndpointState state)
    {
        // do nothing
    }

    public void onRemove(InetAddress endpoint)
    {
        // do nothing.
    }

    private void reConnect(InetAddress endpoint, VersionedValue versionedValue)
    {
        if (!getDatacenter(endpoint).equals(getDatacenter(public_ip)))
            return; // do nothing return back...

        try
        {
            InetAddress remoteIP = InetAddress.getByName(versionedValue.value);
            MessagingService.instance().getConnectionPool(endpoint).reset(remoteIP);
            logger.debug(String.format("Intiated reconnect to an Internal IP %s for the %s", remoteIP, endpoint));
        } catch (UnknownHostException e)
        {
            logger.error("Error in getting the IP address resolved: ", e);
        }
    }

    @Override
    public void gossiperStarting()
    {
        super.gossiperStarting();
        Gossiper.instance.addLocalApplicationState(ApplicationState.INTERNAL_IP, StorageService.instance.valueFactory.internalIP(private_ip));
        Gossiper.instance.register(this);
    }
}
