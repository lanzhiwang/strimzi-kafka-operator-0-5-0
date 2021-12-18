/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.operator.resource;

public abstract class ReconcileResult<R> {

    private static final ReconcileResult DELETED = new ReconcileResult(null) {
        public String toString() {
            return "DELETED";
        }
    };

    private static final ReconcileResult NOOP = new ReconcileResult(null) {
        public String toString() {
            return "NOOP";
        }
    };

    public static class Created<R> extends ReconcileResult<R> {
        private Created(R resource) {
            super(resource);
        }

        public String toString() {
            return "CREATED";
        }
    }

    public static class Patched<R> extends ReconcileResult<R> {

        private Patched(R resource) {
            super(resource);
        }

        public String toString() {
            return "PATCH";
        }
    }

    // ReconcileResult.Patched<T> result = ReconcileResult.patched(
    //     operation().inNamespace(namespace).withName(name).cascading(true).patch(desired)
    // );
    public static final <D> Patched<D> patched(D resource) {
        return new Patched(resource);
    }

    // ReconcileResult<T> result = ReconcileResult.created(
    //     operation().inNamespace(namespace).withName(name).create(desired)
    // );
    public static final <D> ReconcileResult<D> created(D resource) {
        return new Created<>(resource);
    }

    // ReconcileResult.deleted()
    public static final <P> ReconcileResult<P> deleted() {
        return DELETED;
    }

    // ReconcileResult.noop()
    public static final <P> ReconcileResult<P> noop() {
        return NOOP;
    }

    private final R resource;

    // Resource<ServiceAccount, DoneableServiceAccount>> R
    private ReconcileResult(R resource) {
        this.resource = resource;
    }

    public R resource() {
        return this.resource;
    }
}
