package org.overpaas.core.types.common


import java.lang.reflect.Field;
import java.util.Map;

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.decorators.Location;
import org.overpaas.core.decorators.OverpaasEntity;
import org.overpaas.core.decorators.Startable;
import org.overpaas.util.OverpaasDsl;
import org.overpaas.util.LanguageUtils;
import org.overpaas.util.LanguageUtils.FieldVisitor;

class EntityStartUtils {

	/** starts the children of the host, optionally inserting add'l Properties and possibly modifying the child Properties before starting */ 	
	public static void startGroup(Map addlProperties=[:], GroupEntity host, Closure propertiesMods={}) {
		def childProperties = [:]
		if (host.hasProperty('properties')) childProperties << host.properties
		childProperties << addlProperties
		
		propertiesMods(childProperties)
		
		OverpaasDsl.run( (host.getChildren().collect { def item -> { -> if (item in Startable) item.start(childProperties) } }) as Closure[] )
		this
	}
	
	public static OverpaasEntity startEntity(Map properties=[:], OverpaasEntity entity, GroupEntity parent=null, Location location=null) {
		println "factory $this creating entity with $properties and $location"
		if (location) {
			if (entity.location && entity.location!=location) 
			throw new IllegalStateException("request to start $entity in $location but it is already set with location "+entity.location)
			entity.location = location
		}
		if (!parent && !entity.parents)
			throw new IllegalStateException("request to start $entity without any parents specified or set")
		//TODO dynamically look for appropriate start method, throw better exception if not there
		entity.startInLocation(parent, entity.location)
		entity
	}


	/** starts a clone of the given template entity running as a child of the host */
	public static void startFromTemplate(Map childProperties=[:], GroupEntity host, Startable template, Map startupProperties) {
		createFromTemplate(childProperties, host, template).start(startupProperties)
	}
	/** creates a (not-started) clone of the given template, configured to be a child of the host */
	public static <T extends OverpaasEntity> T createFromTemplate(Map childProperties=[:], GroupEntity host, T template) {
		assert !template.parents : "templates must not be assigned any parent entity"
		OverpaasEntity c = cloneTemplate(template);
		c.id = LanguageUtils.newUid();
		c.properties << childProperties;
		host.addChild(c)
		c
	}
	
	/** creates a clone of the given template, with one change: 
	 * all fields called 'entity' in any object contained anywhere in the given
	 * template's hierarchy which had been equal to the template
	 * are set equal to the clone
	 * @param template
	 * @return
	 */
	public static <T extends OverpaasEntity> T cloneTemplate(T template) {
		OverpaasEntity result = LanguageUtils.clone template
		LanguageUtils.visitFields(result, { parent, name, value -> 
			if (name=="entity" && template.equals(value)) parent."$name" = result } as FieldVisitor, [ result ] as Set)
		result
	}
}
