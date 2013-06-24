package brooklyn.location.basic;

import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;

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
 * then you can simply refer to:   foo   or   named:foo   (or bob-aws-east or named:bob-aws-east)   in any location spec
 */
public class NamedLocationResolver implements LocationResolver {

    public static final Logger log = LoggerFactory.getLogger(NamedLocationResolver.class);

    public static final String NAMED = "named";
    
    @SuppressWarnings("rawtypes")
    @Override
    public Location newLocationFromString(Map properties, String spec) {
        throw new UnsupportedOperationException("Use RegistryLocationResolver.newLocationFromString (registry required as an argument)");
    }
    @SuppressWarnings({ "rawtypes" })
    public Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry) {
        String name = spec;
        if (spec.toLowerCase().startsWith(NAMED+":")) {
            name = spec.substring( (NAMED+":").length() );
        }
        LocationDefinition ld = registry.getDefinedLocationByName(name);
        if (ld==null) throw new NoSuchElementException("No named location defined matching '"+name+"'");
        return ((BasicLocationRegistry)registry).resolveLocationDefinition(ld, locationFlags, name);
    }

    @Override
    public String getPrefix() {
        return NAMED;
    }
    
    /** accepts anything starting  named:xxx  or  xxx where xxx is a defined location name */
    @Override
    public boolean accepts(String spec, LocationRegistry registry) {
        if (BasicLocationRegistry.isResolverPrefixForSpec(this, spec, false)) return true;
        if (registry.getDefinedLocationByName(spec)!=null) return true;
        return false;
    }

}
