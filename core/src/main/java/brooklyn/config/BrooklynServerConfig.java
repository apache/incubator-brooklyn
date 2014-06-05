package brooklyn.config;

import static brooklyn.entity.basic.ConfigKeys.newStringConfigKey;

import java.io.File;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.os.Os;
import brooklyn.util.text.Strings;

/** config keys for the brooklyn server */
public class BrooklynServerConfig {


    private static final Logger log = LoggerFactory.getLogger(BrooklynServerConfig.class);
    
    /** provided for setting; consumers should use {@link #getMgmtBaseDir(ManagementContext)} */ 
    public static final ConfigKey<String> MGMT_BASE_DIR = newStringConfigKey(
            "brooklyn.base.dir", "Directory for reading and writing all brooklyn server data", 
            Os.mergePaths("~", ".brooklyn"));
    
    @Deprecated /** @deprecated since 0.7.0 use BrooklynServerConfig routines */
    // copied here so we don't have back-ref to BrooklynConfigKeys
    public static final ConfigKey<String> BROOKLYN_DATA_DIR = newStringConfigKey(
            "brooklyn.datadir", "Directory for writing all brooklyn data");

    /** also used for containers */
    public static final String PERSISTENCE_PATH_SEGMENT = "brooklyn-persisted-state";
    
    /** provided for setting; consumers should use {@link #getPersistenceDir(ManagementContext)},
     * but note for object stores this may be treated specially */ 
    public static final ConfigKey<String> PERSISTENCE_DIR = newStringConfigKey(
        "brooklyn.persistence.dir", "Directory for writing brooklyn persisted state; if not absolute, taken relative to mgmt base", 
        Os.mergePaths(PERSISTENCE_PATH_SEGMENT, "data"));

    public static String getMgmtBaseDir(ManagementContext mgmt) {
        return getMgmtBaseDir(mgmt.getConfig());
    }
    
    public static String getMgmtBaseDir(StringConfigMap brooklynProperties) {
        String base = (String) brooklynProperties.getConfigRaw(MGMT_BASE_DIR, true).orNull();
        if (base==null) {
            base = brooklynProperties.getConfig(BROOKLYN_DATA_DIR);
            if (base!=null)
                log.warn("Using deprecated "+BROOKLYN_DATA_DIR.getName()+": use "+MGMT_BASE_DIR.getName()+" instead; value: "+base);
        }
        if (base==null) base = brooklynProperties.getConfig(MGMT_BASE_DIR);
        return Os.tidyPath(base)+File.separator;
    }
    public static String getMgmtBaseDir(Map<String,?> brooklynProperties) {
        String base = (String) brooklynProperties.get(MGMT_BASE_DIR.getName());
        if (base==null) base = (String) brooklynProperties.get(BROOKLYN_DATA_DIR.getName());
        if (base==null) base = MGMT_BASE_DIR.getDefaultValue();
        return Os.tidyPath(base)+File.separator;
    }
    
    protected static String relativeToBase(StringConfigMap brooklynProperties, ConfigKey<String> key) {
        String d = brooklynProperties.getConfig(key);
        if (Strings.isBlank(d))
            throw new IllegalArgumentException(""+key+" must not be blank");
        if (!Os.isAbsolute(d)) d = Os.mergePaths(getMgmtBaseDir(brooklynProperties), d);
        return Os.tidyPath(d);
    }
    
    /** dir where persistence should be put according to configuration,
     * but note for object stores this may be treated specially */
    public static String getPersistenceDir(ManagementContext mgmt) {
        return getPersistenceDir(mgmt.getConfig());
    }
    /** see {@link #getPersistenceDir(ManagementContext)} */ 
    public static String getPersistenceDir(StringConfigMap brooklynProperties) {
        return relativeToBase(brooklynProperties, PERSISTENCE_DIR);
    }

    public static File getBrooklynWebTmpDir(ManagementContext mgmt) {
        String brooklynMgmtBaseDir = getMgmtBaseDir(mgmt);
        File webappTempDir = new File(Os.mergePaths(brooklynMgmtBaseDir, "planes", mgmt.getManagementPlaneId(), mgmt.getManagementNodeId(), "jetty"));
        try {
            FileUtils.forceMkdir(webappTempDir);
            Os.deleteOnExitRecursivelyAndEmptyParentsUpTo(webappTempDir, new File(brooklynMgmtBaseDir)); 
            return webappTempDir;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            IllegalStateException e2 = new IllegalStateException("Cannot create working directory "+webappTempDir+" for embedded jetty server: "+e, e);
            log.warn(e2.getMessage()+" (rethrowing)");
            throw e2;
        }
    }
    
}
