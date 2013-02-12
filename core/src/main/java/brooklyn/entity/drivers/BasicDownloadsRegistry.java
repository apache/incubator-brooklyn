package brooklyn.entity.drivers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Set;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.basic.Attributes;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class BasicDownloadsRegistry implements DownloadsRegistry {

    private final List<Function<? super EntityDriver, ? extends DownloadTargets>> resolvers = Lists.newCopyOnWriteArrayList();
    
    /**
     * The default is (in-order) to:
     * <ol>
     *   <li>Use brooklyn properties for any download overrides defined there (see {@link DownloadPropertiesResolver}
     *   <li>Use the entity's Attributes.DOWNLOAD_URL
     * </ol>
     * @param config
     * @return
     */
    public static BasicDownloadsRegistry newDefault(StringConfigMap config) {
        BasicDownloadsRegistry result = new BasicDownloadsRegistry();
        result.registerResolver(new DownloadPropertiesResolver(config));
        result.registerResolver(DownloadResolvers.attributeSubstituter(Attributes.DOWNLOAD_URL));
        return result;
    }
    
    public static BasicDownloadsRegistry newEmpty() {
        return new BasicDownloadsRegistry();
    }
    
    @Override
    public void registerPrimaryResolver(Function<? super EntityDriver, ? extends DownloadTargets> resolver) {
        resolvers.add(0, checkNotNull(resolver, "resolver"));
    }

    @Override
    public void registerResolver(Function<? super EntityDriver, ? extends DownloadTargets> resolver) {
        resolvers.add(checkNotNull(resolver, "resolver"));
    }

    @Override
    public List<String> resolve(EntityDriver driver) {
        checkNotNull(driver, "driver");
        
        List<String> primaries = Lists.newArrayList();
        List<String> fallbacks = Lists.newArrayList();
        for (Function<? super EntityDriver, ? extends DownloadTargets> resolver : resolvers) {
            DownloadTargets vals = resolver.apply(driver);
            primaries.addAll(vals.getPrimaryLocations());
            fallbacks.addAll(vals.getFallbackLocations());
            if (!vals.canContinueResolving()) {
                break;
            }
        }
        
        Set<String> result = Sets.newLinkedHashSet();
        result.addAll(primaries);
        result.addAll(fallbacks);

        if (result.isEmpty()) {
            throw new IllegalArgumentException("No download resolver matched for driver "+driver);
        } else {
            return ImmutableList.copyOf(result);
        }
    }
}
