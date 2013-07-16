package brooklyn.location.jclouds;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jclouds.apis.ApiMetadata;
import org.jclouds.apis.Apis;
import org.jclouds.providers.ProviderMetadata;
import org.jclouds.providers.Providers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

@SuppressWarnings("rawtypes")
public class JcloudsResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(JcloudsResolver.class);
    
    public static final String JCLOUDS = "jclouds";
    
    public static final Map<String,ProviderMetadata> PROVIDERS = getProvidersMap();
    public static final Map<String,ApiMetadata> APIS = getApisMap();
    
    private static Map<String,ProviderMetadata> getProvidersMap() {
        Map<String,ProviderMetadata> result = Maps.newLinkedHashMap();
        for (ProviderMetadata p: Providers.all()) {
            result.put(p.getId(), p);
        }
        return ImmutableMap.copyOf(result);
    }

    private static Map<String,ApiMetadata> getApisMap() {
        Map<String,ApiMetadata> result = Maps.newLinkedHashMap();
        for (ApiMetadata api: Apis.all()) {
            result.put(api.getId(), api);
        }
        return ImmutableMap.copyOf(result);
    }

    public static final Collection<String> AWS_REGIONS = Arrays.asList(
            // from http://docs.amazonwebservices.com/general/latest/gr/rande.html as of Apr 2012.
            // it is suggested not to maintain this list here, instead to require aws-ec2 explicitly named.
            "eu-west-1","us-east-1","us-west-1","us-west-2","ap-southeast-1","ap-northeast-1","sa-east-1");
         
    /** @deprecated since 0.5; use {@link #resolveWithDefaultProperties(String)} */
    public static JcloudsLocation resolve(String spec) {
        return resolveWithDefaultProperties(spec);
    }
    
    /** @deprecated since 0.6; use {@code managementContext.getLocationRegistry().resolve(spec)} */
    public static JcloudsLocation resolveWithDefaultProperties(String spec) {
        BrooklynProperties properties = BrooklynProperties.Factory.newDefault();
        return resolveWithProperties(spec, properties);
    }
    
    /** @deprecated since 0.6; use {@code managementContext.getLocationRegistry().resolve(spec)} */
    public static JcloudsLocation resolveWithProperties(String spec, Map properties) {
        return (JcloudsLocation) new JcloudsResolver().newLocationFromString(properties, spec);
    }

    private ManagementContext managementContext;

    @Override
    public void init(ManagementContext managementContext) {
        this.managementContext = checkNotNull(managementContext, "managementContext");
    }
    
    /** @deprecated since 0.6; use {@link #newLocationFromString(String, LocationRegistry, Map, Map)} */
    public JcloudsLocation newLocationFromString(String spec) {
        return newLocationFromString(new LinkedHashMap(), spec);
    }
    
    @Override
    public JcloudsLocation newLocationFromString(Map properties, String spec) {
        return newLocationFromString(spec, null, properties, new MutableMap());
    }
    
    @Override
    public JcloudsLocation newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
    }

    protected static class JcloudsSpecParser {
        String providerOrApi;
        String parameter;
        
        public static JcloudsSpecParser parse(String spec, boolean dryrun) {
            JcloudsSpecParser result = new JcloudsSpecParser();
            int split = spec.indexOf(':');
            if (split<0) {
                if (spec.equalsIgnoreCase(JCLOUDS)) {
                    if (dryrun) return null;
                    throw new IllegalArgumentException("Cannot use '"+spec+"' as a location ID; it is insufficient. "+
                           "Try jclouds:aws-ec2 (for example).");
                }
                result.providerOrApi = spec;
                result.parameter = null;
            } else {
                result.providerOrApi = spec.substring(0, split);
                result.parameter = spec.substring(split+1);
                int numJcloudsPrefixes = 0;
                while (result.providerOrApi.equalsIgnoreCase(JCLOUDS)) {
                    //strip any number of jclouds: prefixes, for use by static "resolve" method
                    numJcloudsPrefixes++;
                    result.providerOrApi = result.parameter;
                    result.parameter = null;
                    split = result.providerOrApi.indexOf(':');
                    if (split>=0) {
                        result.parameter = result.providerOrApi.substring(split+1);
                        result.providerOrApi = result.providerOrApi.substring(0, split);
                    }
                }
                if (!dryrun && numJcloudsPrefixes > 1) {
                    log.warn("Use of deprecated location spec '"+spec+"'; in future use a single \"jclouds\" prefix");
                }
            }
            
            if (result.parameter==null && AWS_REGIONS.contains(result.providerOrApi)) {
                // treat amazon as a default
                result.parameter = result.providerOrApi;
                result.providerOrApi = "aws-ec2";
                if (!dryrun)
                    log.warn("Use of deprecated location '"+result.parameter+"'; in future refer to with explicit provider '"+result.providerOrApi+":"+result.parameter+"'");
            }
            
            return result;
        }

        public boolean isProvider() {
            return PROVIDERS.containsKey(providerOrApi);
        }

        public boolean isApi() {
            return APIS.containsKey(providerOrApi);
        }
        
        public String getProviderOrApi() {
            return providerOrApi;
        }
        
        public String getParameter() {
            return parameter;
        }
    }
    
    @SuppressWarnings("unchecked")
    protected JcloudsLocation newLocationFromString(String spec, brooklyn.location.LocationRegistry registry, Map properties, Map locationFlags) {
        JcloudsSpecParser details = JcloudsSpecParser.parse(spec, false);
        String locationName = (String) locationFlags.get("name");

        boolean isProvider = details.isProvider();
        String providerOrApi = details.providerOrApi;
        String regionName = details.parameter;
        
        if (Strings.isEmpty(providerOrApi)) {
            throw new IllegalArgumentException("Cloud provider/API type not specified in spec \""+spec+"\"");
        }
        if (!isProvider && !details.isApi()) {
            throw new NoSuchElementException("Cloud provider/API type "+providerOrApi+" is not supported by jclouds");
        }
        
        // For everything in brooklyn.properties, only use things with correct prefix (and remove that prefix).
        // But for everything passed in via locationFlags, pass those as-is.
        // TODO Should revisit the locationFlags: where are these actually used? Reason accepting properties without
        //      full prefix is that the map's context is explicitly this location, rather than being generic properties.
        Map allProperties = getAllProperties(registry, properties);
        Map jcloudsProperties = JcloudsPropertiesFromBrooklynProperties.getJcloudsProperties(providerOrApi, regionName, locationName, allProperties);
        jcloudsProperties.putAll(locationFlags);
        
        if (isProvider) {
            // providers from ServiceLoader take a location (endpoint already configured)
            jcloudsProperties.put(JcloudsLocationConfig.CLOUD_REGION_ID.getName(), regionName);
        } else {
            // other "providers" are APIs so take an _endpoint_ (but not a location)
        }
        
        return managementContext.getLocationManager().createLocation(LocationSpec.spec(JcloudsLocation.class)
                .configure(jcloudsProperties));
    }

    private Map getAllProperties(brooklyn.location.LocationRegistry registry, Map<?,?> properties) {
        Map<Object,Object> allProperties = Maps.newHashMap();
        if (registry!=null) allProperties.putAll(registry.getProperties());
        allProperties.putAll(properties);
        return allProperties;
    }
    
    @Override
    public String getPrefix() {
        return JCLOUDS;
    }
    
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        if (BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true)) return true;
        JcloudsSpecParser details = JcloudsSpecParser.parse(spec, true);
        if (details==null) return false;
        if (details.isProvider() || details.isApi()) return true;
        return false;
    }

}
