package brooklyn.web.console.entity


public interface JsTreeNode extends Serializable {
    String getId();
    String getState();
    Map<String, String> getData();
    Map<String, String> getMetadata();
    List<JsTreeNode> getChildren();
}
