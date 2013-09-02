package brooklyn.entity.dns.geoscaling;

import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.PROVIDE_CITY_INFO;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.dns.AbstractGeoDnsServiceImpl;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain;
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

public class GeoscalingDnsServiceImpl extends AbstractGeoDnsServiceImpl implements GeoscalingDnsService {

    private static final Logger log = LoggerFactory.getLogger(GeoscalingDnsServiceImpl.class);

    // Must remember any desired redirection targets if they're specified before configure() has been called.
    private Set<HostGeoInfo> rememberedTargetHosts;
    private final GeoscalingWebClient webClient = new GeoscalingWebClient();
    
    // These are available only after the configure() method has been invoked.
    private boolean randomizeSmartSubdomainName;
    private String username;
    private String password;
    private String primaryDomainName;
    private String smartSubdomainName;

    public GeoscalingDnsServiceImpl() {
    }

    @Override
    public void init() {
        super.init();
        
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
            if (smartSubdomainName != null) smartSubdomainName += "-";
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
        String result = getAttribute(MANAGED_DOMAIN);
        return (Strings.isBlank(result)) ? null : result;
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
            SmartSubdomain smartSubdomain = (primaryDomain != null) ? primaryDomain.getSmartSubdomain(smartSubdomainName) : null;
            if (smartSubdomain != null) {
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
            this.rememberedTargetHosts = MutableSet.copyOf(targetHosts);
            return;
        }
        
        webClient.login(username, password);
        Domain primaryDomain = webClient.getPrimaryDomain(primaryDomainName);
        if (primaryDomain==null) 
            throw new NullPointerException(this+" got null from web client for primary domain "+primaryDomainName);
        SmartSubdomain smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        
        if (smartSubdomain == null) {
            log.info("GeoScaling {} smart subdomain '{}.{}' does not exist, creating it now", new Object[] {this, smartSubdomainName, primaryDomainName});
            // TODO use WithMutexes to ensure this is single-entrant
            primaryDomain.createSmartSubdomain(smartSubdomainName);
            smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        }
        
        if (smartSubdomain != null) {
            log.debug("GeoScaling {} being reconfigured to use {}", this, targetHosts);
            String script = GeoscalingScriptGenerator.generateScriptString(targetHosts);
            smartSubdomain.configure(PROVIDE_CITY_INFO, script);
            if (targetHosts.isEmpty()) {
                setServiceState(Lifecycle.CREATED);
                setAttribute(ROOT_URL, null);
            } else {
                setServiceState(Lifecycle.RUNNING);
                String domain = getAttribute(MANAGED_DOMAIN);
                if (!Strings.isEmpty(domain)) setAttribute(ROOT_URL, "http://"+domain+"/");
            }
        } else {
            log.warn("Failed to retrieve or create GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+
                    "', aborting attempt to configure service");
            setServiceState(Lifecycle.ON_FIRE);
        }
        
        webClient.logout();
    }

}
