package brooklyn.util.internal

import groovy.lang.Closure

import java.util.Map

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
     * Starts the children of the host, optionally inserting additional properties and possibly modifying the child properties before starting.
     */   
    public static void startGroup(Map addlProperties=[:], Group host, Closure propertiesMods={}) {
        def childProperties = [:]
        if (host.hasProperty('properties')) childProperties << host.properties
        childProperties << addlProperties
        
        propertiesMods(childProperties)
        
        OverpaasDsl.run( (host.getChildren().collect { def item -> { -> if (item in Startable) item.start(childProperties) } }) as Closure[] )
        this
    }
    
    public static Entity startEntity(Map properties=[:], Entity entity, Group parent=null, Location location=null) {
        log.debug "factory creating entity {} with properties {} and location {}", entity, properties, location
		entity.properties << properties
        if (location) {
            if (entity.properties.location && entity.properties.location!=location) 
                throw new IllegalStateException("request to start $entity in $location but it is already set with location "+entity.location)
            entity.location = location
        }
        if (!parent && !entity.parents)
            throw new IllegalStateException("request to start $entity without any parents specified or set")
        //TODO dynamically look for appropriate start method, throw better exception if not there
        entity.startInLocation(parent, entity.location)
        entity
    }

    /**
     * Starts a clone of the given template entity running as a child of the host.
     */
    public static void startFromTemplate(Map childProperties=[:], Group host, Startable template, Map startupProperties) {
        createFromTemplate(childProperties, host, template).start(startupProperties)
    }

    /**
     * Creates a (not-started) clone of the given template, configured to be a child of the host
     */
    public static <T extends Entity> T createFromTemplate(Map childProperties=[:], Group host, T template) {
        assert !template.parents : "templates must not be assigned any parent entity"
        Entity c = cloneTemplate(template);
        c.id = LanguageUtils.newUid();
        c.properties << childProperties;
        host.addChild(c)
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
