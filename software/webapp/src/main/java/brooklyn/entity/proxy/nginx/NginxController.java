package brooklyn.entity.proxy.nginx;

import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

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
@ImplementedBy(NginxControllerImpl.class)
public interface NginxController extends AbstractController {

    public static final MethodEffector<Void> GET_CURRENT_CONFIGURATION = 
            new MethodEffector<Void>(NginxController.class, "getCurrentConfiguration");
    
    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
        new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "1.3.7");

    @SetFromFlag("sticky")
    public static final BasicConfigKey<Boolean> STICKY =
        new BasicConfigKey<Boolean>(Boolean.class, "nginx.sticky", "whether to use sticky sessions", true);

    @SetFromFlag("httpPollPeriod")
    public static final BasicConfigKey<Long> HTTP_POLL_PERIOD =
        new BasicConfigKey<Long>(Long.class, "nginx.sensorpoll.http", "poll period (in milliseconds)", 1000L);

    public boolean isSticky();
    
    @Description("Gets the current server configuration (by brooklyn recalculating what the config should be); does not affect the server")
    public String getCurrentConfiguration();
    
    public String getConfigFile();
    
    public boolean appendSslConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl,
            boolean sslBlock, boolean certificateBlock);
}
