package brooklyn.entity.proxy.nginx;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * This is a group whose members will be made available to a load-balancer / URL forwarding service (such as nginx).
 * Configuration requires a <b>domain</b> and some mechanism for finding members.
 * The easiest way to find members is using a <b>target</b> whose children will be tracked,
 * but alternative membership policies can also be used.
 */
@ImplementedBy(UrlMappingImpl.class)
public interface UrlMapping extends AbstractGroup {

    public static final MethodEffector<Void> DISCARD = new MethodEffector<Void>(UrlMapping.class, "discard");

    @SetFromFlag("label")
    public static final BasicConfigKey<String> LABEL =
        new BasicConfigKey<String>(String.class, "urlmapping.label", "optional human-readable label to identify a server");

    @SetFromFlag("domain")
    public static final BasicConfigKey<String> DOMAIN =
        new BasicConfigKey<String>(String.class, "urlmapping.domain", "domain (hostname, e.g. www.foo.com) to present for this URL map rule; required.");

    @SetFromFlag("path")
    public static final BasicConfigKey<String> PATH =
        new BasicConfigKey<String>(String.class, "urlmapping.path", 
                "URL path (pattern) for this URL map rule. Currently only supporting regex matches "+ 
                "(if not supplied, will match all paths at the indicated domain)");

    @SetFromFlag("ssl")
    public static final BasicConfigKey<ProxySslConfig> SSL_CONFIG = AbstractController.SSL_CONFIG;
            
    @SetFromFlag("rewrites")
    public static final BasicConfigKey<Collection<UrlRewriteRule>> REWRITES =
        new BasicConfigKey(Collection.class, "urlmapping.rewrites", "Set of URL rewrite rules to apply");

    @SetFromFlag("target")
    public static final BasicConfigKey<Entity> TARGET_PARENT =
        new BasicConfigKey<Entity>(Entity.class, "urlmapping.target.parent", "optional target entity whose children will be pointed at by this mapper");

    public static final BasicAttributeSensor<Collection<String>> TARGET_ADDRESSES =
        new BasicAttributeSensor(Collection.class, "urlmapping.target.addresses", "set of addresses which should be forwarded to by this URL mapping");
        
    public static final BasicAttributeSensor<String> ROOT_URL = WebAppService.ROOT_URL;    

    public String getUniqueLabel();

    /** adds a rewrite rule, must be called at config time.  see {@link UrlRewriteRule} for more info. */
    public UrlMapping addRewrite(String from, String to);
    
    /** adds a rewrite rule, must be called at config time.  see {@link UrlRewriteRule} for more info. */
    public UrlMapping addRewrite(UrlRewriteRule rule);
    
    public String getDomain();
    
    public String getPath();
    
    public Entity getTarget(); 
    
    public void setTarget(Entity target);

    public void recompute();
    
    @Effector(description="Unmanages the url-mapping, so it is discarded and no longer applies")
    public void discard();
}
