package brooklyn.catalog.internal;

import brooklyn.entity.Application;

public class CatalogTemplateItemDto extends AbstractCatalogItem<Application> {

    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.TEMPLATE;
    }

    @Override
    public Class<Application> getCatalogItemJavaType() {
        return Application.class;
    }
    
}
