package brooklyn.management.ha;

import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;

/**
 * For choosing which management node to promote, when master detected as failed or stopped.
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public interface MasterChooser {

    ManagerMemento choose(ManagementPlaneMemento memento, Duration heartbeatTimeout, String ownNodeId, long timeNow);
}
