package brooklyn.location.basic;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jclouds.Constants;
import org.jclouds.providers.ProviderMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.LocationResolver;
import brooklyn.location.basic.jclouds.CredentialsFromEnv;
import brooklyn.location.basic.jclouds.JcloudsLocation;
import brooklyn.location.basic.jclouds.JcloudsLocationFactory;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class JcloudsResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(JcloudsResolver.class);
    
    public static final String JCLOUDS = "jclouds";
    
    public static final Collection<String> PROVIDERS_LOADER = Lists.newArrayList(Iterables.transform(org.jclouds.providers.Providers.all(), new Function<ProviderMetadata,String>() {
        @Override
        public String apply(ProviderMetadata input) { return input.getId(); }
    }));
    public static final Collection<String> PROVIDERS_REST = Lists.newArrayList(org.jclouds.rest.Providers.getSupportedProviders());
    
    public static final Collection<String> AWS_REGIONS = Arrays.asList(
            // from http://docs.amazonwebservices.com/general/latest/gr/rande.html as of Apr 2012.
            // it is suggested not to maintain this list here, instead to require aws-ec2 explicitly named.
            "eu-west-1","us-east-1","us-west-1","us-west-2","ap-southeast-1","ap-northeast-1","sa-east-1");
         
    public static JcloudsLocation resolve(String spec) {
        return (JcloudsLocation) new LocationRegistry().resolve(JCLOUDS+":"+spec);
    }
    
    public JcloudsLocation newLocationFromString(String spec) {
        return newLocationFromString(new LinkedHashMap(), spec);
    }
    
    @Override
    public JcloudsLocation newLocationFromString(Map properties, String spec) {
        String provider=spec, region=null;
        int split = spec.indexOf(':');
        if (split<0) {
            if (spec.equalsIgnoreCase(getPrefix()))
                throw new IllegalArgumentException("Cannot use '"+spec+"' as a location ID; it is insufficient. "+
                       "Try jclouds:aws-ec2 (for example).");
        } else {
            provider = spec.substring(0, split);
            region = spec.substring(split+1);
            while (provider.equalsIgnoreCase(JCLOUDS)) {
                //strip any number of jclouds: prefixes, for use by static "resolve" method
                provider = region;
                region = null;
                split = provider.indexOf(':');
                if (split>=0) {
                    region = provider.substring(split+1);
                    provider = provider.substring(0, split);
                }
            }
        }
        
        if (region==null && AWS_REGIONS.contains(provider)) {
            // treat amazon as a default
            region = provider;
            provider = "aws-ec2";
            log.warn("Use of deprecated location '"+region+"'; in future refer to with explicit provider '"+provider+":"+region+"'");
        }
        
        if (!PROVIDERS_REST.contains(provider)) {
            log.warn("Unknown jclouds provider '"+provider+"' (will throw); known providers are: "+PROVIDERS_REST);
            throw new NoSuchElementException("Unknown location '"+spec+"'");
        }

        Map jcloudsProperties = new MutableMap(new CredentialsFromEnv(BrooklynProperties.Factory.newDefault().addFromMap(properties), provider).asMap());
        
        // TODO Adding properties here, so user can programmatically pass things through to JcloudsLocation for provisioning
        jcloudsProperties.putAll(properties);
        
        if (PROVIDERS_LOADER.contains(provider)) {
            // providers from ServiceLoader take a location (endpoint already configured)
            return new JcloudsLocationFactory(jcloudsProperties).newLocation(region);
        } else {
            // other "providers" are APIs (jclouds 1.4 way of detecting, there's an Apis class is 1.5)
            // and so take an _endpoint_ (but not a location)
            jcloudsProperties.put(Constants.PROPERTY_ENDPOINT, region);
            return new JcloudsLocationFactory(jcloudsProperties).newLocation(null);          
        }
    }
    
    @Override
    public String getPrefix() {
        return JCLOUDS;
    }
    
}
