package brooklyn.catalog.internal;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;


public class CatalogEntityItemDto extends CatalogItemDtoAbstract<Entity,EntitySpec<?>> {
    
    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.ENTITY;
    }

    @Override
    public Class<Entity> getCatalogItemJavaType() {
        return Entity.class;
    }

}
