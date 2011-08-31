package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.io.Serializable;
import java.net.URL;

import com.cloudsoftcorp.monterey.clouds.NetworkId;
import com.cloudsoftcorp.monterey.node.api.NodeId;
import com.cloudsoftcorp.util.web.client.CredentialsConfig;
import com.google.common.base.Preconditions;

/**
 * The MontereyNetworkSummaryImpl
 *
 * @author aled
 **/
public class MontereyNetworkConnectionDetails implements Serializable {

    private static final long serialVersionUID = 4356143284218945471L;
    
    final NetworkId networkId;
    final URL managementUrl;
    final CredentialsConfig webApiAdminCredential;
    final CredentialsConfig webApiClientCredential;
    final NodeId monitorAddress;
    final NodeId managerAddress;
    
    public MontereyNetworkConnectionDetails(NetworkId networkId, URL managementUrl, CredentialsConfig adminCredential, CredentialsConfig clientCredential, NodeId monitorAddress, NodeId managerAddress) {
        this.networkId = Preconditions.checkNotNull(networkId, "networkId");
        this.managementUrl = Preconditions.checkNotNull(managementUrl, "managementUrl");
        this.webApiAdminCredential = Preconditions.checkNotNull(adminCredential, "adminCredential");
        this.webApiClientCredential = Preconditions.checkNotNull(clientCredential, "clientCredential");
        this.monitorAddress = Preconditions.checkNotNull(monitorAddress, "monitorAddress");
        this.managerAddress = Preconditions.checkNotNull(managerAddress, "managerAddress");
    }
    
    @Override
    public String toString() {
        return managementUrl+"("+networkId+")";
    }
    
    @Override
    public boolean equals(Object obj) {
        return (obj instanceof MontereyNetworkConnectionDetails) && networkId.equals(((MontereyNetworkConnectionDetails)obj).networkId);
    }
    
    @Override
    public int hashCode() {
        return networkId.hashCode();
    }
}
