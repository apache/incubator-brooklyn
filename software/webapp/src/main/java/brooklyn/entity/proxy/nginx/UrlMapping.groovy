package brooklyn.entity.proxy.nginx;

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.Attributes
import brooklyn.entity.trait.Changeable
import brooklyn.entity.trait.Startable
import brooklyn.entity.webapp.WebAppService
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.management.SubscriptionHandle
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions
import com.google.common.base.Predicate

/**
 * This is a group whose members will be made available to a load-balancer / URL forwarding service (such as nginx).
 * Configuration requires a <b>domain</b> and some mechanism for finding members.
 * The easiest way to find members is using a <b>target</b> whose children will be tracked,
 * but alternative membership policies can also be used.
 */
public class UrlMapping extends AbstractGroup {

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

    @SetFromFlag("rewrites")
    public static final BasicConfigKey<Collection<UrlRewriteRule>> REWRITES =
        new BasicConfigKey<Collection<UrlRewriteRule>>(Collection.class, "urlmapping.rewrites", "Set of URL rewrite rules to apply");

    @SetFromFlag("target")
    public static final BasicConfigKey<Entity> TARGET_PARENT =
        new BasicConfigKey<String>(Entity.class, "urlmapping.target.parent", "optional target entity whose children will be pointed at by this mapper");

    public static final BasicAttributeSensor<Collection<String>> TARGET_ADDRESSES =
        new BasicAttributeSensor<Collection<String>>(Collection.class, "urlmapping.target.addresses", "set of addresses which should be forwarded to by this URL mapping");
        
    public static final BasicAttributeSensor<String> ROOT_URL = WebAppService.ROOT_URL;    

    public UrlMapping(Map<?,?> props, Entity owner) {
        super(props, owner);
        recompute();
        // following line could be more efficient (just modify the addresses set, not clearing it each time; 
        // but since addresses is lazy loaded not that big a dreal)
//        subscribe(this, Changeable.GROUP_SIZE, { resetAddresses(true) } as SensorEventListener);
        // above not needed since our target tracking figures this out
    }

    public String getUniqueLabel() {
        String l = getConfig(LABEL);
        if (l) return id+"-"+l
        else return id;
    }

    /** adds a rewrite rule, must be called at config time.  see {@link UrlRewriteRule} for more info. */
    public synchronized UrlMapping addRewrite(String from, String to) {
        addRewrite(new UrlRewriteRule(from, to));
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

    /** defines how address string, ie  hostname:port, is constructed from a given entity.
     * returns null if not possible.
     * <p>
     * the default is to look at HOSTNAME and HTTP_PORT attribute sensors. this method is suitable (intended) for overriding.
     */
    protected String getAddressOfEntity(Entity s) {
        def h = s.getAttribute(Attributes.HOSTNAME);
        def p = s.getAttribute(Attributes.HTTP_PORT);
        if (h && p) return ""+h+":"+p;
        log.error("Unable to construct hostname:port representation for "+s+"; skipping in "+this);
        return null;
    }
    
    protected synchronized void recomputeAddresses() {
        Set<String> resultM = []
        for (Entity s: members) {
            String hp = getAddressOfEntity(s);
            if (hp) resultM += hp;
        }
        def result = Collections.unmodifiableCollection(resultM);
        def oldAddresses = getAttribute(TARGET_ADDRESSES);
        if (!result.equals(oldAddresses)) {
            setAttribute(TARGET_ADDRESSES, result);
        }
    }
    
    SubscriptionHandle subscriptionHandle;
    public synchronized void recompute() {
        if (subscriptionHandle) subscriptionContext.unsubscribe(subscriptionHandle);
        def t = target;
        if (t) {
            subscriptionHandle = subscribeToChildren(t, Startable.SERVICE_UP, {
                SensorEvent event ->
                def m1 = getMembers();
                if (event.value) addMember(event.source);
                else removeMember(event.source);
                if (m1!=getMembers()) {
                    recomputeAddresses();
                }
            } as SensorEventListener);
            setMembers(t.getOwnedChildren(), { Entity ee -> ee.getAttribute(Startable.SERVICE_UP) } as Predicate);
        }
        
        recomputeAddresses();
    }
    
}
