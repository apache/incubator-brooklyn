package brooklyn.entity.proxy.nginx;

import java.util.Map;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.HasShortName;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableMap;

/**
 * An entity that represents an Nginx proxy (e.g. for routing requests to servers in a cluster).
 * <p>
 * The default driver *builds* nginx from source (because binaries are not reliably available, esp not with sticky sessions).
 * This requires gcc and other build tools installed. The code attempts to install them but inevitably 
 * this entity may be more finicky about the OS/image where it runs than others.
 * <p>
 * Paritcularly on OS X we require Xcode and command-line gcc installed and on the path.
 * <p>
 * See {@link http://library.linode.com/web-servers/nginx/configuration/basic} for useful info/examples
 * of configuring nginx.
 * <p>
 * https configuration is supported, with the certificates providable on a per-UrlMapping basis or a global basis.
 * (not supported to define in both places.) 
 * per-Url is useful if different certificates are used for different server names,
 * or different ports if that is supported.
 * see more info on Ssl in {@link ProxySslConfig}.
 */
@Catalog(name="nginx server", description="A single nginx server: an HTTP and reverse proxy server", iconUrl="classpath:///nginx-logo.jpeg")
@ImplementedBy(NginxControllerImpl.class)
public interface NginxController extends AbstractController, HasShortName {

    MethodEffector<Void> GET_CURRENT_CONFIGURATION = 
            new MethodEffector<Void>(NginxController.class, "getCurrentConfiguration");
    
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.3.7");

    @SetFromFlag("stickyVersion")
    ConfigKey<String> STICKY_VERSION =
        new BasicConfigKey<String>(String.class, "nginx.sticky.version", 
                "Version of ngnix-sticky-module to be installed, if required", "1.0");

    @SetFromFlag("pcreVersion")
    ConfigKey<String> PCRE_VERSION =
        new BasicConfigKey<String>(String.class, "pcre.version", "Version of PCRE to be installed, if required", "8.32");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://nginx.org/download/nginx-${version}.tar.gz");

    @SetFromFlag("downloadAddonUrls")
    BasicAttributeSensorAndConfigKey<Map<String,String>> DOWNLOAD_ADDON_URLS = new BasicAttributeSensorAndConfigKey<Map<String,String>>(
            SoftwareProcess.DOWNLOAD_ADDON_URLS, ImmutableMap.of(
                    "stickymodule", "http://nginx-sticky-module.googlecode.com/files/nginx-sticky-module-${addonversion}.tar.gz",
                    "pcre", "ftp://ftp.csx.cam.ac.uk/pub/software/programming/pcre/pcre-${addonversion}.tar.gz"));

    @SetFromFlag("sticky")
    BasicConfigKey<Boolean> STICKY =
        new BasicConfigKey<Boolean>(Boolean.class, "nginx.sticky", "whether to use sticky sessions", true);

    @SetFromFlag("httpPollPeriod")
    BasicConfigKey<Long> HTTP_POLL_PERIOD =
        new BasicConfigKey<Long>(Long.class, "nginx.sensorpoll.http", "poll period (in milliseconds)", 1000L);

    @SetFromFlag("withLdOpt")
    ConfigKey<String> WITH_LD_OPT = new BasicConfigKey<String>(
            String.class, "nginx.install.withLdOpt", "String to pass in with --with-ld-opt=\"<val>\" (and for OS X has pcre auto-appended to this)", "-L /usr/local/lib");

    @SetFromFlag("withCcOpt")
    ConfigKey<String> WITH_CC_OPT = new BasicConfigKey<String>(
            String.class, "nginx.install.withCcOpt", "String to pass in with --with-cc-opt=\"<val>\"", "-I /usr/local/include");

    boolean isSticky();

    @Effector(description="Gets the current server configuration (by brooklyn recalculating what the config should be); does not affect the server")
    String getCurrentConfiguration();

    String getConfigFile();

    boolean appendSslConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl,
            boolean sslBlock, boolean certificateBlock);
}
