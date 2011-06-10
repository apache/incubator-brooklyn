package org.overpaas.console

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.decorators.OverpaasEntity;

public class EntityNavigationUtils {

	public static void dump(OverpaasEntity e, String prefix="") {
		println prefix+e
		if (e in GroupEntity) {
			((GroupEntity)e).getChildren().each { dump it, prefix+"  " }
		}
	} 
	
}
