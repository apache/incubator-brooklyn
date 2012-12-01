package brooklyn.catalog.internal;

import brooklyn.config.ConfigKey;


@SuppressWarnings("rawtypes")
public class CatalogConfigurationDto extends CatalogItemDtoAbstract<ConfigKey> {
    
    @Override
    public CatalogItemType getCatalogItemType() {
        return CatalogItemType.CONFIGURATION;
    }

    public Class<ConfigKey> getCatalogItemJavaType() { return ConfigKey.class; }
}
