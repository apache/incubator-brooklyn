package io.brooklyn.camp.spi;

import java.util.ArrayList;
import java.util.List;

public abstract class PlatformTransaction {

    protected List<Object> additions = new ArrayList<Object>();
    
    /** apply the transaction */
    public abstract void commit();
    
    public PlatformTransaction add(AssemblyTemplate at) {
        additions.add(at);
        return this;
    }

    public PlatformTransaction add(ApplicationComponentTemplate act) {
        additions.add(act);
        return this;
    }

    public PlatformTransaction add(PlatformComponentTemplate pct) {
        additions.add(pct);
        return this;
    }

}
