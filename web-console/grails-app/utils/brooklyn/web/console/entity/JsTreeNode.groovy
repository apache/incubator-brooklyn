package brooklyn.web.console.entity

import brooklyn.entity.Entity

public class JsTreeNode {
    private String id
    private Map<String, String> data = [:]
    private Map<String, String> metadata = [:]
    private List<JsTreeNode> children = []
    public transient boolean matched

    public JsTreeNode(Entity e) {
        this.data.put("title", e.displayName)

        // Set html attributes on the a elements jstree uses to display nodes
        this.data.put("attr", ["title": e.id,
                               "id": "jstree-node-id-" + e.id])

        // Here you can store anything you like.
        // The data is then available with jQuery's .data() mechanism.
        this.metadata.put("id", e.id)
    }

    public String getId() {
        return id
    }

    public String getState() {
        if (children) {
            return "open"
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
