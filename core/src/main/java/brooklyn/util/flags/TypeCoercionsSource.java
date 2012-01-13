package brooklyn.util.flags;

/** Allows a class to define a static class, perhaps even in another bundle, 
 * which may declare static fromXxx methods from which type coercions can be taken */
public @interface TypeCoercionsSource {
    String value();
}
