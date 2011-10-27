package brooklyn.gemfire.demo;

import java.io.IOException;
import java.util.List;

import brooklyn.gemfire.demo.HubManager.RegionNode;

public interface RegionChangeListener {

    public boolean regionAdded(String name, boolean recurse) throws IOException;
    public boolean regionRemoved( String name ) throws IOException;
    public RegionNode regionTree();
    public List<String> regionList();

}