package brooklyn.web.console;

public enum MyEnumWithGetterMethod {
    A {
        public String getFoo() { return "a"; }
    },
    B {
        public String getFoo() { return "b"; }
    };

    public abstract String getFoo();
}
