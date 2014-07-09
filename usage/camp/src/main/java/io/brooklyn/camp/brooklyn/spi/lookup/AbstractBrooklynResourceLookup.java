package io.brooklyn.camp.brooklyn.spi.lookup;

import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.PlatformRootSummary;
import io.brooklyn.camp.spi.collection.AbstractResourceLookup;
import brooklyn.management.ManagementContext;

public abstract class AbstractBrooklynResourceLookup<T extends AbstractResource>  extends AbstractResourceLookup<T> {

    protected final PlatformRootSummary root;
    protected final ManagementContext bmc;

    public AbstractBrooklynResourceLookup(PlatformRootSummary root, ManagementContext bmc) {
        this.root = root;
        this.bmc = bmc;
    }

}
