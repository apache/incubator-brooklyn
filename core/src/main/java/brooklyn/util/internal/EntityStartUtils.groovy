package brooklyn.util.internal

import groovy.lang.Closure

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Startable
import brooklyn.util.internal.LanguageUtils.FieldVisitor

/**
 * Entity startup utilities.
 * 
 * @author alex
 */
class EntityStartUtils {
	
	private static final Logger log = LoggerFactory.getLogger(EntityStartUtils.class);
	
    /**
     * Starts the children of the host, optionally inserting additional attributes and possibly modifying the child attributes before starting.
     */   
    public static void startGroup(Map addlAttributes=[:], Group host, Closure attributesMods={}) {
        def childAttributes = [:]
        childAttributes << host.attributes
        childAttributes << addlAttributes
        
        attributesMods(childAttributes)
        
		Set tasks = []
		host.getChildren().each { child -> tasks.add(host.getExecutionContext().submit { if (child in Startable) child.start(childAttributes) }) }
		tasks.collect { it.get() }
    }
    
    public static Entity startEntity(Map startAttributes=[:], Entity entity) {

		entity.attributes << startAttributes
        if (!entity.location)
            throw new IllegalStateException("request to start $entity without a location")
        if (!entity.owner)
            throw new IllegalStateException("request to start $entity without any owner specified or set")
        log.debug "factory creating entity {} with properties {} and location {}", entity, entity.attributes, entity.location

        //TODO dynamically look for appropriate start method, throw better exception if not there
        entity.startInLocation(entity.location)
        entity
    }

    /**
     * Starts a clone of the given template entity running as a child of the host.
     */
    public static void startFromTemplate(Map childAttributes=[:], Group host, Entity template, Map startupAttributes) {
        createFromTemplate(childAttributes, host, template).start(startupAttributes)
    }

    /**
     * Creates a (not-started) clone of the given template, configured to be owned by the given entity
     */
    public static <T extends Entity> T createFromTemplate(Map childAttributes=[:], Group owner, T template) {
        assert !template.owner : "templates must not be assigned any owner (but is in "+template.owner+")"
        assert !template.groups : "templates must not be a member of any group entity (but is in "+template.groups+")"
        Entity c = cloneTemplate(template);
        c.id = LanguageUtils.newUid();
        c.attributes << childAttributes;
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
