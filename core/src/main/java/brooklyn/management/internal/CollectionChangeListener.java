package brooklyn.management.internal;
public interface CollectionChangeListener<Item> {
    void onItemAdded(Item item);
    void onItemRemoved(Item item);
}
