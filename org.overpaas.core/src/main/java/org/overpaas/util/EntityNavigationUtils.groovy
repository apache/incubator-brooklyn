package org.overpaas.util

import org.overpaas.entities.Entity
import org.overpaas.entities.Group

public class EntityNavigationUtils {
	public static void dump(Entity e, String prefix="") {
		println prefix+e
		if (e in Group) {
			e.getChildren().each { dump it, prefix+"  " }
		}
	} 
	
}
