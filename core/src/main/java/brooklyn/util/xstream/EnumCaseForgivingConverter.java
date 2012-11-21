package brooklyn.util.xstream;

import brooklyn.util.exceptions.Exceptions;

import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.enums.EnumConverter;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;

/** ... except this doesn't seem to get applied when we think it should
 * (normal xstream.resgisterConverter doesn't apply to enums) */
public class EnumCaseForgivingConverter extends EnumConverter {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        Class type = context.getRequiredType();
        if (type.getSuperclass() != Enum.class) {
            type = type.getSuperclass(); // polymorphic enums
        }
        String token = reader.getValue();
        // this is the new bit (overriding superclass to accept case-insensitive)
        return resolve(type, token);
    }

    public static <T extends Enum<T>> T resolve(Class<T> type, String token) {
        try {
            return Enum.valueOf(type, token.toUpperCase());
        } catch (Exception e) {
            
            // new stuff here:  try reading case insensitive
            
            Exceptions.propagateIfFatal(e);
            try {
                @SuppressWarnings("unchecked")
                T[] values = (T[]) type.getMethod("values").invoke(null);
                for (T v: values)
                    if (v.name().equalsIgnoreCase(token)) return v;
                throw e;
            } catch (Exception e2) {
                throw Exceptions.propagate(e2);
            }
        }
    }

}
