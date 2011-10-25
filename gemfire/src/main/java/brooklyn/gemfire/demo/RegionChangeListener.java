package brooklyn.gemfire.demo;

import java.io.IOException;

public interface RegionChangeListener {

    public void regionAdded( String name ) throws IOException;

    public boolean regionRemoved( String name ) throws IOException;

}