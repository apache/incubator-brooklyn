package brooklyn.management.entitlement;

import com.google.common.reflect.TypeToken;


public class BasicEntitlementClassDefinition<T> implements EntitlementClass<T> {

    private final String identifier;
    private final TypeToken<T> argumentType;

    public BasicEntitlementClassDefinition(String identifier, TypeToken<T> argumentType) {
        this.identifier = identifier;
        this.argumentType = argumentType;
    }
    
    public BasicEntitlementClassDefinition(String identifier, Class<T> argumentType) {
        this.identifier = identifier;
        this.argumentType = TypeToken.of(argumentType);
    }
    
    @Override
    public String entitlementClassIdentifier() {
        return identifier;
    }

    @Override
    public TypeToken<T> aentitlementClassArgumentType() {
        return argumentType;
    }

}
