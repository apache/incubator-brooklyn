package brooklyn.catalog.internal;

import java.util.List;

import com.google.common.base.Preconditions;

import brooklyn.catalog.CatalogItem;

public class CatalogContextDo implements CatalogItem.CatalogItemContext {

//    private final CatalogDo catalog;
    private final CatalogContextDto contextDto;

    public CatalogContextDo(/*CatalogDo catalog,*/ CatalogContextDto contextDto) {
//        this.catalog = Preconditions.checkNotNull(catalog, "catalog");
        this.contextDto = Preconditions.checkNotNull(contextDto, "contextDto");
    }

    @Override
    public List<String> getBundles() {
        return contextDto.getBundles();
    }

}
