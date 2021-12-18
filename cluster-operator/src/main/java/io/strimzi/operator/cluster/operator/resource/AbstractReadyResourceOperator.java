/*
 * Copyright 2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.operator.resource;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiPredicate;

public abstract class AbstractReadyResourceOperator<C extends KubernetesClient,
            T extends HasMetadata,
            L extends KubernetesResourceList/*<T>*/,
            D extends Doneable<T>,
            R extends Resource<T, D>>
        extends AbstractResourceOperator<C, T, L, D, R> {

    private final Logger log = LogManager.getLogger(getClass());

    // super(vertx, client, "Endpoints");
    public AbstractReadyResourceOperator(Vertx vertx, C client, String resourceKind) {
        super(vertx, client, resourceKind);
    }

    /*
    endpointOperations.readiness(
        namespace,
        desired.getMetadata().getName(),
        1_000,
        operationTimeoutMs
    );
    */
    public Future<Void> readiness(String namespace, String name, long pollIntervalMs, long timeoutMs) {
        return waitFor(namespace, name, pollIntervalMs, timeoutMs, this::isReady);
    }

    // waitFor(namespace, name, pollIntervalMs, timeoutMs, this::isReady);
    public Future<Void> waitFor(String namespace, String name, long pollIntervalMs, final long timeoutMs, BiPredicate<String, String> predicate) {
        Future<Void> fut = Future.future();
        log.debug("Waiting for {} resource {} in namespace {} to get ready", resourceKind, name, namespace);
        long deadline = System.currentTimeMillis() + timeoutMs;
        Handler<Long> handler = new Handler<Long>() {
            @Override
            public void handle(Long timerId) {
                vertx.createSharedWorkerExecutor("kubernetes-ops-pool").executeBlocking(future -> {
                    try {
                        if (predicate.test(namespace, name))   {
                            future.complete();
                        } else {
                            log.trace("{} {} in namespace {} is not ready", resourceKind, name, namespace);
                            future.fail("Not ready yet");
                        }
                    } catch (Throwable e) {
                        log.warn("Caught exception while waiting for {} {} in namespace {} to get ready", resourceKind, name, namespace, e);
                        future.fail(e);
                    }
                }, true, res -> {
                    if (res.succeeded()) {
                        log.debug("{} {} in namespace {} is ready", resourceKind, name, namespace);
                        fut.complete();
                    } else {
                        long timeLeft = deadline - System.currentTimeMillis();
                        if (timeLeft <= 0) {
                            String exceptionMessage = String.format("Exceeded timeout of %dms while waiting for %s %s in namespace %s to be ready", timeoutMs, resourceKind, name, namespace);
                            log.error(exceptionMessage);
                            fut.fail(new TimeoutException(exceptionMessage));
                        } else {
                            // Schedule ourselves to run again
                            vertx.setTimer(Math.min(pollIntervalMs, timeLeft), this);
                        }
                    }
                });
            }
        };
        handler.handle(null);
        return fut;
    }

    public boolean isReady(String namespace, String name) {
        R resourceOp = operation().inNamespace(namespace).withName(name);
        T resource = resourceOp.get();
        if (resource != null)   {
            if (Readiness.isReadinessApplicable(resource.getClass())) {
                return Boolean.TRUE.equals(resourceOp.isReady());
            } else {
                return true;
            }
        } else {
            return false;
        }
    }
}
