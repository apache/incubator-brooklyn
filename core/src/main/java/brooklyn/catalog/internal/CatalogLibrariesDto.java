package brooklyn.catalog.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.collect.ImmutableList;

import brooklyn.catalog.CatalogItem;

public class CatalogLibrariesDto implements CatalogItem.CatalogItemLibraries {

    private List<String> bundles = new CopyOnWriteArrayList<String>();

    public void addBundle(String url) {
        bundles.add(url);
    }

    /** @return An immutable copy of the bundle URLs referenced by this object */
    public List<String> getBundles() {
        return ImmutableList.copyOf(bundles);
    }

}
