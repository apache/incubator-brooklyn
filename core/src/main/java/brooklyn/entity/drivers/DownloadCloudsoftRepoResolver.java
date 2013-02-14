package brooklyn.entity.drivers;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.drivers.DownloadResolverRegistry.DownloadRequirement;
import brooklyn.entity.drivers.DownloadResolverRegistry.DownloadTargets;
import brooklyn.event.basic.BasicConfigKey;

import com.google.common.base.Function;

public class DownloadCloudsoftRepoResolver implements Function<DownloadRequirement, DownloadTargets> {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DownloadCloudsoftRepoResolver.class);

    public static final ConfigKey<String> CLOUDSOFT_REPO_URL = BasicConfigKey.builder(String.class)
            .name(DownloadPropertiesResolver.DOWNLOAD_CONF_PREFIX+"repo.cloudsoft.url")
            .description("Whether to use the cloudsoft repo for downloading entities, during installs")
            .defaultValue("http://downloads.cloudsoftcorp.com/brooklyn/repository")
            .build();

    public static final ConfigKey<Boolean> CLOUDSOFT_REPO_ENABLED = BasicConfigKey.builder(Boolean.class)
            .name(DownloadPropertiesResolver.DOWNLOAD_CONF_PREFIX+"repo.cloudsoft.enabled")
            .description("Whether to use the cloudsoft repo for downloading entities, during installs")
            .defaultValue(true)
            .build();
    
    public static final String CLOUDSOFT_REPO_URL_PATTERN = "%s/"+
            "${simpletype}/${version}/"+
            "<#if filename??>"+
                "${filename}" +
            "<#else>"+
              "<#if addon??>"+
                "${simpletype?lower_case}-${addon?lower_case}-${addonversion?lower_case}.${fileSuffix!\"tar.gz\"}"+
              "<#else>"+
                  "${simpletype?lower_case}-${version?lower_case}.${fileSuffix!\"tar.gz\"}"+
              "</#if>"+
            "</#if>";


    private final StringConfigMap config;

    public DownloadCloudsoftRepoResolver(StringConfigMap config) {
        this.config = config;
    }
    
    public DownloadTargets apply(DownloadRequirement req) {
        Boolean enabled = config.getConfig(CLOUDSOFT_REPO_ENABLED);
        String baseUrl = config.getConfig(CLOUDSOFT_REPO_URL);
        String url = String.format(CLOUDSOFT_REPO_URL_PATTERN, baseUrl);
        
        if (enabled) {
            Map<String, ?> subs = DownloadResolvers.getBasicSubscriptions(req);
            String result = DownloadResolvers.substitute(url, subs);
            return BasicDownloadTargets.builder().addPrimary(result).build();
            
        } else {
            return BasicDownloadTargets.empty();
        }
    }
}
