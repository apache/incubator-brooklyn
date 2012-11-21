package brooklyn.catalog.internal;

import brooklyn.entity.Entity;


public class CatalogEntityItemDto extends AbstractCatalogItem<Entity> {
    
    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.ENTITY;
    }

    @Override
    public Class<Entity> getCatalogItemJavaType() {
        return Entity.class;
    }

}
