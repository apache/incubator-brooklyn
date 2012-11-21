package brooklyn.catalog.internal;

import java.io.InputStream;
import java.io.InputStreamReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;

public class CatalogDtoUtils {

    private static final Logger log = LoggerFactory.getLogger(CatalogDtoUtils.class);
    
    public static CatalogDto newDefaultLocalScanningDto(CatalogScanningModes scanMode) {
        CatalogDo result = new CatalogDo(
                CatalogDto.newNamedInstance("Local Scanned Catalog", "All annotated Brooklyn entities detected in the default classpath") );
        result.setClasspathScanForEntities(scanMode);
        return result.dto;
    }

    /** throws if there are any problems in retrieving or copying */
    public static void populateFromUrl(CatalogDto dto, String url) {
        CatalogDto remoteDto = newDtoFromUrl(url);
        try {
            copyDto(remoteDto, dto, true);
        } catch (Exception e) {
            Exceptions.propagate(e);
        }
    }

    /** does a shallow copy.
     * "skipNulls" means not to copy any fields from the source which are null */ 
    static void copyDto(CatalogDto source, CatalogDto target, boolean skipNulls) throws IllegalArgumentException, IllegalAccessException {
        target.copyFrom(source, skipNulls);
    }

    public static CatalogDto newDtoFromUrl(String url) {
        log.debug("Retrieving catalog from: "+url);
        try {
            InputStream source = new ResourceUtils(null).getResourceFromUrl(url);
            return (CatalogDto) new CatalogXmlSerializer().deserialize(new InputStreamReader(source));
        } catch (Throwable t) {
            log.debug("Unable to retrieve catalog from: "+url+" ("+t+")");
            throw Exceptions.propagate(t);
        } finally {
            log.debug("Retrieved catalog from: "+url);
        }
    }

}
