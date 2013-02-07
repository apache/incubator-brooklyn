package brooklyn.entity.drivers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import brooklyn.entity.basic.Attributes;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

public class BasicDownloadsRegistry implements DownloadsRegistry {

    private final List<Function<? super EntityDriver, String>> resolvers = Lists.newCopyOnWriteArrayList();
    
    public static BasicDownloadsRegistry newDefault() {
        BasicDownloadsRegistry result = new BasicDownloadsRegistry();
        result.registerResolver(DownloadResolvers.attributeSubstituter(Attributes.DOWNLOAD_URL));
        return result;
    }
    
    public static BasicDownloadsRegistry newEmpty() {
        return new BasicDownloadsRegistry();
    }
    
    @Override
    public void registerPrimaryResolver(Function<? super EntityDriver, String> resolver) {
        resolvers.add(0, checkNotNull(resolver, "resolver"));
    }

    @Override
    public void registerResolver(Function<? super EntityDriver, String> resolver) {
        resolvers.add(checkNotNull(resolver, "resolver"));
    }

    @Override
    public String resolve(EntityDriver driver) {
        checkNotNull(driver, "driver");
        
        for (Function<? super EntityDriver, String> resolver : resolvers) {
            String result = resolver.apply(driver);
            if (result != null) {
                return result;
            }
        }
        
        throw new IllegalArgumentException("No download resolver matched for driver "+driver);
    }
}
