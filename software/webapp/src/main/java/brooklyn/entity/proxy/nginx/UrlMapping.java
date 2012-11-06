package brooklyn.entity.proxy.nginx;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.entity.trait.Changeable;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.management.SubscriptionHandle;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

/**
 * This is a group whose members will be made available to a load-balancer / URL forwarding service (such as nginx).
 * Configuration requires a <b>domain</b> and some mechanism for finding members.
 * The easiest way to find members is using a <b>target</b> whose children will be tracked,
 * but alternative membership policies can also be used.
 */
public class UrlMapping extends AbstractGroup {

    private static final Logger log = LoggerFactory.getLogger(UrlMapping.class);
    
    public static final Effector<Void> DISCARD = new MethodEffector<Void>(UrlMapping.class, "discard");

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

    public UrlMapping(Map<?,?> props, Entity owner) {
        super(props, owner);
    }

    public String getUniqueLabel() {
        String l = getConfig(LABEL);
        if (groovyTruth(l)) return getId()+"-"+l;
        else return getId();
    }

    /** adds a rewrite rule, must be called at config time.  see {@link UrlRewriteRule} for more info. */
    public synchronized UrlMapping addRewrite(String from, String to) {
        return addRewrite(new UrlRewriteRule(from, to));
    }
    /** adds a rewrite rule, must be called at config time.  see {@link UrlRewriteRule} for more info. */
    public synchronized UrlMapping addRewrite(UrlRewriteRule rule) {
        Collection<UrlRewriteRule> rewrites = getConfig(REWRITES);
        if (rewrites==null) {
            rewrites = new ArrayList<UrlRewriteRule>();
        }
        rewrites.add(rule);
        setConfig(REWRITES, rewrites);
        return this;
    }
    
    public String getDomain() {
        return Preconditions.checkNotNull( getConfig(DOMAIN), "domain config argument required");
    }
    
    public String getPath() {
        return getConfig(PATH);
    }
    
    public Entity getTarget() { 
        return getConfig(TARGET_PARENT); 
    }
    public void setTarget(Entity target) {
        setConfig(TARGET_PARENT, target);
        recompute();
    }

    @Override
    public void onManagementStarting() {
        if (getConfig(TARGET_PARENT) != null) {
            recompute();
            // following line could be more efficient (just modify the addresses set, not clearing it each time; 
            // but since addresses is lazy loaded not that big a deal)
            // subscribe(this, Changeable.GROUP_SIZE, { resetAddresses(true) } as SensorEventListener);
            // above not needed since our target tracking figures this out
        }
    }
    
    /** defines how address string, ie  hostname:port, is constructed from a given entity.
     * returns null if not possible.
     * <p>
     * the default is to look at HOSTNAME and HTTPS_PORT or HTTP_PORT attribute sensors (depending on SSL_CONFIG being set with targetIsSsl).
     * <p> 
     * this method is suitable (intended) for overriding if needed.
     */
    protected String getAddressOfEntity(Entity s) {
        String h = s.getAttribute(Attributes.HOSTNAME);
        
        Integer p = null;
        List<String> protos = s.getAttribute(WebAppServiceConstants.ENABLED_PROTOCOLS);
        ProxySslConfig sslConfig = getConfig(SSL_CONFIG);
        if (sslConfig != null && sslConfig.getTargetIsSsl()) {
            // use ssl
            if (protos != null && hasProtocol(protos, "https")) {
                // proto configured correctly
            } else {
                // proto not defined; use https anyway, but it might fail
                log.warn("Misconfiguration for "+this+": ENABLED_PROTOCOLS='"+protos+"' for "+s+" but sslConfig="+sslConfig);
            }
            p = s.getAttribute(Attributes.HTTPS_PORT);
            if (p == null)
                log.warn("Misconfiguration for "+this+": sslConfig="+sslConfig+" but no HTTPS_PORT on "+s);
        }
        if (p == null) {
            // default to http
            p = s.getAttribute(Attributes.HTTP_PORT);
        }
        
        if (groovyTruth(h) && p != null) return h+":"+p;
        log.error("Unable to construct hostname:port representation for "+s+"; skipping in "+this);
        return null;
    }
    
    protected synchronized void recomputeAddresses() {
        Set<String> resultM = Sets.newLinkedHashSet();
        for (Entity s: getMembers()) {
            String hp = getAddressOfEntity(s);
            if (hp != null) resultM.add(hp);
        }
        Set<String> result = Collections.unmodifiableSet(resultM);
        Collection<String> oldAddresses = getAttribute(TARGET_ADDRESSES);
        if (oldAddresses == null || !result.equals(ImmutableSet.copyOf(oldAddresses))) {
            setAttribute(TARGET_ADDRESSES, result);
        }
    }
    
    // FIXME Do we really need this?!
    public SubscriptionHandle getSubscriptionHandle() {
        return subscriptionHandle;
    }
    
    private SubscriptionHandle subscriptionHandle;
    private SubscriptionHandle subscriptionHandle2;
    public synchronized void recompute() {
        if (subscriptionHandle != null) getSubscriptionContext().unsubscribe(subscriptionHandle);
        if (subscriptionHandle2 != null) getSubscriptionContext().unsubscribe(subscriptionHandle2);
        
        Entity t = getTarget();
        if (t != null) {
            subscriptionHandle = subscribeToChildren(t, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
                @Override public void onEvent(SensorEvent<Boolean> event) {
                    boolean changed = (event.getValue()) ? addMember(event.getSource()) : removeMember(event.getSource());
                    if (changed) {
                        recomputeAddresses();
                    }
                }});
            subscriptionHandle2 = subscribe(t, Changeable.MEMBER_REMOVED, new SensorEventListener<Entity>() {
                @Override public void onEvent(SensorEvent<Entity> event) {
                    boolean changed = removeMember(event.getValue());
                    if (changed) {
                        recomputeAddresses();
                    }
                }});
            setMembers(t.getOwnedChildren(), EntityPredicates.attributeEqualTo(Startable.SERVICE_UP, true));
        }
        
        recomputeAddresses();
    }
    
    @Description("Unmanages the url-mapping, so it is discarded and no longer applies")
    public void discard() {
        Entities.unmanage(this);
    }
    
    private boolean hasProtocol(List<String> protocols, String desired) {
        for (String contender : protocols) {
            if ("https".equals(contender.toLowerCase())) return true;
        }
        return false;
    }
}
