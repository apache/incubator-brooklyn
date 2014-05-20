package brooklyn.util.guava;

import javax.annotation.Nullable;

import com.google.common.reflect.TypeToken;

public class TypeTokens {

    /** returns raw type, if it's raw, else null;
     * used e.g. to set only one of the raw type or the type token,
     * for instance to make serialized output nicer */
    @Nullable
    public static <T> Class<? super T> getRawTypeIfRaw(@Nullable TypeToken<T> type) {
        if (type==null || !type.equals(TypeToken.of(type.getRawType()))) {
            return null;
        } else {
            return type.getRawType();
        }
    }
    
    /** returns null if it's raw, else the type token */
    @Nullable
    public static <T> TypeToken<T> getTypeTokenIfNotRaw(@Nullable TypeToken<T> type) {
        if (type==null || type.equals(TypeToken.of(type.getRawType()))) {
            return null;
        } else {
            return type;
        }
    }
    
    /** given either a token or a raw type, returns the raw type */
    public static <T> Class<? super T> getRawType(TypeToken<T> token, Class<? super T> raw) {
        if (raw!=null) return raw;
        if (token!=null) return token.getRawType();
        throw new IllegalStateException("Both indicators of type are null");
    }
    
    
    /** given either a token or a raw type, returns the token */
    @SuppressWarnings("unchecked")
    public static <T> TypeToken<T> getTypeToken(TypeToken<T> token, Class<? super T> raw) {
        if (token!=null) return token;
        if (raw!=null) return TypeToken.of((Class<T>)raw);
        throw new IllegalStateException("Both indicators of type are null");
    }

}
