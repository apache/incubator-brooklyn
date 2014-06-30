package brooklyn.catalog.internal;

import java.util.List;

import com.google.common.base.Preconditions;

import brooklyn.catalog.CatalogItem;

public class CatalogLibrariesDo implements CatalogItem.CatalogItemLibraries {

    private final CatalogLibrariesDto librariesDto;

    public CatalogLibrariesDo(CatalogLibrariesDto librariesDto) {
        this.librariesDto = Preconditions.checkNotNull(librariesDto, "librariesDto");
    }

    @Override
    public List<String> getBundles() {
        return librariesDto.getBundles();
    }

}
