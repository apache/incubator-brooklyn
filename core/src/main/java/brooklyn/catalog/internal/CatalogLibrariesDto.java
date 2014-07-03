package brooklyn.catalog.internal;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import brooklyn.catalog.CatalogItem;

public class CatalogLibrariesDto implements CatalogItem.CatalogItemLibraries {

    private List<String> bundles = new CopyOnWriteArrayList<String>();

    public void addBundle(String url) {
        Preconditions.checkNotNull(url, "Cannot add a bundle to a deserialized DTO");
        bundles.add( Preconditions.checkNotNull(url) );
    }

    /** @return An immutable copy of the bundle URLs referenced by this object */
    public List<String> getBundles() {
        if (bundles==null)  {
            // can be null on deserialization
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(bundles);
    }

}
