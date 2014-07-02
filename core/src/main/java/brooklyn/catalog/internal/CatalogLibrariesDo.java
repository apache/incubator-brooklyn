package brooklyn.catalog.internal;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

import brooklyn.catalog.CatalogItem;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.OsgiManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Time;

public class CatalogLibrariesDo implements CatalogItem.CatalogItemLibraries {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogLibrariesDo.class);
    private final CatalogLibrariesDto librariesDto;


    public CatalogLibrariesDo(CatalogLibrariesDto librariesDto) {
        this.librariesDto = Preconditions.checkNotNull(librariesDto, "librariesDto");
    }

    @Override
    public List<String> getBundles() {
        return librariesDto.getBundles();
    }

    /**
     * Registers all bundles with the management context's OSGi framework.
     */
    void load(ManagementContext managementContext) {
        ManagementContextInternal mgmt = (ManagementContextInternal) managementContext;
        Maybe<OsgiManager> osgi = mgmt.getOsgiManager();
        List<String> bundles = getBundles();
        if (osgi.isAbsent()) {
            LOG.warn("{} not loading bundles in {} because osgi manager is unavailable. Bundles: {}",
                    new Object[]{this, managementContext, Joiner.on(", ").join(bundles)});
            return;
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("{} loading bundles in {}: {}",
                    new Object[]{this, managementContext, Joiner.on(", ").join(bundles)});
        }
        Stopwatch timer = Stopwatch.createStarted();
        for (String bundleUrl : bundles) {
            osgi.get().registerBundle(bundleUrl);
        }
        LOG.debug("Catalog registered {} bundles in {}", bundles.size(), Time.makeTimeStringRounded(timer));
    }

}
