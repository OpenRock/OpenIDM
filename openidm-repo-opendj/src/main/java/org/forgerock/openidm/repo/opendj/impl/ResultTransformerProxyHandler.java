package org.forgerock.openidm.repo.opendj.impl;/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions copyright [year] [name of copyright owner]".
 *
 * Copyright 2015 ForgeRock AS.
 */

import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;

/**
 * A {@link ResultHandler} that runs a transform function on a result prior to
 * passing it along to the proxied handler.
 *
 * @param <V> Type of Result
 */
public abstract class ResultTransformerProxyHandler<V> implements ResultHandler<V> {
    /** The handler we are proxying */
    final ResultHandler<V> handler;

    /**
     * Create a new transformer proxy
     *
     * @param handler The handler we are proxying
     */
    public ResultTransformerProxyHandler(ResultHandler<V> handler) {
        this.handler = handler;
    }

    /**
     * Transformation applied to the object prior to being sent to the proxied handler.
     *
     * @param obj The object we are to transform
     *
     * @return The transformed object
     *
     * @throws ResourceException If there was an error during transformation
     */
    abstract V transform(V obj) throws ResourceException;

    @Override
    public void handleError(ResourceException error) {
        handler.handleError(error);
    }

    @Override
    public void handleResult(V result) {
        try {
            handler.handleResult(transform(result));
        } catch (ResourceException e) {
            handler.handleError(e);
        }
    }
}
