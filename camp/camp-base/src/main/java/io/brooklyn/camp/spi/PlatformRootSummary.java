package io.brooklyn.camp.spi;

/** Holds the metadata (name, description, etc) for a CampPlatform.
 * Required to initialize a CampPlatform.
 * <p>
 * See {@link AbstractResource} for more general information.
 */
public class PlatformRootSummary extends AbstractResource {

    public static final String CAMP_TYPE = "Platform";
    
    /** Use {@link #builder()} to create */
    protected PlatformRootSummary() {
    }
    
    // no fields beyond basic resource
    
    //TODO:
    
    // in the DTO
    
//    "supportedFormatsUri": URI, 
//    "extensionsUri": URI,
//    "typeDefinitionsUri": URI,
//    "tags": [ String, + ], ?
//    "specificationVersion": String[], 
//    "implementationVersion": String, ? 
//    "assemblyTemplates": [ Link + ], ? 
//    "assemblies": [ Link + ], ? 
//    "platformComponentTemplates": [ Link + ], ? 
//    "platformComponentCapabilities": [Link + ], ? 
//    "platformComponents": [ Link + ], ?

    
    // builder
    
    public static Builder<? extends PlatformRootSummary> builder() {
        return new Builder<PlatformRootSummary>(CAMP_TYPE);
    }
    
    public static class Builder<T extends PlatformRootSummary> extends AbstractResource.Builder<T,Builder<T>> {
        
        protected Builder(String type) { super(type); }
        
        @SuppressWarnings("unchecked")
        protected T createResource() { return (T) new PlatformRootSummary(); }
        
        protected void initialize() {
            super.initialize();
            // TODO a better way not to have an ID here (new subclass BasicIdentifiableResource for other BasicResource instances)
            id("");
        }
    }

}
