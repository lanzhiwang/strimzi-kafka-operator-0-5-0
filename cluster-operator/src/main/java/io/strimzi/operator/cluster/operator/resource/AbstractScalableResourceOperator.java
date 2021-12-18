/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.operator.resource;

import io.fabric8.kubernetes.api.model.Doneable;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResourceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public abstract class AbstractScalableResourceOperator<C extends KubernetesClient,
            T extends HasMetadata,
            L extends KubernetesResourceList/*<T>*/,
            D extends Doneable<T>,
            R extends ScalableResource<T, D>>
        extends AbstractReadyResourceOperator<C, T, L, D, R> {

    private final Logger log = LogManager.getLogger(getClass());

    public AbstractScalableResourceOperator(Vertx vertx, C client, String resourceKind) {
        super(vertx, client, resourceKind);
    }

    private R resource(String namespace, String name) {
        return operation().inNamespace(namespace).withName(name);
    }

    public Future<Integer> scaleUp(String namespace, String name, int scaleTo) {
        Future<Integer> fut = Future.future();
        vertx.createSharedWorkerExecutor("kubernetes-ops-pool").executeBlocking(
            future -> {
                try {
                    Integer currentScale = currentScale(namespace, name);
                    if (currentScale != null && currentScale < scaleTo) {
                        log.info("Scaling up to {} replicas", scaleTo);
                        resource(namespace, name).scale(scaleTo, true);
                        currentScale = scaleTo;
                    }
                    future.complete(currentScale);
                } catch (Exception e) {
                    log.error("Caught exception while scaling up", e);
                    future.fail(e);
                }
            },
            false,
            fut.completer()
        );
        return fut;
    }

    protected abstract Integer currentScale(String namespace, String name);

    public Future<Integer> scaleDown(String namespace, String name, int scaleTo) {
        Future<Integer> fut = Future.future();
        vertx.createSharedWorkerExecutor("kubernetes-ops-pool").executeBlocking(
            future -> {
                try {
                    Integer nextReplicas = currentScale(namespace, name);
                    if (nextReplicas != null) {
                        while (nextReplicas > scaleTo) {
                            nextReplicas--;
                            log.info("Scaling down from {} to {}", nextReplicas + 1, nextReplicas);
                            resource(namespace, name).scale(nextReplicas, true);
                        }
                    }
                    future.complete(nextReplicas);
                } catch (Exception e) {
                    log.error("Caught exception while scaling down", e);
                    future.fail(e);
                }
            },
            false,
            fut.completer()
        );
        return fut;
    }
}
