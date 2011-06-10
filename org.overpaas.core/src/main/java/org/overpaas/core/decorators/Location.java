package org.overpaas.core.decorators;

import java.io.Serializable;
import java.util.Collection;

public interface Location extends Serializable {

	public interface SingleLocationEntity {
		Location getLocation();
	}
	public interface MultiLocationEntityGroup {
		Collection<Location> getLocations();
	}
}
