package brooklyn.catalog;

import javax.annotation.Nullable;

import brooklyn.catalog.CatalogItem.CatalogItemType;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.policy.Policy;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

public class CatalogPredicates {

    public static <T> Predicate<CatalogItem<T>> isCatalogItemType(final CatalogItemType ciType) {
        return new Predicate<CatalogItem<T>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T> item) {
                return item!=null && item.getCatalogItemType()==ciType;
            }
        };
    }

    public static final Predicate<CatalogItem<Application>> IS_TEMPLATE = 
            CatalogPredicates.<Application>isCatalogItemType(CatalogItemType.TEMPLATE);
    public static final Predicate<CatalogItem<Entity>> IS_ENTITY = 
            CatalogPredicates.<Entity>isCatalogItemType(CatalogItemType.ENTITY);
    public static final Predicate<CatalogItem<Policy>> IS_POLICY = 
            CatalogPredicates.<Policy>isCatalogItemType(CatalogItemType.POLICY);
    
    public static final Function<CatalogItem<?>,String> ID_OF_ITEM_TRANSFORMER = new Function<CatalogItem<?>, String>() {
        @Override @Nullable
        public String apply(@Nullable CatalogItem<?> input) {
            if (input==null) return null;
            return input.getId();
        }
    };

    public static <T> Predicate<CatalogItem<T>> name(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T> item) {
                return item!=null && filter.apply(item.getName());
            }
        };
    }

    public static <T> Predicate<CatalogItem<T>> javaType(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T> item) {
                return item!=null && filter.apply(item.getJavaType());
            }
        };
    }

    public static <T> Predicate<CatalogItem<T>> xml(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T> item) {
                return item!=null && filter.apply(item.toXmlString());
            }
        };
    }
}