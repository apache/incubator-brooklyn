package brooklyn.catalog.internal;

import java.lang.reflect.Field;
import java.util.List;

public class CatalogDto {
    
    String id;
    String url;
    String name;
    String description;
    CatalogClasspathDto classpath;
    List<CatalogItemDtoAbstract<?>> entries = null;
    
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

    /**
     * @throws NullPointerException If source is null (and !skipNulls)
     */
    void copyFrom(CatalogDto source, boolean skipNulls) throws IllegalAccessException {
        if (source==null) {
            if (skipNulls) return;
            throw new NullPointerException("source DTO is null, when copying to "+this);
        }
        
        if (!skipNulls || source.id != null) id = source.id;
        if (!skipNulls || source.url != null) url = source.url;
        if (!skipNulls || source.name != null) name = source.name;
        if (!skipNulls || source.description != null) description = source.description;
        if (!skipNulls || source.classpath != null) classpath = source.classpath;
        if (!skipNulls || source.entries != null) entries = source.entries;
    }

}
