package brooklyn.catalog.internal;

import brooklyn.entity.Application;
import brooklyn.entity.proxying.EntitySpec;

public class CatalogTemplateItemDto extends CatalogItemDtoAbstract<Application,EntitySpec<?>> {

    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.TEMPLATE;
    }

    @Override
    public Class<Application> getCatalogItemJavaType() {
        return Application.class;
    }
    
}
