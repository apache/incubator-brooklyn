package brooklyn.entity.drivers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.basic.Attributes;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class BasicDownloadsRegistry implements DownloadsRegistry {

    private final List<Function<? super EntityDriver, List<String>>> resolvers = Lists.newCopyOnWriteArrayList();
    
    public static BasicDownloadsRegistry newDefault(StringConfigMap config) {
        BasicDownloadsRegistry result = new BasicDownloadsRegistry();
        result.registerResolver(DownloadResolvers.attributeSubstituter(Attributes.DOWNLOAD_URL));
        result.registerResolver(new DownloadPropertiesResolver(config));
        return result;
    }
    
    public static BasicDownloadsRegistry newEmpty() {
        return new BasicDownloadsRegistry();
    }
    
    @Override
    public void registerPrimaryResolver(Function<? super EntityDriver, List<String>> resolver) {
        resolvers.add(0, checkNotNull(resolver, "resolver"));
    }

    @Override
    public void registerResolver(Function<? super EntityDriver, List<String>> resolver) {
        resolvers.add(checkNotNull(resolver, "resolver"));
    }

    @Override
    public List<String> resolve(EntityDriver driver) {
        checkNotNull(driver, "driver");
        
        for (Function<? super EntityDriver, List<String>> resolver : resolvers) {
            List<String> result = resolver.apply(driver);
            if (result != null && !result.isEmpty()) {
                return result;
            }
        }
        
        throw new IllegalArgumentException("No download resolver matched for driver "+driver);
    }
}
