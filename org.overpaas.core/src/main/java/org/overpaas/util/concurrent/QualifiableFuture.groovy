package org.overpaas.util.concurrent;

import groovy.lang.Closure;

import java.util.concurrent.Future;

public interface QualifiableFuture<T> extends Future<T> {
	Future<T> when(Closure condition);
}
