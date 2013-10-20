package brooklyn.location.basic;

import com.google.common.annotations.Beta;

@Beta
public interface HasSubnetHostname {

    /** returns a hostname for use internally within a subnet / VPC */
    @Beta
    String getSubnetHostname();

    /** returns an IP for use internally within a subnet / VPC */
    String getSubnetIp();
}
