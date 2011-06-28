package brooklyn.web.console.entity


import brooklyn.entity.Entity


public class JsTreeNodeImpl implements JsTreeNode {

    private String id;
    private Map<String, String> data = [:]
    private List<JsTreeNode> children = []
    public transient boolean matched;

    public JsTreeNodeImpl(Entity e, Boolean matched=false) {
        this(e.id, e.displayName, e.entityClass?.name ?: "$e [no entity class]", matched)
    }

    public JsTreeNodeImpl(String id, String name, String clazz, Boolean matched) {
        this.id = id;
        this.data.put("title", name)
        this.data.put("type", clazz)
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

    public List<JsTreeNode> getChildren() {
        return children;
    }
}
