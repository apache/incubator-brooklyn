package brooklyn.web.console

public class JsTreeNodeImpl implements JsTreeNode {

    private String id;
    private Map<String, String> data = [:]
    private List<JsTreeNode> children = []
    public transient boolean matched;

    public JsTreeNodeImpl(String id, String name, boolean matched=false) {
        this.id = id;
        this.data.put("title", name)
        this.matched = matched
    }

    public String getId() {
        return id
    }

    public String getState() {
        if (children.size() != 0) {
            return matched ? "open" : "closed"
        } else {
            return "leaf"
        }
    }

    public Map<String, String> getData() {
        return data
    }

    public List<JsTreeNode> getChildren() {
        return children;
    }

    public transient boolean hasDescendant(JsTreeNode other) {
        if (children.contains(other)) { return true }
        for(child in children) {
            if (child.hasDescendant(other)) {
                return true;
            }
        }
        return false
    }
}
