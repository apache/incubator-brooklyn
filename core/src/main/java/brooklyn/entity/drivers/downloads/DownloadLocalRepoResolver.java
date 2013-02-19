package brooklyn.entity.drivers.downloads;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.drivers.downloads.DownloadResolverRegistry.DownloadRequirement;
import brooklyn.entity.drivers.downloads.DownloadResolverRegistry.DownloadTargets;
import brooklyn.event.basic.BasicConfigKey;

import com.google.common.base.Function;

public class DownloadLocalRepoResolver implements Function<DownloadRequirement, DownloadTargets> {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DownloadLocalRepoResolver.class);

    public static final ConfigKey<String> LOCAL_REPO_PATH = BasicConfigKey.builder(String.class)
            .name(DownloadPropertiesResolver.DOWNLOAD_CONF_PREFIX+"repo.local.path")
            .description("Fully qualified path of the local repo")
            .defaultValue("$HOME/.brooklyn/repository")
            .build();

    public static final ConfigKey<Boolean> LOCAL_REPO_ENABLED = BasicConfigKey.builder(Boolean.class)
            .name(DownloadPropertiesResolver.DOWNLOAD_CONF_PREFIX+"repo.local.enabled")
            .description("Whether to use the local repo for downloading entities, during installs")
            .defaultValue(true)
            .build();

    public static final String LOCAL_REPO_URL_PATTERN = "file://%s/"+
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

    public DownloadLocalRepoResolver(StringConfigMap config) {
        this.config = config;
    }
    
    public DownloadTargets apply(DownloadRequirement req) {
        Boolean enabled = config.getConfig(LOCAL_REPO_ENABLED);
        String path = config.getConfig(LOCAL_REPO_PATH);
        String url = String.format(LOCAL_REPO_URL_PATTERN, path);
        
        if (enabled) {
            Map<String, ?> subs = DownloadResolvers.getBasicSubscriptions(req);
            String result = DownloadResolvers.substitute(url, subs);
            return BasicDownloadTargets.builder().addPrimary(result).build();
            
        } else {
            return BasicDownloadTargets.empty();
        }
    }
}
