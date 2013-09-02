package brooklyn.catalog.internal;

import brooklyn.catalog.Catalog;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationBuilder;

public class MyCatalogItems {

    @Catalog(description="Some silly app test")
    public static class MySillyAppTemplate extends AbstractApplication {
        @Override
        public void init() {
            // no-op
        }
    }
    
    @Catalog(description="Some silly app builder test")
    public static class MySillyAppBuilderTemplate extends ApplicationBuilder {
        @Override protected void doBuild() {
        }
    }
}
