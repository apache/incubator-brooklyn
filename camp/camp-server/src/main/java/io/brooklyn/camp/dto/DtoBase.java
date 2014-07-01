package io.brooklyn.camp.dto;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class DtoBase {

    @Override public String toString() { return ToStringBuilder.reflectionToString(this); }
    @Override public boolean equals(Object obj) { return EqualsBuilder.reflectionEquals(this, obj); }
    @Override public int hashCode() { return HashCodeBuilder.reflectionHashCode(this); }

}
