package brooklyn.entity.dns.geoscaling

import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.*
import static com.google.common.base.Preconditions.*

import java.util.Map
import java.util.Set

import brooklyn.config.render.RendererHints
import brooklyn.entity.Entity
import brooklyn.entity.basic.Lifecycle
import brooklyn.entity.dns.AbstractGeoDnsService
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.geo.HostGeoInfo
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.text.Identifiers;


class GeoscalingDnsService extends AbstractGeoDnsService {
    
    @SetFromFlag("randomizeSubdomainName")
    public static final BasicConfigKey RANDOMIZE_SUBDOMAIN_NAME = [ Boolean, "randomize.subdomain.name" ];
    @SetFromFlag("username")
    public static final BasicConfigKey GEOSCALING_USERNAME = [ String, "geoscaling.username" ];
    @SetFromFlag("password")
    public static final BasicConfigKey GEOSCALING_PASSWORD = [ String, "geoscaling.password" ];
    @SetFromFlag("primaryDomainName")
    public static final BasicConfigKey GEOSCALING_PRIMARY_DOMAIN_NAME = [ String, "geoscaling.primary.domain.name" ];
    @SetFromFlag("smartSubdomainName")
    public static final BasicConfigKey GEOSCALING_SMART_SUBDOMAIN_NAME = [ String, "geoscaling.smart.subdomain.name" ];
    
    public static final BasicAttributeSensor GEOSCALING_ACCOUNT =
        [ String, "geoscaling.account", "Active user account for the GeoScaling.com service" ];
    public static final BasicAttributeSensor<String> MANAGED_DOMAIN =
        ([ String, "geoscaling.managed.domain", "Fully qualified domain name that will be geo-redirected" ] as BasicAttributeSensor<String>).with {
        RendererHints.register(it, new RendererHints.NamedActionWithUrl("Open", { "http://"+it+"/" }));
    };

    // Must remember any desired redirection targets if they're specified before configure() has been called.
    private Set<HostGeoInfo> rememberedTargetHosts;
    private final GeoscalingWebClient webClient = [ ];
    
    // These are available only after the configure() method has been invoked.
    private boolean randomizeSmartSubdomainName;
    private String username;
    private String password;
    private String primaryDomainName;
    private String smartSubdomainName;
    
    public GeoscalingDnsService(Map properties = [:], Entity owner = null) {
        super(properties, owner);
        
        // defaulting to randomized subdomains makes deploying multiple applications easier
        if (getConfig(RANDOMIZE_SUBDOMAIN_NAME)==null) setConfig(RANDOMIZE_SUBDOMAIN_NAME, true); 
    }
    
    // Ensure our configure() method gets called; may be able to remove this if/when the framework detects this
    // and invokes the configure() method automatically?
    @Override
    public void onManagementBecomingMaster() {
        applyConfig();
        super.onManagementBecomingMaster();
    }

	boolean isConfigured = false;
	
    public synchronized void applyConfig() {		
        randomizeSmartSubdomainName = getConfig(RANDOMIZE_SUBDOMAIN_NAME);
        username = getConfig(GEOSCALING_USERNAME);
        password = getConfig(GEOSCALING_PASSWORD);
        primaryDomainName = getConfig(GEOSCALING_PRIMARY_DOMAIN_NAME);
        smartSubdomainName = getConfig(GEOSCALING_SMART_SUBDOMAIN_NAME);

        // Ensure all mandatory configuration is provided.
        checkNotNull(username, "The GeoScaling username is not specified");
        checkNotNull(password, "The GeoScaling password is not specified");
        checkNotNull(primaryDomainName, "The GeoScaling primary domain name is not specified");
        
        if (randomizeSmartSubdomainName) {
            // if no smart subdomain specified, but random is, use something random
            if (smartSubdomainName) smartSubdomainName += "-";
            else smartSubdomainName = "";
            smartSubdomainName += Identifiers.makeRandomId(8);
        }
        checkNotNull(smartSubdomainName, "The GeoScaling smart subdomain name is not specified or randomized");
        
        String fullDomain = smartSubdomainName+"."+primaryDomainName;
        log.info("GeoScaling service will configure redirection for '"+fullDomain+"' domain");
        setAttribute(GEOSCALING_ACCOUNT, username);
        setAttribute(MANAGED_DOMAIN, fullDomain);
        setAttribute(HOSTNAME, getHostname());
        
        isConfigured = true;
        
        if (rememberedTargetHosts != null) {
            reconfigureService(rememberedTargetHosts);
            rememberedTargetHosts = null;
        }
    }
    
    @Override
    public String getHostname() {
        return getAttribute(MANAGED_DOMAIN)?:null;
    }
    
    /** minimum/default TTL here is 300s = 5m */
    public long getTimeToLiveSeconds() { return 5*60; }
    
    @Override
    public void destroy() {
        setServiceState(Lifecycle.STOPPING);
        if (!isConfigured) return;
        
        // Don't leave randomized subdomains configured on our GeoScaling account.
        if (randomizeSmartSubdomainName) {
            webClient.login(username, password);
            Domain primaryDomain = webClient.getPrimaryDomain(primaryDomainName);
            SmartSubdomain smartSubdomain = primaryDomain?.getSmartSubdomain(smartSubdomainName);
            if (smartSubdomain) {
                log.info("Deleting randomized GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+"'");
                smartSubdomain.delete();
            }
            webClient.logout();
        }
        
        super.destroy();
		
		isConfigured = false;
    }
    
    protected void reconfigureService(Collection<HostGeoInfo> targetHosts) {
        if (!isConfigured) {
            this.rememberedTargetHosts = targetHosts;
            return;
        }
        
        webClient.login(username, password);
        Domain primaryDomain = webClient.getPrimaryDomain(primaryDomainName);
        if (primaryDomain==null) 
            throw new NullPointerException("$this got null from web client for primary domain $primaryDomainName")
        SmartSubdomain smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        
        if (!smartSubdomain) {
            log.info("GeoScaling $this smart subdomain '"+smartSubdomainName+"."+primaryDomainName+"' does not exist, creating it now");
            // TODO use WithMutexes to ensure this is single-entrant
            primaryDomain.createSmartSubdomain(smartSubdomainName);
            smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        }
        
        if (smartSubdomain) {
            log.debug("GeoScaling $this being reconfigured to use $targetHosts");
            String script = GeoscalingScriptGenerator.generateScriptString(targetHosts);
            smartSubdomain.configure(PROVIDE_CITY_INFO, script);
            setServiceState(targetHosts.isEmpty() ? Lifecycle.CREATED : Lifecycle.RUNNING);
        } else {
            log.warn("Failed to retrieve or create GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+
                    "', aborting attempt to configure service");
            setServiceState(Lifecycle.ON_FIRE);
        }
        
        webClient.logout();
    }

}
