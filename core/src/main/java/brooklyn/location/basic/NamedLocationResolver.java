package brooklyn.location.basic;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationResolver;
import brooklyn.util.KeyValueParser;
import brooklyn.util.MutableMap;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Allows you to say, in your brooklyn.properties:
 * 
 * brooklyn.location.named.foo=localhost
 * brooklyn.location.named.foo.user=bob
 * brooklyn.location.named.foo.privateKeyFile=~/.ssh/custom-key-for-bob
 * brooklyn.location.named.foo.privateKeyPassphrase=WithAPassphrase
 * <p>
 * or
 * <p>
 * brooklyn.location.named.bob-aws-east=jclouds:aws-ec2:us-east-1
 * brooklyn.location.named.bob-aws-east.identity=BobId
 * brooklyn.location.named.bob-aws-east.credential=BobCred
 * <p>
 * then you can simply refer to:   named:foo   or   named:bob-aws-east   in any location spec.
 */
public class NamedLocationResolver implements RegistryLocationResolver {

    public static final Logger log = LoggerFactory.getLogger(NamedLocationResolver.class);

    public static final String NAMED = "named";
    
    @Override
    public Location newLocationFromString(Map properties, String spec) {
        throw new UnsupportedOperationException("This class must have the RegistryLocationResolver.newLocationFromString method invoked");
    }
    public Location newLocationFromString(String spec, LocationRegistry registry, Map locationFlags) {
        if (!spec.toLowerCase().startsWith(NAMED+":")) {
            throw new IllegalArgumentException("Invalid location '"+spec+"'; must specify something like "+NAMED+":myloc1");
        }
        spec = spec.substring( (NAMED+":").length() );
        String prefix = "brooklyn.location."+NAMED+"."+spec;
        String provider = (String) registry.getProperties().get(prefix);
        if (provider==null) {
            throw new IllegalArgumentException("Invalid named location '"+spec+"'; property "+prefix+" must be set to the target location string");
        }
        Map theseLocationFlags = findSubkeysWithPrefixRemoved(registry.getProperties(), prefix+".");
        Map newLocationFlags = new MutableMap().add(locationFlags).add(theseLocationFlags).add("name", spec);
        try {
            return registry.resolve(provider, newLocationFlags);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate named location "+spec+" pointing at "+provider+": "+e, e);
        }
    }
    
    private static Map findSubkeysWithPrefixRemoved(Map map, String prefix) {
        Map result = new LinkedHashMap();
        for (Object entry: map.entrySet()) {
            Object key = ((Map.Entry)entry).getKey();
            if ((key instanceof String) && ((String)key).startsWith(prefix)) {
                key = ((String)key).substring(prefix.length());
                Object val = ((Map.Entry)entry).getValue();
                result.put(key, val);
            }
        }
        return result;
    }

    @Override
    public String getPrefix() {
        return NAMED;
    }
}
