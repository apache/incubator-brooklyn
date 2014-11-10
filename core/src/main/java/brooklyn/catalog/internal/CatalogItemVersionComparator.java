package brooklyn.catalog.internal;

import java.util.Comparator;

import brooklyn.catalog.CatalogItem;

public class CatalogItemVersionComparator implements Comparator<CatalogItem<?, ?>> {

    protected CatalogItemVersionComparator() {}

    @Override
    public int compare(CatalogItem<?, ?> o1, CatalogItem<?, ?> o2) {
        return -o1.getVersion().compareTo(o2.getVersion());
    }

}
