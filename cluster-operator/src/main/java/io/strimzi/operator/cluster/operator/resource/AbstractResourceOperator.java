/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.operator.resource;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.strimzi.operator.cluster.model.Labels;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/*
AbstractResourceOperator<
    KubernetesClient, C
    ServiceAccount, T
    ServiceAccountList, L
    DoneableServiceAccount, D
    Resource<ServiceAccount, DoneableServiceAccount>> R
*/
public abstract class AbstractResourceOperator<C extends KubernetesClient, T extends HasMetadata,
        L extends KubernetesResourceList/*<T>*/, D, R extends Resource<T, D>> {

    private final Logger log = LogManager.getLogger(getClass());
    protected final Vertx vertx;
    protected final C client;
    protected final String resourceKind;

    // super(vertx, client, resourceKind);
    // super(vertx, client, "ServiceAccount");
    public AbstractResourceOperator(Vertx vertx, C client, String resourceKind) {
        this.vertx = vertx;
        this.client = client;
        this.resourceKind = resourceKind;
    }

    protected abstract MixedOperation<T, L, D, R> operation();

    public Future<ReconcileResult<T>> createOrUpdate(T resource) {
        if (resource == null) {
            throw new NullPointerException();
        }
        return reconcile(
            resource.getMetadata().getNamespace(),
            resource.getMetadata().getName(),
            resource
        );
    }

    public Future<ReconcileResult<T>> reconcile(String namespace, String name, T desired) {
        if (desired != null && !namespace.equals(desired.getMetadata().getNamespace())) {
            return Future.failedFuture("Given namespace " + namespace + " incompatible with desired namespace " + desired.getMetadata().getNamespace());
        } else if (desired != null && !name.equals(desired.getMetadata().getName())) {
            return Future.failedFuture("Given name " + name + " incompatible with desired name " + desired.getMetadata().getName());
        }
        Future<ReconcileResult<T>> fut = Future.future();
        vertx.createSharedWorkerExecutor("kubernetes-ops-pool").executeBlocking(future -> {
            T current = operation().inNamespace(namespace).withName(name).get();
            if (desired != null) {
                if (current == null) {
                    log.debug("{} {}/{} does not exist, creating it", resourceKind, namespace, name);
                    internalCreate(namespace, name, desired).setHandler(future);
                } else {
                    log.debug("{} {}/{} already exists, patching it", resourceKind, namespace, name);
                    internalPatch(namespace, name, current, desired).setHandler(future);
                }
            } else {
                if (current != null) {
                    // Deletion is desired
                    log.debug("{} {}/{} exist, deleting it", resourceKind, namespace, name);
                    internalDelete(namespace, name).setHandler(future);
                } else {
                    log.debug("{} {}/{} does not exist, noop", resourceKind, namespace, name);
                    future.complete(ReconcileResult.noop());
                }
            }
        }, false, fut.completer());
        return fut;
    }

    // internalCreate(namespace, name, desired)
    protected Future<ReconcileResult<T>> internalCreate(String namespace, String name, T desired) {
        try {
            ReconcileResult<T> result = ReconcileResult.created(
                operation().inNamespace(namespace).withName(name).create(desired)
            );
            log.debug("{} {} in namespace {} has been created", resourceKind, name, namespace);
            return Future.succeededFuture(result);
        } catch (Exception e) {
            log.error("Caught exception while creating {} {} in namespace {}", resourceKind, name, namespace, e);
            return Future.failedFuture(e);
        }
    }

    // internalPatch(namespace, name, current, desired)
    protected Future<ReconcileResult<T>> internalPatch(String namespace, String name, T current, T desired) {
        try {
            ReconcileResult.Patched<T> result = ReconcileResult.patched(
                operation().inNamespace(namespace).withName(name).cascading(true).patch(desired)
            );
            log.debug("{} {} in namespace {} has been patched", resourceKind, name, namespace);
            return Future.succeededFuture(result);
        } catch (Exception e) {
            log.error("Caught exception while patching {} {} in namespace {}", resourceKind, name, namespace, e);
            return Future.failedFuture(e);
        }
    }

    // internalDelete(namespace, name)
    protected Future<ReconcileResult<T>> internalDelete(String namespace, String name) {
        try {
            operation().inNamespace(namespace).withName(name).delete();
            log.debug("{} {} in namespace {} has been deleted", resourceKind, name, namespace);
            return Future.succeededFuture(ReconcileResult.deleted());
        } catch (Exception e) {
            log.error("Caught exception while deleting {} {} in namespace {}", resourceKind, name, namespace, e);
            return Future.failedFuture(e);
        }
    }

    public T get(String namespace, String name) {
        return operation().inNamespace(namespace).withName(name).get();
    }

    @SuppressWarnings("unchecked")
    public List<T> list(String namespace, Labels selector) {
        NonNamespaceOperation<T, L, D, R> tldrNonNamespaceOperation = operation().inNamespace(namespace);
        if (selector != null) {
            Map<String, String> labels = selector.toMap();
            return tldrNonNamespaceOperation.withLabels(labels).list().getItems();
        } else {
            return tldrNonNamespaceOperation.list().getItems();
        }
    }
}
