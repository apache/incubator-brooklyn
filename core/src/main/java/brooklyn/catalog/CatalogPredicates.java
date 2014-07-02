package brooklyn.catalog;

import javax.annotation.Nullable;

import brooklyn.catalog.CatalogItem.CatalogItemType;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;

import com.google.common.base.Function;
import com.google.common.base.Predicate;

public class CatalogPredicates {

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> isCatalogItemType(final CatalogItemType ciType) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && item.getCatalogItemType()==ciType;
            }
        };
    }

    public static final Predicate<CatalogItem<Application,EntitySpec<? extends Application>>> IS_TEMPLATE = 
            CatalogPredicates.<Application,EntitySpec<? extends Application>>isCatalogItemType(CatalogItemType.TEMPLATE);
    public static final Predicate<CatalogItem<Entity,EntitySpec<?>>> IS_ENTITY = 
            CatalogPredicates.<Entity,EntitySpec<?>>isCatalogItemType(CatalogItemType.ENTITY);
    public static final Predicate<CatalogItem<Policy,PolicySpec<?>>> IS_POLICY = 
            CatalogPredicates.<Policy,PolicySpec<?>>isCatalogItemType(CatalogItemType.POLICY);
    
    public static final Function<CatalogItem<?,?>,String> ID_OF_ITEM_TRANSFORMER = new Function<CatalogItem<?,?>, String>() {
        @Override @Nullable
        public String apply(@Nullable CatalogItem<?,?> input) {
            if (input==null) return null;
            return input.getId();
        }
    };

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> name(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.getName());
            }
        };
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> javaType(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.getJavaType());
            }
        };
    }

    public static <T,SpecT> Predicate<CatalogItem<T,SpecT>> xml(final Predicate<? super String> filter) {
        return new Predicate<CatalogItem<T,SpecT>>() {
            @Override
            public boolean apply(@Nullable CatalogItem<T,SpecT> item) {
                return (item != null) && filter.apply(item.toXmlString());
            }
        };
    }
}