package brooklyn.catalog.internal;

import java.util.List;
import java.util.Map;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.util.xstream.EnumCaseForgivingSingleValueConverter;
import brooklyn.util.xstream.XmlSerializer;

public class CatalogXmlSerializer extends XmlSerializer<Object> {
    
    public CatalogXmlSerializer() {
        xstream.aliasType("list", List.class);
        xstream.aliasType("map", Map.class);

        xstream.useAttributeFor("id", String.class);

        xstream.aliasType("catalog", CatalogDto.class);
        xstream.useAttributeFor(CatalogDto.class, "url");
        xstream.addImplicitCollection(CatalogDto.class, "entries" 
                //, "template", CatalogTemplateItem.class
                );
        
        xstream.aliasType("template", CatalogTemplateItemDto.class);
        xstream.aliasType("entity", CatalogEntityItemDto.class);
        xstream.aliasType("policy", CatalogPolicyItemDto.class);
        
        xstream.useAttributeFor(CatalogItemDtoAbstract.class, "type");
        xstream.useAttributeFor(CatalogItemDtoAbstract.class, "name");
        
        xstream.useAttributeFor(CatalogClasspathDto.class, "scan");
        xstream.addImplicitCollection(CatalogClasspathDto.class, "entries", "entry", String.class);
        xstream.registerConverter(new EnumCaseForgivingSingleValueConverter(CatalogScanningModes.class));
    }
    
}
