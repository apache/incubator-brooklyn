package brooklyn.entity.drivers.downloads;

import java.util.Map;

import brooklyn.entity.drivers.EntityDriver;

/**
 * Used by an {@link EntityDriver} to obtain the download locations when installing an entity.
 * 
 * Most commonly, the {@link DownloadResolver}'s targets are URIs. However, an EntityDriver 
 * implementation is free to interpret the String however is appropriate (e.g. the name of a 
 * custom package to install from the enterprise's package manager repository).
 * 
 * @author aled
 */
public interface DownloadResolverFactory {

    /**
     * For installing the main entity.
     * Returns a list of options, to be tried in order until one of them works.
     */
    public DownloadResolver resolve(EntityDriver driver);

    /**
     * For installing the main entity.
     * Returns a list of options, to be tried in order until one of them works.
     */
    public DownloadResolver resolve(EntityDriver driver, Map<String,?> properties);

    /**
     * For installing an entity add-on.
     * Returns a list of options, to be tried in order until one of them works.
     * This is used for resolving the download for an "add-on" - e.g. an additional module required 
     * during an entity's installation. Common properties include:
     * <ul>
     *   <li>addonversion: the required version of the add-on
     * </ul>
     */
    public DownloadResolver resolve(EntityDriver driver, String addonName, Map<String,?> addonProperties);
}
