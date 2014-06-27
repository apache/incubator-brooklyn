package brooklyn.management.entitlement;


public class NotEntitledException extends RuntimeException {

    private static final long serialVersionUID = -4001882260980589181L;
    
    EntitlementContext entitlementContext;
    EntitlementClass<?> permission;
    Object typeArgument;
    
    public <T> NotEntitledException(EntitlementContext entitlementContext, EntitlementClass<T> permission, T typeArgument) {
        this.entitlementContext = entitlementContext;
        this.permission = permission;
        this.typeArgument = typeArgument;
    }

}
