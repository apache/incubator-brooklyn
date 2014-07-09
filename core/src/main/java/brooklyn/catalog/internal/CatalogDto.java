package brooklyn.catalog.internal;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.PropagatedRuntimeException;

import com.google.common.base.Objects;

public class CatalogDto {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogDto.class);

    String id;
    String url;
    String name;
    String description;
    CatalogClasspathDto classpath;
    List<CatalogItemDtoAbstract<?,?>> entries = null;
    
    // for thread-safety, any dynamic additions to this should be handled by a method 
    // in this class which does copy-on-write
    List<CatalogDto> catalogs = null;

    public static CatalogDto newDefaultLocalScanningDto(CatalogClasspathDo.CatalogScanningModes scanMode) {
        CatalogDo result = new CatalogDo(
                CatalogDto.newNamedInstance("Local Scanned Catalog", "All annotated Brooklyn entities detected in the default classpath") );
        result.setClasspathScanForEntities(scanMode);
        return result.dto;
    }

    public static CatalogDto newDtoFromUrl(String url) {
        if (LOG.isDebugEnabled()) LOG.debug("Retrieving catalog from: {}", url);
        try {
            InputStream source = ResourceUtils.create().getResourceFromUrl(url);
            CatalogDto result = (CatalogDto) new CatalogXmlSerializer().deserialize(new InputStreamReader(source));
            if (LOG.isDebugEnabled()) LOG.debug("Retrieved catalog from: {}", url);
            return result;
        } catch (Throwable t) {
            Exceptions.propagateIfFatal(t);
            throw new PropagatedRuntimeException("Unable to retrieve catalog from " + url + ": " + t, t);
        }
    }

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

    /**
     * Populates this Dto by loading the catalog at its {@link #url}. Takes no action if url is null.
     * Throws if there are any problems in retrieving or copying from url.
     */
    void populateFromUrl() {
        if (url != null) {
            CatalogDto remoteDto = newDtoFromUrl(url);
            try {
                copyFrom(remoteDto, true);
            } catch (Exception e) {
                Exceptions.propagate(e);
            }
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .omitNullValues()
                .add("name", name)
                .add("id", id)
                .add("url", url)
                .toString();
    }

}
