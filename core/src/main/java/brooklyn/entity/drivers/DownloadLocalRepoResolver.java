package brooklyn.entity.drivers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.StringConfigMap;
import brooklyn.entity.drivers.DownloadsRegistry.DownloadTargets;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;

public class DownloadLocalRepoResolver implements Function<EntityDriver, DownloadTargets> {
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DownloadLocalRepoResolver.class);

    public static final String LOCAL_REPO_ENABLED_PROPERTY = DownloadPropertiesResolver.DOWNLOAD_CONF_PREFIX+"localrepo.enabled";
    
    public static final String DEFAULT_LOCAL_REPO_URL = "file://$HOME/.brooklyn/repository/"+
            "${simpletype}/${version}/"+
            "<#if driver.downloadFilename??>${driver.downloadFilename}<#else>${simpletype?lower_case}-${version?lower_case}.${driver.downloadFileSuffix!\"tar.gz\"}</#if>";

    private final StringConfigMap config;

    public DownloadLocalRepoResolver(StringConfigMap config) {
        this.config = config;
    }
    
    public DownloadTargets apply(EntityDriver driver) {
        String localRepoEnabledStr = config.getFirst(LOCAL_REPO_ENABLED_PROPERTY);
        boolean localRepoEnabled = (localRepoEnabledStr == null) ? true : Boolean.parseBoolean(localRepoEnabledStr);
        
        if (localRepoEnabled) {
            String result = DownloadResolvers.substitute(driver, DEFAULT_LOCAL_REPO_URL, Functions.constant(ImmutableMap.<String,String>of()));
            return BasicDownloadTargets.builder().addPrimary(result).build();
        } else {
            return BasicDownloadTargets.empty();
        }
    }
}
