package brooklyn.management.ha;

import java.util.Map;

import com.google.common.annotations.Beta;

/**
 * Meta-data about the management plane - the management nodes and who is currently master.
 * Does not contain any data about the entities under management.
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public interface ManagementPlaneMemento {

    // TODO Any better name for this? (e.g. including "high availability" or some such in name - but probably not?)
    
    // TODO Add getPlaneId(); but first need to set it sensibly on each management node
    
    String getMasterNodeId();
    
    Map<String, ManagerMemento> getNodes();

    String toVerboseString();
}
