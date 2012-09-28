package brooklyn.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

import com.google.common.io.Closeables;

public class Serializers {

    public static <T> T reconstitute(T object) throws IOException, ClassNotFoundException {
        if (object == null) return null;
        return reconstitute(object, object.getClass().getClassLoader());
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T reconstitute(T object, ClassLoader classLoader) throws IOException, ClassNotFoundException {
        if (object == null) return null;
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(object);
        oos.close();
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ClassLoaderObjectInputStream(bais, classLoader);
        try {
            return (T) ois.readObject();
        } finally {
            Closeables.closeQuietly(ois);
        }
    }
    
    /**
     * Follows pattern in org.apache.commons.io.input.ClassLoaderObjectInputStream
     */
    public static class ClassLoaderObjectInputStream extends ObjectInputStream {

        private final ClassLoader classLoader;
        
        public ClassLoaderObjectInputStream(InputStream inputStream, ClassLoader classLoader) throws IOException {
            super(inputStream);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
            Class<?> clazz = Class.forName(objectStreamClass.getName(), false, classLoader);

            if (clazz != null) {
                return clazz;
            } else {
                return super.resolveClass(objectStreamClass);
            }
        }
    }
}
