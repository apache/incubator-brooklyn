package brooklyn.catalog.internal;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;

public class CatalogClasspathDto {
    
    /** whether/what to scan; defaults to 'none' */
    CatalogScanningModes scan;
    private List<String> entries;
    
    public synchronized void addEntry(String url) {
        if (entries==null)
            entries = new CopyOnWriteArrayList<String>();
        
        entries.add(url);
    }

    public synchronized List<String> getEntries() {
        return entries;
    }
    
}
