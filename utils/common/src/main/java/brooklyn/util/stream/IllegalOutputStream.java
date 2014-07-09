package brooklyn.util.stream;

import java.io.OutputStream;

/** output stream which throws if anyone tries to write to it */
public class IllegalOutputStream extends OutputStream {
    @Override public void write(int b) {
        throw new IllegalStateException("should not write to this output stream");
    }
    @Override public void write(byte[] b, int off, int len) {
        throw new IllegalStateException("should not write to this output stream");
    }
}
