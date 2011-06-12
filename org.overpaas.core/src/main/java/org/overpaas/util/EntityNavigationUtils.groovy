package org.overpaas.util

import groovy.util.logging.Slf4j;

import org.overpaas.entities.Entity
import org.overpaas.entities.Group

@Slf4j
public class EntityNavigationUtils {
	public static void dump(Entity e, String prefix="") {
		log.debug prefix+e
		if (e in Group) {
			e.children.each { dump it, prefix+"  " }
		}
	} 
	
}
