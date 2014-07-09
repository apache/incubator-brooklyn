package brooklyn.catalog.internal;

import brooklyn.config.ConfigKey;


@SuppressWarnings("rawtypes")
public class CatalogConfigurationDto extends CatalogItemDtoAbstract<ConfigKey,Void> {
    
    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.CONFIGURATION;
    }

    public Class<ConfigKey> getCatalogItemJavaType() { return ConfigKey.class; }

    @Override
    public String getRegisteredTypeName() {
        return getJavaType();
    }
    
    @Override
    public Class<Void> getSpecType() {
        return null;
    }
    
}
