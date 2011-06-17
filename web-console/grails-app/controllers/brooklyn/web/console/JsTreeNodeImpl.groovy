package brooklyn.web.console

public class JsTreeNodeImpl implements JsTreeNode {

    private String id;
    private Map<String, String> data = [:]
    private List<JsTreeNode> children = []

    public JsTreeNodeImpl(String id, String name) {
        this.id = id;
        this.data.put("title", name)
    }

    public String getId() {
        return id
    }

    public String getState() {
        if (children.size() != 0) {
            return "open"
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

    public void addChild(JsTreeNode kiddy) {
        children.add(kiddy)
        kiddy.parent = this
    }

    public transient boolean hasDescendant(JsTreeNode other) {
        if (children.contains(other)) { return true }
        for(child in children) {
            if (child.hasDescendant(other)) {
                return true;
            }
        }
    }
}
