package brooklyn.entity.drivers;

import java.util.List;

import com.google.common.base.Function;

/**
 * A registry of "resolvers" for determining where to download the installers from, for different entities.
 * 
 * Most commonly, this is a URI. However, an EntityDriver implementation is free to interpret the 
 * String however is appropriate (e.g. the name of a custom package to install from the enterprise's 
 * package manager repository).
 * 
 * When using {@link resolve(EntityDriver)} to get the list of things to try (in-order until one succeeds),
 * the registry will go through each of the registered resolvers in-order to get their contributions.
 * These contributions are split into "primary" and "fallback". All of the primaries will be added to the
 * list first, and then all of the fallbacks.
 * 
 * @author aled
 */
public interface DownloadsRegistry {

    /**
     * Registers a resolver, to be tried before all other resolvers. 
     * 
     * @see registerResolver(Function)
     */
    public void registerPrimaryResolver(Function<? super EntityDriver, ? extends DownloadTargets> resolver);

    /**
     * Registers a resolver, to be tried after all other resolvers have been tried.
     * The function should not return null (instead see {@code BasicDownloadTargets.empty()}).
     * 
     * A resolver determines where the entity's installer should be obtained from. Most commonly, this
     * is a URI. However, an EntityDriver implementation is free to interpret the String however is 
     * appropriate (e.g. the name of a custom package to install from the enterprise's package manager
     * repository).
     */
    public void registerResolver(Function<? super EntityDriver, ? extends DownloadTargets> resolver);

    /**
     * Returns a list of options, to be tried in order until one of them works.
     */
    public List<String> resolve(EntityDriver driver);
    
    
    /**
     * Describes the download locations, and their order, to try.
     * 
     * @author aled
     */
    public interface DownloadTargets {
        /**
         * Gets the locations to try (in-order).
         */
        public List<String> getPrimaryLocations();

        /**
         * Gets the locations to try (in-order), to be used only after all primary locations 
         * have been tried.
         */
        public List<String> getFallbackLocations();

        /**
         * Indicates whether or not the results of this resolver are the last that should be used.
         * If returns false, {@link resolve(EntityDriver)} will not iterate over any other resolvers.
         * 
         * For example, useful in an enterprise to disable any other resolvers that would have 
         * resulted in going out to the public internet.
         */
        public boolean canContinueResolving();
    }
}
