package brooklyn.entity.nosql.riak;

import brooklyn.entity.basic.SoftwareProcessDriver;

import java.util.List;

public interface RiakNodeDriver extends SoftwareProcessDriver {

    public String getVmArgsLocation();
    public String getEtcDir();
    public String getAppConfigLocation();
    public String getHostname();
    public void joinCluster(List<String> clusterHosts);
}
