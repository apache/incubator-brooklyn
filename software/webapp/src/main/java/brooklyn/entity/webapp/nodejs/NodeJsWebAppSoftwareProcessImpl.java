package brooklyn.entity.webapp.nodejs;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.JavaAppUtils;
import brooklyn.entity.webapp.WebAppServiceMethods;
import brooklyn.location.access.BrooklynAccessUtils;

import com.google.common.base.Throwables;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;

public abstract class NodeJsWebAppSoftwareProcessImpl extends SoftwareProcessImpl implements NodeJsWebAppSoftwareProcess {

    private static final Logger LOG = LoggerFactory.getLogger(NodeJsWebAppSoftwareProcessImpl.class);

    public NodeJsWebAppSoftwareProcessImpl() {
        super();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        WebAppServiceMethods.connectWebAppServerPolicies(this);
    }

    public NodeJsWebAppDriver getDriver() {
        return (NodeJsWebAppDriver) super.getDriver();
    }
    
    @Override
    protected void doStop() {
        super.doStop();
        // zero our workrate derived workrates.
        // TODO might not be enough, as policy may still be executing and have a record of historic vals; should remove policies
        // (also not sure we want this; implies more generally a responsibility for sensors to announce things when disconnected,
        // vs them just showing the last known value...)
        setAttribute(REQUESTS_PER_SECOND_LAST, 0D);
        setAttribute(REQUESTS_PER_SECOND_IN_WINDOW, 0D);
    }

}
