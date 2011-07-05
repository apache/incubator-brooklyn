package brooklyn.web.console.entity

import brooklyn.entity.Entity

public class JsTreeNodeImpl implements JsTreeNode {
    private String id
    private Map<String, String> data = [:]
    private Map<String, String> metadata = [:]
    private List<JsTreeNode> children = []
    public transient boolean matched

    public JsTreeNodeImpl(Entity e, Boolean matched=false) {
        this(e.id, e.displayName, (e.entityClass?.name) ?: "[no entity class]", matched)
    }

    public JsTreeNodeImpl(String id, String name, String clazz, Boolean matched) {
        this.data.put("title", name)
        
        // Set html attributes on the a elements jstree uses to display nodes
        this.data.put("attr", ["title": id,
                               "id": "jstree-node-id-" + id])

        // Here you can store anything you like.
        // The data is then available with jQuery's .data() mechanism.
        this.metadata.put("id", id)

        this.matched = matched
    }

    public String getId() {
        return id
    }

    public String getState() {
        if (children) {
            return matched ? "open" : "closed"
        } else {
            return "leaf"
        }
    }

    public Map<String, String> getData() {
        return data
    }

    public Map<String, String> getMetadata() {
        return metadata
    }

    public List<JsTreeNode> getChildren() {
        return children
    }
}
