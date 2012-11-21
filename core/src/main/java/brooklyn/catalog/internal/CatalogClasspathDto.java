package brooklyn.catalog.internal;

import java.util.ArrayList;
import java.util.List;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;

public class CatalogClasspathDto {
    
    /** whether/what to scan; defaults to 'none' */
    CatalogScanningModes scan;
    List<String> entries;
    
    public synchronized void addEntry(String url) {
        if (entries==null)
            entries = new ArrayList<String>();
        
        entries.add(url);
    }
    
}
