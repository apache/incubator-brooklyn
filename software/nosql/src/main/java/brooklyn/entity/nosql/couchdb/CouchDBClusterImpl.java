package brooklyn.entity.nosql.couchdb;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;

/**
 * Implementation of {@link CouchDBCluster}.
 */
public class CouchDBClusterImpl extends DynamicClusterImpl implements CouchDBCluster {

    private static final Logger log = LoggerFactory.getLogger(CouchDBClusterImpl.class);

    public CouchDBClusterImpl() {
    }

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the CouchDB nodes.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, EntitySpec.create(CouchDBNode.class));
    }

    @Override
    public String getClusterName() {
        return getAttribute(CLUSTER_NAME);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        setAttribute(Startable.SERVICE_UP, calculateServiceUp());
    }

    @Override
    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
    }
}
