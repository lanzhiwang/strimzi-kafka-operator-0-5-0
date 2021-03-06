/*
 * Copyright 2017-2018, Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.operator.cluster.operator.resource;

import io.fabric8.kubernetes.api.model.DoneableEndpoints;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.vertx.core.Vertx;

class EndpointOperator extends AbstractReadyResourceOperator<KubernetesClient, Endpoints, EndpointsList, DoneableEndpoints, Resource<Endpoints, DoneableEndpoints>> {

    EndpointOperator(Vertx vertx, KubernetesClient client) {
        super(vertx, client, "Endpoints");
    }

    @Override
    protected MixedOperation<Endpoints, EndpointsList, DoneableEndpoints, Resource<Endpoints, DoneableEndpoints>> operation() {
        return client.endpoints();
    }
}
