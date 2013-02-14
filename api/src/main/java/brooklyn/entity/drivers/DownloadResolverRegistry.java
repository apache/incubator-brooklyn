package brooklyn.entity.drivers;

import java.util.List;
import java.util.Map;

import com.google.common.base.Function;

/**
 * A registry of "resolvers" for determining where to download the installers from, for different entities.
 * 
 * When using {@link resolve(EntityDriver)} to get the list of things to try (in-order until one succeeds),
 * the registry will go through each of the registered resolvers in-order to get their contributions.
 * These contributions are split into "primary" and "fallback". All of the primaries will be added to the
 * list first, and then all of the fallbacks.
 * 
 * @author aled
 */
public interface DownloadResolverRegistry extends DownloadResolverFactory {

    /**
     * Registers a producer, to be tried before all other producers.
     * 
     * A "producer" will generate the download targets to be tried, when installing a given entity
     * or entity add-on.
     * 
     * The function should not return null (instead see {@code BasicDownloadTargets.empty()}).
     * 
     * @see registerResolver(Function)
     */
    public void registerPrimaryProducer(Function<? super DownloadRequirement, ? extends DownloadTargets> resolver);

    /**
     * Registers a producer, to be tried after all other registered producers have been tried.
     * The function should not return null (instead see {@code BasicDownloadTargets.empty()}).
     */
    public void registerProducer(Function<? super DownloadRequirement, ? extends DownloadTargets> resolver);

    /**
     * Registers a producer for generating the expected filename of the download artifact.
     * 
     * If all such registered producers return null, then default behaviour is to infer the download
     * name from the first target in the {@link resolve(EntityDriver)} result. 
     */
    public void registerFilenameProducer(Function<? super DownloadRequirement, String> producer);

    /**
     * Gives artifact meta-data for what is required to be downloaded.
     * 
     * @author aled
     */
    public interface DownloadRequirement {
        /**
         * The {@link EntityDriver} that this download is for.
         */
        public EntityDriver getEntityDriver();

        /**
         * The name of the add-on to be downloaded, or null if it is the main installed.
         * For example, can be used to specify nginx sticky-module or pcre download.
         */
        public String getAddonName();
        
        /**
         * Default properties for this download. These will be made available when resolving the
         * download template.
         * 
         * For the main entity download, properties include:
         * <ul>
         *   <li>fileSuffix: expected file suffix 
         * </ul>
         * 
         * For an add-on, common properties include:
         * <ul>
         *   <li>version: version of the add-on to be used
         *   <li>fileSuffix: expected file suffix 
         * </ul>
         */
        public Map<String, ?> getProperties();
    }
    
    
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
