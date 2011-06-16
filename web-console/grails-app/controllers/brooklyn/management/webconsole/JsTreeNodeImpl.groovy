package brooklyn.management.webconsole

public class JsTreeNodeImpl implements JsTreeNode {

    private String id;
    private Map<String, String> data = [:];
    private List<JsTreeNode> children;

    public JsTreeNodeImpl(String id, String name, List<JsTreeNodeImpl> children) {
        this.id = id;
        this.children = children
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
}
