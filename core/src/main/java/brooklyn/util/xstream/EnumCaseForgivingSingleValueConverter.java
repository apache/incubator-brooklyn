package brooklyn.util.xstream;

import com.thoughtworks.xstream.converters.enums.EnumSingleValueConverter;

public class EnumCaseForgivingSingleValueConverter extends EnumSingleValueConverter {

    private final Class enumType;

    public EnumCaseForgivingSingleValueConverter(Class type) {
        super(type);
        enumType = type;
    }

    public Object fromString(String str) {
        return EnumCaseForgivingConverter.resolve(enumType, str);
    }
}
