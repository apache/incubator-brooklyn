package com.cloudsoftcorp.monterey.brooklyn.entity;

import java.io.Serializable;
import java.net.URL;

import com.cloudsoftcorp.monterey.clouds.NetworkId;
import com.cloudsoftcorp.util.web.client.CredentialsConfig;
import com.google.common.base.Preconditions;

/**
 * The MontereyNetworkSummaryImpl
 *
 * @author aled
 **/
public class MontereyNetworkConnectionDetails implements Serializable {

    private static final long serialVersionUID = 4356143284218945471L;
    
    private NetworkId networkId;
    private URL managementUrl;
    private CredentialsConfig adminCredential;

    public MontereyNetworkConnectionDetails(NetworkId networkId, URL managementUrl, CredentialsConfig adminCredential) {
        this.networkId = Preconditions.checkNotNull(networkId, "networkId");
        this.managementUrl = Preconditions.checkNotNull(managementUrl, "managementUrl");
        this.adminCredential = Preconditions.checkNotNull(adminCredential, "managementUrl");
    }
    
    @SuppressWarnings("unused")
    private MontereyNetworkConnectionDetails() { /* for gson */ }

    public NetworkId getNetworkId() {
        return networkId;
    }

    public URL getManagementUrl() {
        return managementUrl;
    }
    
    public CredentialsConfig getWebApiAdminCredential() {
        return adminCredential;
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
