package brooklyn.config;

import static brooklyn.entity.basic.ConfigKeys.newStringConfigKey;

import java.io.File;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.ConfigKeys;
import brooklyn.management.ManagementContext;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.os.Os;

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

    public static final String DEFAULT_PERSISTENCE_CONTAINER_NAME = "brooklyn-persisted-state";
    /** on file system, the 'data' subdir is used so that there is an obvious place to put backup dirs */ 
    public static final String DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM = Os.mergePaths(DEFAULT_PERSISTENCE_CONTAINER_NAME, "data");
    
    /** provided for setting; consumers should query the management context persistence subsystem for the actual target,
     * or use {@link #resolvePersistencePath(String, StringConfigMap, String)} if trying to resolve the value */
    public static final ConfigKey<String> PERSISTENCE_DIR = newStringConfigKey(
        "brooklyn.persistence.dir", 
        "Directory or container name for writing brooklyn persisted state");

    public static final ConfigKey<String> PERSISTENCE_LOCATION_SPEC = newStringConfigKey(
        "brooklyn.persistence.location.spec", 
        "Optional location spec string for an object store (e.g. jclouds:swift:URL) where persisted state should be kept;"
        + "if blank or not supplied, the file system is used"); 

    public static final ConfigKey<Boolean> PERSISTENCE_BACKUPS_REQUIRED =
        ConfigKeys.newBooleanConfigKey("brooklyn.persistence.backups.required",
            "Whether a backup should always be made of the persistence directory; "
            + "if true, it will fail if this operation is not permitted (e.g. jclouds-based cloud object stores); "
            + "if false, the persistence store will be overwritten with changes (but files not removed if they are unreadable); "
            + "if null or not set, the legacy beahviour of creating backups where possible (e.g. file system) is currently used, "
            + "but this may be changed in future versions");

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
    
    protected static String resolveAgainstBaseDir(StringConfigMap brooklynProperties, String path) {
        if (!Os.isAbsolute(path)) path = Os.mergePaths(getMgmtBaseDir(brooklynProperties), path);
        return Os.tidyPath(path);
    }
    
    /** @deprecated since 0.7.0 use {@link #resolvePersistencePath(String, StringConfigMap, String)} */
    public static String getPersistenceDir(ManagementContext mgmt) {
        return getPersistenceDir(mgmt.getConfig());
    }
    /** @deprecated since 0.7.0 use {@link #resolvePersistencePath(String, StringConfigMap, String)} */ 
    public static String getPersistenceDir(StringConfigMap brooklynProperties) {
        return resolvePersistencePath(null, brooklynProperties, null);
    }
    
    /** container name or full path for where persist state should be kept
     * @param optionalSuppliedValue  a value which has been supplied explicitly, optionally
     * @param brooklynProperties  the properties map where the persistence path should be looked up
     *   if not supplied, along with finding the brooklyn.base.dir if needed (using file system persistence with a relative path)
     * @param optionalObjectStoreLocationSpec  if a location spec is supplied, this will return a container name
     *    suitable for use with the given object store based on brooklyn.persistence.dir;
     *    if null this method will return a full file system path, relative 
     *    to the brooklyn.base.dir if the configured brooklyn.persistence.dir is not absolute
     */
    public static String resolvePersistencePath(String optionalSuppliedValue, StringConfigMap brooklynProperties, String optionalObjectStoreLocationSpec) {
        String path = optionalSuppliedValue;
        if (path==null) path = brooklynProperties.getConfig(PERSISTENCE_DIR);
        if (optionalObjectStoreLocationSpec==null) {
            // file system
            if (path==null) path=DEFAULT_PERSISTENCE_DIR_FOR_FILESYSTEM;
            return resolveAgainstBaseDir(brooklynProperties, path);
        } else {
            // obj store
            if (path==null) path=DEFAULT_PERSISTENCE_CONTAINER_NAME;
            return path;
        }
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
