package brooklyn.entity.drivers.downloads;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public class BasicDownloadResolver implements DownloadResolver {

    private final List<String> targets;
    private final String filename;
    private final String unpackDirectoryName;

    public BasicDownloadResolver(Iterable<String> targets, String filename) {
        this(targets, filename, null);
    }
    
    public BasicDownloadResolver(Iterable<String> targets, String filename, String unpackDirectoryName) {
        this.targets = ImmutableList.copyOf(checkNotNull(targets, "targets"));
        this.filename = checkNotNull(filename, "filename");
        this.unpackDirectoryName = unpackDirectoryName;
    }
    
    @Override
    public List<String> getTargets() {
        return targets;
    }

    @Override
    public String getFilename() {
        return filename;
    }

    @Override
    public String getUnpackedDirectorName(String defaultVal) {
        return unpackDirectoryName == null ? defaultVal : unpackDirectoryName;
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("targets", targets).add("filename", filename)
                .add("unpackDirName", unpackDirectoryName).omitNullValues().toString();
    }
}
