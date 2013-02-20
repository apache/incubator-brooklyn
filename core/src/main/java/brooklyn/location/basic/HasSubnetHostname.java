package brooklyn.location.basic;

import com.google.common.annotations.Beta;

public interface HasSubnetHostname {

    @Beta
    /** returns a hostname for use internally within a subnet / VPC */
    String getSubnetHostname();
    
}
