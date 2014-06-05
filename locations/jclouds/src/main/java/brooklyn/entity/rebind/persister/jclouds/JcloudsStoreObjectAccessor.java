package brooklyn.entity.rebind.persister.jclouds;

import java.io.IOException;

import org.apache.commons.io.Charsets;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.util.Strings2;

import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Throwables;
import com.google.common.io.ByteSource;

/**
 * @author Andrea Turli
 */
public class JcloudsStoreObjectAccessor implements PersistenceObjectStore.StoreObjectAccessor {

    private final BlobStore blobStore;
    private final String containerName;
    private final String blobName;

    public JcloudsStoreObjectAccessor(BlobStore blobStore, String containerName, String blobNameOptionallyWithPath) {
        this.blobStore = blobStore;
        this.containerName = containerName;
        this.blobName = blobNameOptionallyWithPath;
    }

    @Override
    public boolean exists() {
        return blobStore.blobExists(containerName, blobName);
    }

    @Override
    public void put(String val) {
        if (val==null) val = "";
        
        blobStore.createContainerInLocation(null, containerName);
        // seems not needed, at least not w SoftLayer
//        blobStore.createDirectory(containerName, directoryName);
        ByteSource payload = ByteSource.wrap(val.getBytes(Charsets.UTF_8));
        Blob blob;
        try {
            blob = blobStore.blobBuilder(blobName).payload(payload)
                    .contentLength(payload.size())
                    .build();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        blobStore.putBlob(containerName, blob);
    }

    @Override
    public void append(String val) {
        String val0 = get();
        if (val0==null) val0="";
        if (val==null) val="";
        put(val0+val);
    }

    @Override
    public void delete() {
        blobStore.removeBlob(containerName, blobName);
    }

    @Override
    public String get() {
        try {
            Blob blob = blobStore.getBlob(containerName, blobName);
            if (blob==null) return null;
            return Strings2.toString(blob.getPayload());
        } catch (IOException e) {
            Exceptions.propagateIfFatal(e);
            throw new IllegalStateException("Error reading blobstore "+containerName+" "+blobName+": "+e, e);
        }
    }
}
