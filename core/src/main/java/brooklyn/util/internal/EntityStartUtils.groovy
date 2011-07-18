package brooklyn.util.internal

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.util.internal.LanguageUtils.FieldVisitor

import com.google.common.base.Preconditions

/**
 * Entity startup utilities.
 */
class EntityStartUtils {
    private static final Logger log = LoggerFactory.getLogger(EntityStartUtils.class);
    
    /**
     * Starts the children of the host.
     */   
    public static void startGroup(Group host, Collection<Location> locs = []) {
        host.ownedChildren.each { child -> startEntity(child, locs) }
    }

    public static Entity startEntity(Entity entity, Collection<Location> locs) {
        Preconditions.checkArgument(locs != null && !locs.isEmpty(), "Request to start $entity without a location")
        Preconditions.checkArgument(entity.owner != null, "Request to start $entity without any owner specified or set")
 
        log.debug "Starting entity {} in locations {}", entity, locs
        if (entity in Startable) entity.start(locs)
    }

    /**
     * Starts a clone of the given template entity running as a child of the host.
     */
    public static void startFromTemplate(Group host, Entity template, Collection<Location> locs) {
        startEntity(createFromTemplate(host, template), locs)
    }

    /**
     * Creates a (not-started) clone of the given template, configured to be owned by the given entity
     */
    public static <T extends Entity> T createFromTemplate(Group owner, T template) {
        Preconditions.checkArgument(template.owner == null, "Templates must not be assigned any owner (but is in "+template.owner+")")
        Preconditions.checkArgument(template.groups == null || template.groups.isEmpty(), "Templates must not be a member of any group entity (but is in "+template.groups+")")

        Entity copy = cloneTemplate(template);
        copy.id = LanguageUtils.newUid()
        owner.addOwnedChild(copy)
        copy
    }
    
    /**
     * Creates a clone of the given template.
     * 
     * With one change - all fields called <em>entity</em> in any object contained anywhere in the given
     * template's hierarchy which had been equal to the template are set equal to the clone.
     */
    public static <T extends Entity> T cloneTemplate(T template) {
        Entity result = LanguageUtils.clone template
        LanguageUtils.visitFields(result, { parent, name, value -> 
            if (name=="entity" && template.equals(value)) parent."$name" = result } as FieldVisitor, [ result ] as Set)
        result
    }

    /**
     * Stop an entity if it is still running.
     */
    public static <E extends Entity & Startable> void stopEntity(E entity) {
        if (entity != null && entity.getAttribute(Startable.SERVICE_UP)) {
            log.warn "Entity {} still running", entity
            try {
                entity.stop()
            } catch (Exception e) {
                log.warn "Error caught trying to shut down {}: {}", entity.id, e.message
            }
        }
    }
}
