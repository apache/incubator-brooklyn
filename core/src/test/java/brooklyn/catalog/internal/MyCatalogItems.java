package brooklyn.catalog.internal;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.AbstractApplication;

@SuppressWarnings("serial")
public class MyCatalogItems {

    @Catalog(description="Some silly test")
    public static class MySillyAppTemplate extends AbstractApplication {}
    
}
