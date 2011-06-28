package brooklyn.util.internal

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.util.internal.LanguageUtils.FieldVisitor

/**
 * Entity startup utilities.
 * 
 * @author alex
 */
class EntityStartUtils {
	
	private static final Logger log = LoggerFactory.getLogger(EntityStartUtils.class);
	
    /**
     * Starts the children of the host.
     */   
	public static void startGroup(Group host, Collection<Location> locs = []) {
		Set tasks = []
		host.getOwnedChildren().each { child -> tasks.add(host.getExecutionContext().submit { if (child in Startable) child.start(locs) }) }
		tasks.collect { it.get() }
	}

    public static Entity startEntity(Entity entity, Collection<Location> locs) {
        if (!locs || locs.isEmpty())
            throw new IllegalStateException("request to start $entity without a location")
        if (!entity.owner)
            throw new IllegalStateException("request to start $entity without any owner specified or set")
        log.debug "factory creating entity {} with properties {} and location {}", entity, entity.attributes, entity.location

        //TODO dynamically look for appropriate start method, throw better exception if not there
        entity.startInLocation(locs)
        entity
    }

    /**
     * Starts a clone of the given template entity running as a child of the host.
     */
    public static void startFromTemplate(Group host, Entity template, Collection<Location> locs) {
        createFromTemplate(host, template).start(locs)
    }

    /**
     * Creates a (not-started) clone of the given template, configured to be owned by the given entity
     */
    public static <T extends Entity> T createFromTemplate(Group owner, T template) {
        assert !template.owner : "templates must not be assigned any owner (but is in "+template.owner+")"
        assert !template.groups : "templates must not be a member of any group entity (but is in "+template.groups+")"
        Entity c = cloneTemplate(template);
        c.id = LanguageUtils.newUid();
        owner.addOwnedChild(c)
        c
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
}
