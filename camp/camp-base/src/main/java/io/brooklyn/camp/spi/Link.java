package io.brooklyn.camp.spi;

public class Link<T> {

    private final String id;
    private final String name;
    
    public Link(String id, String name) {
        super();
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
}
