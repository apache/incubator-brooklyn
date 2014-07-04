package brooklyn.catalog.internal;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import brooklyn.catalog.CatalogItem;

public class CatalogLibrariesDto implements CatalogItem.CatalogItemLibraries {

    private static Logger LOG = LoggerFactory.getLogger(CatalogLibrariesDto.class);

    // TODO: Incorporate name and version into entries
    private List<String> bundles = new CopyOnWriteArrayList<String>();

    public void addBundle(String url) {
        Preconditions.checkNotNull(bundles, "Cannot add a bundle to a deserialized DTO");
        bundles.add(Preconditions.checkNotNull(url, "url"));
    }

    /**
     * @return An immutable copy of the bundle URLs referenced by this object
     */
    public List<String> getBundles() {
        if (bundles == null) {
            // can be null on deserialization
            return Collections.emptyList();
        }
        return ImmutableList.copyOf(bundles);
    }

    /**
     * Parses an instance of CatalogLibrariesDto from the given List. Expects the list entries
     * to be maps of string -> string. Will skip items that are not.
     */
    public static CatalogLibrariesDto fromList(List<?> possibleLibraries) {
        CatalogLibrariesDto dto = new CatalogLibrariesDto();
        for (Object object : possibleLibraries) {
            if (object instanceof Map) {
                Map entry = (Map) object;
                String name = stringValOrNull(entry, "name");
                String version = stringValOrNull(entry, "version");
                String url = stringValOrNull(entry, "url");
                dto.addBundle(url);
            } else {
                LOG.debug("Unexpected entry in libraries list not instance of map: " + object);
            }
        }

        return dto;
    }

    private static String stringValOrNull(Map map, String key) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }
}
