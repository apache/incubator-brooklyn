package brooklyn.location.basic;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.NoSuchElementException;

import org.jclouds.rest.Providers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.Location;
import brooklyn.location.LocationResolver;
import brooklyn.location.basic.jclouds.CredentialsFromEnv;
import brooklyn.location.basic.jclouds.JcloudsLocationFactory;

import com.google.common.collect.Lists;

public class JcloudsResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(JcloudsResolver.class);
    
    public static final String JCLOUDS = "jclouds";
    
    public static final Collection<String> PROVIDERS = Lists.newArrayList(Providers.getSupportedProviders());
    public static final Collection<String> AWS_REGIONS = Arrays.asList(
            // from http://docs.amazonwebservices.com/general/latest/gr/rande.html as of Apr 2012.
            // it is suggested not to maintain this list here, instead to require aws-ec2 explicitly named.
            "eu-west-1","us-east-1","us-west-1","us-west-2","ap-southeast-1","ap-northeast-1","sa-east-1");
            
    @Override
    public Location newLocationFromString(Map properties, String spec) {
        String provider=spec, region=null;
        int split = spec.indexOf(':');
        if (split<0) {
            if (spec.equalsIgnoreCase(getPrefix()))
                throw new IllegalArgumentException("Cannot use '"+spec+"' as a location ID; it is insufficient. "+
                       "Try jclouds:aws-ec2 (for example).");
        } else {
            provider = spec.substring(0, split);
            region = spec.substring(split+1);
            if (provider.equalsIgnoreCase(JCLOUDS)) {
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
        
        if (!PROVIDERS.contains(provider)) 
            throw new NoSuchElementException("Unknown location '"+spec+"'"); 
        
        return new JcloudsLocationFactory(
                new CredentialsFromEnv(BrooklynProperties.Factory.newEmpty().addFromMap(properties), provider).asMap()).
                newLocation(region);
    }
    
    @Override
    public String getPrefix() {
        return JCLOUDS;
    }
    
}
