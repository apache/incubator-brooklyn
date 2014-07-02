package brooklyn.catalog.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import brooklyn.catalog.CatalogItem;

public class CatalogLibrariesDto implements CatalogItem.CatalogItemLibraries {

    List<String> bundles = new CopyOnWriteArrayList<String>();

    public void addBundle(String url) {
        bundles.add(url);
    }

    public List<String> getBundles() {
        return bundles;
    }

}
