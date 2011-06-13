package org.overpaas.util

import org.overpaas.entities.Entity
import org.overpaas.entities.Group

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class EntityNavigationUtils {
    static final Logger log = LoggerFactory.getLogger(EntityNavigationUtils.class)
 
	public static void dump(Entity e, String prefix="") {
		log.debug prefix+e
		if (e in Group) {
			e.children.each { dump it, prefix+"  " }
		}
	} 
	
}
