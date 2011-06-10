package org.overpaas.core.decorators;

import java.util.Collection;


public interface GroupEntity extends OverpaasEntity {
	Collection<OverpaasEntity> getChildren();
	public OverpaasEntity addChild(OverpaasEntity child);
	public boolean removeChild(OverpaasEntity child);
}
