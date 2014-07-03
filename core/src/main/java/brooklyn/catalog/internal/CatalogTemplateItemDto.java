package brooklyn.catalog.internal;

import brooklyn.entity.Application;
import brooklyn.entity.proxying.EntitySpec;

public class CatalogTemplateItemDto extends CatalogItemDtoAbstract<Application,EntitySpec<? extends Application>> {

    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.TEMPLATE;
    }

    @Override
    public Class<Application> getCatalogItemJavaType() {
        return Application.class;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Class<EntitySpec<? extends Application>> getSpecType() {
        return (Class)EntitySpec.class;
    }

}
