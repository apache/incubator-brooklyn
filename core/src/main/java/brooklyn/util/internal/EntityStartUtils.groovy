package brooklyn.util.internal

import java.lang.reflect.Field

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.location.Location
import brooklyn.util.internal.LanguageUtils.FieldVisitor

import com.google.common.base.Preconditions


/**
 * Entity startup utilities.
 */
class EntityStartUtils {
    private static final Logger log = LoggerFactory.getLogger(EntityStartUtils.class);
    
    public static void startEntity(Entity entity, Collection<Location> locations = []) {
        entity.start(locations)
    }

    /**
     * Stop an entity if it is still running.
     */
    public static void stopEntity(Entity entity) {
        entity.stop()
    }

    /**
     * Starts a clone of the given template entity running as a child of the host.
     */
    public static void startFromTemplate(Entity owner, Entity template, Collection<Location> locations) {
        startEntity(createFromTemplate(owner, template), locations)
    }

    /**
     * Creates a (not-started) clone of the given template, configured to be owned by the given entity
     */
    public static <T extends Entity> T createFromTemplate(Entity owner, T template) {
        Preconditions.checkArgument(template.owner == null, "Templates must not be assigned any owner (but is in "+template.owner+")")
        Preconditions.checkArgument(template.groups == null || template.groups.isEmpty(), "Templates must not be a member of any group entity (but is in "+template.groups+")")

        //pick a different ID
        Entity copy = cloneTemplate(template);
        Field f = AbstractEntity.class.getDeclaredFields().find { it.name == "id" }
        f.setAccessible(true);
        f.set(copy, LanguageUtils.newUid());
        
        owner.addOwnedChild(copy)
        copy
    }
    
    /**
     * Creates a clone of the given template.
     * 
     * With one change - all fields called <em>entity</em> in any object contained anywhere in the given
     * template's hierarchy which had been equal to the template are set equal to the clone.
     */
    //FIXME doesn't work for non-trivial groovy classes -- there is no metaclass
    public static <T extends Entity> T cloneTemplate(T template) {
        Entity result = LanguageUtils.clone template
        LanguageUtils.visitFields(result,
	            { parent, name, value -> if (name=="entity" && template.equals(value)) parent."$name" = result } as FieldVisitor,
	            [ result ] as Set)
        result
    }
}
