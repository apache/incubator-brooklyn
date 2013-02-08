package brooklyn.entity.drivers;

import java.util.List;

import com.google.common.base.Function;

/**
 * A registry of "resolvers" for determining where to download the installer for different entities.
 * 
 * Most commonly, this is a URI. However, an EntityDriver implementation is free to interpret the 
 * String however is appropriate (e.g. the name of a custom package to install from the enterprise's 
 * package manager repository).
 * 
 * @author aled
 */
public interface DownloadsRegistry {

    /**
     * Registers a resolver, to be tried before all other resolvers. 
     * 
     * @see registerResolver(Function)
     */
    public void registerPrimaryResolver(Function<? super EntityDriver, List<String>> resolver);

    /**
     * Registers a resolver, to be tried after all other resolvers have been tried.
     * The resolver can return null or empty, which means that the next resolver (if any) will be tried.
     * 
     * A resolver determines where the entity's installer should be obtained from. Most commonly, this
     * is a URI. However, an EntityDriver implementation is free to interpret the String however is 
     * appropriate (e.g. the name of a custom package to install from the enterprise's package manager
     * repository).
     */
    public void registerResolver(Function<? super EntityDriver, List<String>> resolver);

    /**
     * Returns a list of options, to be tried in order until one of them works.
     */
    public List<String> resolve(EntityDriver driver);
}
