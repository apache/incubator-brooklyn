package brooklyn.entity.rebind.persister;

import java.io.File;
import java.io.IOException;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.io.Files;

/**
 * Reads/writes to a file. This impl does it immediately, with no synchronisation.
 * Callers should wrap in {@link StoreObjectAccessorLocking} if multiple threads may be accessing this.
 *
 * @author aled
 */
public class FileBasedStoreObjectAccessor implements PersistenceObjectStore.StoreObjectAccessor {

    /**
     * @param file
     * @param executor A sequential executor (e.g. SingleThreadedExecutor, or equivalent)
     */
    public FileBasedStoreObjectAccessor(File file, String tmpExtension) {
        this.file = file;
        this.tmpFile = new File(file.getParentFile(), file.getName()+(Strings.isBlank(tmpExtension) ? ".tmp" : tmpExtension));
    }

    private final File file;
    private final File tmpFile;
    
    @Override
    public String get() {
        try {
            if (!exists()) return null;
            return Files.asCharSource(file, Charsets.UTF_8).read();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public boolean exists() {
        return file.exists();
    }

    @Override
    public void put(String val) {
        try {
            if (val==null) val = "";
            Files.write(val, tmpFile, Charsets.UTF_8);
            FileBasedObjectStore.moveFile(tmpFile, file);
            
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void append(String val) {
        try {
            if (val==null) val = "";
            Files.append(val, file, Charsets.UTF_8);
            
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    public void delete() {
        file.delete();
        tmpFile.delete();
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("file", file).toString();
    }

}
