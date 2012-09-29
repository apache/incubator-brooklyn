package brooklyn.mementos;

import java.util.List;

/**
 * A simple tree structure, where a node references a parent and children using their ids.
 * 
 * @author aled
 */
public interface TreeNode {

    /**
     * The id of this node in the tree. This id will be used by the parent's getChildren(), 
     * and by each child's getParent().
     */
    String getId();
    
    /**
     * The id of the parent entity, or null if none.
     */
    String getParent();
    
    /**
     * The ids of the children.
     */
    List<String> getChildren();
}
