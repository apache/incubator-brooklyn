package brooklyn.entity.nosql.riak;

import brooklyn.entity.basic.SoftwareProcessDriver;

public interface RiakNodeDriver extends SoftwareProcessDriver {

    public String getRiakEtcDir();

    public void joinCluster(String nodeName);

    public void leaveCluster();

    public void recoverFailedNode(String nodeName);

    public void commitCluster();
}
