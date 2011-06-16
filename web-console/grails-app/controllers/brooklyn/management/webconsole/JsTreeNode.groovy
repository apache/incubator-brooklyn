package brooklyn.management.webconsole

public interface JsTreeNode extends Serializable {
    String getId();
    String getState();
    Map<String, String> getData();
    List<JsTreeNode> getChildren();
}
