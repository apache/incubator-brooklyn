package brooklyn.entity.drivers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.util.MutableMap;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

import freemarker.template.TemplateException;

public class DownloadResolvers {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadResolvers.class);

    private DownloadResolvers() {}
    
    /**
     * Converts the basevalue by substituting things in the form ${key} or ${KEY} for values specific
     * to a given entity driver. The keys used are:
     * <ul>
     *   <li>driver: the driver instance (e.g. can do freemarker.org stuff like ${driver.osTag} to call {@code driver.getOsTag()})
     *   <li>entity: the entity instance
     *   <li>type: the fully qualified type name of the entity
     *   <li>simpletype: the unqualified type name of the entity
     *   <li>version: the version for this entity, or not included if null
     * </ul>
     * 
     * Additional substitution keys (and values) can be defined using additionalSubstitutions parameter; these
     * override the default substitutions listed above.
     */
    public static String substitute(EntityDriver driver, String basevalue, Function<? super EntityDriver, ? extends Map<String,String>> additionalSubs) {
        Entity entity = driver.getEntity();
        String type = entity.getEntityType().getName();
        String simpleType = type.substring(type.lastIndexOf(".")+1);
        String version = entity.getAttribute(Attributes.VERSION);
        if (version == null) version = entity.getConfig(ConfigKeys.SUGGESTED_VERSION);
        
        Map<String,?> subs = MutableMap.<String,Object>builder()
                .put("entity", entity)
                .put("driver", driver)
                .put("type", type)
                .put("simpletype", simpleType)
                .putIfNotNull("version", version)
                .putAll(additionalSubs.apply(driver))
                .build();
        
        try {
            return new Templater().processTemplate(basevalue, subs);
        } catch (TemplateException e) {
            throw new IllegalArgumentException("Failed to process driver download '"+basevalue+"' for driver of "+entity, e);
        }
    }

    public static Function<EntityDriver, String> attributeSubstituter(AttributeSensor<String> attribute) {
        return attributeSubstituter(attribute, Functions.constant(ImmutableMap.<String,String>of()));
    }
    
    public static Function<EntityDriver, String> attributeSubstituter(AttributeSensor<String> attribute, Function<? super EntityDriver, ? extends Map<String,String>> additionalSubs) {
        return new AttributeValueSubstituter(attribute, additionalSubs);
    }
    
    private static class AttributeValueSubstituter implements Function<EntityDriver, String> {
        private final AttributeSensor<String> attribute;
        private final Function<? super EntityDriver, ? extends Map<String,String>> additionalSubs;
        
        AttributeValueSubstituter(AttributeSensor<String> attribute, Function<? super EntityDriver, ? extends Map<String,String>> additionalSubs) {
            this.attribute = checkNotNull(attribute, "attribute");
            this.additionalSubs = checkNotNull(additionalSubs, "additionalSubs");
        }
        
        @Override
        public String apply(@Nullable EntityDriver input) {
            String pattern = input.getEntity().getAttribute(attribute);
            return (pattern == null) ? null : DownloadResolvers.substitute(input, pattern, additionalSubs);
        }
        
        @Override public boolean equals(@Nullable Object obj) {
            if (obj instanceof AttributeValueSubstituter) {
                AttributeValueSubstituter o = (AttributeValueSubstituter) obj;
                return Objects.equal(attribute, o.attribute) && Objects.equal(additionalSubs, o.additionalSubs);
            }
            return false;
        }

        @Override public int hashCode() {
            return Objects.hashCode(attribute, additionalSubs);
        }

        @Override public String toString() {
            return Objects.toStringHelper(this).add("attribute", attribute).add("additionalSubs", additionalSubs).toString();
        }
    }
}
