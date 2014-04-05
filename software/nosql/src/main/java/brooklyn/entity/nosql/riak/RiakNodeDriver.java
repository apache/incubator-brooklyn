package brooklyn.entity.nosql.riak;

import brooklyn.entity.basic.SoftwareProcessDriver;

import java.util.List;

public interface RiakNodeDriver extends SoftwareProcessDriver {

    public String getEtcDir();
    public String getPrivateIp();
    public void clusterJoin(RiakNode node);
    public void clusterLeave();
}
