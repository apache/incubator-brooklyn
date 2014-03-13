package brooklyn.test.entity;

import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.CatalogDtoUtils;
import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.management.internal.LocalManagementContext;

/** management context which forces an empty catalog to prevent scanning / interacting with local filesystem.
 * TODO this class could also force standard properties
 * TODO this should be more widely used in tests! */
public class LocalManagementContextForTests extends LocalManagementContext {

    {
        catalog = new BasicBrooklynCatalog(this, CatalogDtoUtils.newDefaultLocalScanningDto(CatalogScanningModes.NONE));
    }
    
}
