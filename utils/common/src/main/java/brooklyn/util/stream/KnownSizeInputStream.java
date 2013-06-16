package brooklyn.util.stream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** an input stream, whose size we know */
public class KnownSizeInputStream extends InputStream {

    final long length;
    final InputStream target;
    
    public KnownSizeInputStream(InputStream target, long length) {
        this.target = target;
        this.length = length;
    }
    
    public long length() {
        return length;
    }
    
    public InputStream getTarget() {
        return target;
    }

    public int read() throws IOException {
        return target.read();
    }

    public int hashCode() {
        return target.hashCode();
    }

    public int read(byte[] b) throws IOException {
        return target.read(b);
    }

    public boolean equals(Object obj) {
        return target.equals(obj);
    }

    public int read(byte[] b, int off, int len) throws IOException {
        return target.read(b, off, len);
    }

    public long skip(long n) throws IOException {
        return target.skip(n);
    }

    public int available() throws IOException {
        return target.available();
    }

    public String toString() {
        return target.toString();
    }

    public void close() throws IOException {
        target.close();
    }

    public void mark(int readlimit) {
        target.mark(readlimit);
    }

    public void reset() throws IOException {
        target.reset();
    }

    public boolean markSupported() {
        return target.markSupported();
    }

    public static KnownSizeInputStream of(String contents) {
        return of(contents.getBytes());
    }

    public static KnownSizeInputStream of(byte[] contents) {
        return new KnownSizeInputStream(new ByteArrayInputStream(contents), contents.length);
    }
    
}
