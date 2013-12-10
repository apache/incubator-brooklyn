package brooklyn.util.stream;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.io.InputSupplier;

public class InputStreamSupplier implements InputSupplier<InputStream> {

    private final InputStream target;

    /** @deprecated since 0.7.0; use {@link InputStreamSupplier#of(InputStream)} instead */
    @Deprecated
    public InputStreamSupplier(InputStream target) {
        this.target = target;
    }

    @Override
    public InputStream getInput() throws IOException {
        return target;
    }

    public static InputStreamSupplier of(InputStream target) {
        return new InputStreamSupplier(target);
    }

    public static InputStreamSupplier fromString(String input) {
        return new InputStreamSupplier(Streams.newInputStreamWithContents(input));
    }

}
