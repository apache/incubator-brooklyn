package brooklyn.gemfire.api;

import java.io.IOException;
import java.util.List;

import brooklyn.gemfire.api.HubManager.RegionNode;

public interface RegionManager {

    public boolean addRegion(String name, boolean createIntermediate) throws IOException;
    public boolean removeRegion(String name) throws IOException;
    public RegionNode regionTree();
    public List<String> regionList();

}