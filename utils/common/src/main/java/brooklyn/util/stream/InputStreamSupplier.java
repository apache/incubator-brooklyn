package brooklyn.util.stream;

import java.io.IOException;
import java.io.InputStream;

import com.google.common.io.InputSupplier;

public class InputStreamSupplier implements InputSupplier<InputStream> {

    final InputStream target;
    
    public InputStreamSupplier(InputStream target) {
        this.target = target;
    }

    @Override
    public InputStream getInput() throws IOException {
        return target;
    }

    public static InputStreamSupplier fromString(String input) {
        return new InputStreamSupplier(Streams.fromString(input));
    }
    
}
