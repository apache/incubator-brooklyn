package brooklyn.entity.drivers;

import java.util.Map;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.drivers.DownloadResolverRegistry.DownloadRequirement;
import brooklyn.entity.drivers.DownloadResolverRegistry.DownloadTargets;

import com.google.common.base.Function;
import com.google.common.collect.Maps;

/**
 * Retrieves the DOWNLOAD_URL or DOWNLOAD_ADDON_URLS attribute of a given entity, and performs the
 * template substitutions to generate the download URL.
 * 
 * @author aled
 */
public class DownloadUrlAttributeProducer extends DownloadResolvers.Substituter implements Function<DownloadRequirement, DownloadTargets> {
    public DownloadUrlAttributeProducer() {
        super(
            new Function<DownloadRequirement, String>() {
                @Override public String apply(DownloadRequirement input) {
                    if (input.getAddonName() == null) {
                        return input.getEntityDriver().getEntity().getAttribute(Attributes.DOWNLOAD_URL);
                    } else {
                        String addon = input.getAddonName();
                        Map<String, String> addonUrls = input.getEntityDriver().getEntity().getAttribute(Attributes.DOWNLOAD_ADDON_URLS);
                        return (addonUrls != null) ? addonUrls.get(addon) : null;
                    }
                }
            },
            new Function<DownloadRequirement, Map<String,?>>() {
                @Override public Map<String,?> apply(DownloadRequirement input) {
                    Map<String,Object> result = Maps.newLinkedHashMap();
                    if (input.getAddonName() == null) {
                        result.putAll(DownloadResolvers.getBasicEntitySubstitutions(input.getEntityDriver()));
                    } else {
                        result.putAll(DownloadResolvers.getBasicAddonSubstitutions(input.getEntityDriver(), input.getAddonName()));
                    }
                    result.putAll(input.getProperties());
                    return result;
                }
            });
    }
}
