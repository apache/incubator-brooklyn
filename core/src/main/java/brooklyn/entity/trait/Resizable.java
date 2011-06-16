package brooklyn.entity.trait;

import brooklyn.util.task.Task;

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
	Task resize(int desiredSize);
    
	Task grow(int desiredIncrease);
    
	Task shrink(int desiredDecrease);
}
