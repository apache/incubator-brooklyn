package org.overpaas.decorators;

import java.util.List;
import java.util.concurrent.Future;

public interface Resizable<T> {
    /**
     * A request to grow or shrink this entity to the desired size.
     * The desired size may not be possible, in which case this method will not
     * return an error but the returned list will contain at least one future
     * who throws an error
     * 
     * @param desiredSize
     * @return a list of handles to tasks started (possibly empty)
     */
	List<Future> resize(int desiredSize);
    
	List<Future> grow(int desiredIncrease);
    
	List<Future> shrink(int desiredDecrease);
    
}
