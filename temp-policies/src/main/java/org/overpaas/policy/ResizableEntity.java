package org.overpaas.policy;

/**
 * An entity that can be grown or shrunk. 
 * 
 * Examples include:
 * <ul>
 *   <li> a cluster of tomcat instances could instances added or removed; 
 *   <li> in Monterey we can add/remove mediator instances; 
 *   <li> a storage area network (SAN) could have its disk size increased/decreased.
 * </ul>
 * 
 * @author aled
 */
public interface ResizableEntity extends Entity { // extends Entity, Resizable {

    /**
     * A request to grow or shrink this entity to the desired size. The desired size may not be 
     * possible, in which case the returned expected size will not match the desired size.
     * 
     * @param desiredSize
     * @return The size that this entity is now expected to become.
     */
    int resize(int desiredSize);
    
    int getCurrentSize();
}
