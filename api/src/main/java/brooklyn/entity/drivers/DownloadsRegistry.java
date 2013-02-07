package brooklyn.entity.drivers;

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
    public void registerPrimaryResolver(Function<? super EntityDriver, String> resolver);

    /**
     * Registers a resolver, to be tried after all other resolvers have been tried.
     * The resolver can return null, which means that the next resolver (if any) will be tried.
     * 
     * A resolver determines where the entity's installer should be obtained from. Most commonly, this
     * is a URI. However, an EntityDriver implementation is free to interpret the String however is 
     * appropriate (e.g. the name of a custom package to install from the enterprise's package manager
     * repository).
     */
    public void registerResolver(Function<? super EntityDriver, String> resolver);

    public String resolve(EntityDriver driver);
}
