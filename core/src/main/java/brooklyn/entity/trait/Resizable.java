package brooklyn.entity.trait;

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
	List<Future<?>> //Task 
		resize(int desiredSize);
    
	List<Future<?>>  //Task 
		grow(int desiredIncrease);
    
	List<Future<?>>  //Task 
		shrink(int desiredDecrease);
}
