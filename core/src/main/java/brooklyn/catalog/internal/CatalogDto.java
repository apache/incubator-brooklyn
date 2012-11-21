package brooklyn.catalog.internal;

import java.lang.reflect.Field;
import java.util.List;

public class CatalogDto {
    
    String id;
    String url;
    String name;
    String description;
    CatalogClasspathDto classpath;
    List<AbstractCatalogItem<?>> entries = null;
    
    // for thread-safety, any dynamic additions to this should be handled by a method 
    // in this class which does copy-on-write
    List<CatalogDto> catalogs = null;

    public static CatalogDto newNamedInstance(String name, String description) {
        CatalogDto result = new CatalogDto();
        result.name = name;
        result.description = description;
        return result;
    }

    public static CatalogDto newLinkedInstance(String url) {
        CatalogDto result = new CatalogDto();
        result.url = url;
        return result;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()+"["+
                (url!=null ? url+"; " : "")+
                (name!=null ? name+":" : "")+
                (id!=null ? id : "")+
                "]";
    }

    void copyFrom(CatalogDto source, boolean skipNulls) throws IllegalArgumentException, IllegalAccessException {
        if (source==null) {
            if (skipNulls) return;
            throw new NullPointerException("source DTO is null, when copying to "+this);
        }
        for (Field f: getClass().getFields()) {
            Object value = f.get(source);
            if (!skipNulls || value!=null)
                f.set(this, value);
        }
    }

}
