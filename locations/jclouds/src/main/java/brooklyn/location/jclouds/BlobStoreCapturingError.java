package brooklyn.location.jclouds;

import java.util.Set;

import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;
import org.jclouds.blobstore.domain.BlobBuilder;
import org.jclouds.blobstore.domain.BlobMetadata;
import org.jclouds.blobstore.domain.PageSet;
import org.jclouds.blobstore.domain.StorageMetadata;
import org.jclouds.blobstore.options.CreateContainerOptions;
import org.jclouds.blobstore.options.GetOptions;
import org.jclouds.blobstore.options.ListContainerOptions;
import org.jclouds.blobstore.options.PutOptions;
import org.jclouds.domain.Location;

public class BlobStoreCapturingError implements BlobStore {

    BlobStore delegate;

    public BlobStoreContext getContext() {
        return delegate.getContext();
    }

    public BlobBuilder blobBuilder(String name) {
        return delegate.blobBuilder(name);
    }

    public Set<? extends Location> listAssignableLocations() {
        return delegate.listAssignableLocations();
    }

    public PageSet<? extends StorageMetadata> list() {
        return delegate.list();
    }

    public boolean containerExists(String container) {
        return delegate.containerExists(container);
    }

    public boolean createContainerInLocation(Location location, String container) {
        return delegate.createContainerInLocation(location, container);
    }

    public boolean createContainerInLocation(Location location, String container, CreateContainerOptions options) {
        return delegate.createContainerInLocation(location, container, options);
    }

    public PageSet<? extends StorageMetadata> list(String container) {
        return delegate.list(container);
    }

    public PageSet<? extends StorageMetadata> list(String container, ListContainerOptions options) {
        return delegate.list(container, options);
    }

    public void clearContainer(String container) {
        delegate.clearContainer(container);
    }

    public void clearContainer(String container, ListContainerOptions options) {
        delegate.clearContainer(container, options);
    }

    public void deleteContainer(String container) {
        delegate.deleteContainer(container);
    }

    public boolean directoryExists(String container, String directory) {
        return delegate.directoryExists(container, directory);
    }

    public void createDirectory(String container, String directory) {
        delegate.createDirectory(container, directory);
    }

    public void deleteDirectory(String containerName, String name) {
        delegate.deleteDirectory(containerName, name);
    }

    public boolean blobExists(String container, String name) {
        return delegate.blobExists(container, name);
    }

    public String putBlob(String container, Blob blob) {
        return delegate.putBlob(container, blob);
    }

    public String putBlob(String container, Blob blob, PutOptions options) {
        return delegate.putBlob(container, blob, options);
    }

    public BlobMetadata blobMetadata(String container, String name) {
        return delegate.blobMetadata(container, name);
    }

    public Blob getBlob(String container, String name) {
        return delegate.getBlob(container, name);
    }

    public Blob getBlob(String container, String name, GetOptions options) {
        return delegate.getBlob(container, name, options);
    }

    public void removeBlob(String container, String name) {
        delegate.removeBlob(container, name);
    }

    public long countBlobs(String container) {
        return delegate.countBlobs(container);
    }

    public long countBlobs(String container, ListContainerOptions options) {
        return delegate.countBlobs(container, options);
    }
    
    
}
