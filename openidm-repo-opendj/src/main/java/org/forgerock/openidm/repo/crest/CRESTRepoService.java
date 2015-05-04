/*
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

package org.forgerock.openidm.repo.crest;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResult;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.openidm.repo.RepoBootService;
import org.forgerock.openidm.repo.RepositoryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Generic repository wrapping a {@link CollectionResourceProvider}
 */
public abstract class CRESTRepoService implements RequestHandler, RepositoryService, RepoBootService {
    private CollectionResourceProvider resourceProvider;

    public CRESTRepoService(CollectionResourceProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
    }

    public CRESTRepoService() {}

    protected abstract String getResourceId(Request request);

    protected void setResourceProvider(CollectionResourceProvider provider) {
        this.resourceProvider = provider;
    }

    protected class BlockingResultHandler<T> implements ResultHandler<T> {
        private T result = null;
        private ResourceException exception = null;

        @Override
        public synchronized void handleError(ResourceException error) {
            this.exception = error;
            notify();
        }

        @Override
        public synchronized void handleResult(T result) {
            this.result = result;
            notify();
        }

        public synchronized T get() throws ResourceException {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new InternalServerErrorException(e.getMessage(), e);
            }

            if (exception != null) {
                throw exception;
            } else {
                return result;
            }
        }
    }

    protected class BlockingQueryResultHandler implements QueryResultHandler {
        private List<Resource> results = new ArrayList<>();
        private ResourceException exception = null;

        @Override
        public synchronized void handleError(ResourceException error) {
            this.exception = error;
            notify();
        }

        @Override
        public synchronized boolean handleResource(Resource resource) {
            results.add(resource);

            /*
             * TODO - this will go on forever so long as there are more results.
             *        we may wish to constrain this by returning false eventually.
             */

            return true;
        }

        @Override
        public synchronized void handleResult(QueryResult result) {
            notify();
        }

        public synchronized List<Resource> get() throws ResourceException {
            try {
                wait();
            } catch (InterruptedException e) {
                throw new InternalServerErrorException(e.getMessage(), e);
            }

            if (exception != null) {
                throw exception;
            } else {
                return results;
            }
        }
    }

    @Override
    public Resource create(CreateRequest request) throws ResourceException {
        BlockingResultHandler<Resource> handler = new BlockingResultHandler<Resource>();

        handleCreate(null, request, handler);

        return handler.get();
    }

    @Override
    public Resource read(ReadRequest request) throws ResourceException {
        BlockingResultHandler<Resource> handler = new BlockingResultHandler<Resource>();

        handleRead(null, request, handler);

        return handler.get();
    }

    @Override
    public Resource update(UpdateRequest request) throws ResourceException {
        BlockingResultHandler<Resource> handler = new BlockingResultHandler<Resource>();

        handleUpdate(null, request, handler);

        return handler.get();
    }

    @Override
    public Resource delete(DeleteRequest request) throws ResourceException {
        BlockingResultHandler<Resource> handler = new BlockingResultHandler<Resource>();

        handleDelete(null, request, handler);

        return handler.get();
    }

    @Override
    public List<Resource> query(QueryRequest request) throws ResourceException {
        BlockingQueryResultHandler handler = new BlockingQueryResultHandler();

        handleQuery(null, request, handler);

        return handler.get();
    }

    @Override
    public void handleAction(ServerContext context, ActionRequest request, ResultHandler<JsonValue> handler) {
        String resourceId = getResourceId(request);
        resourceProvider.actionInstance(context, resourceId, request, handler);
    }

    @Override
    public void handleCreate(ServerContext context, CreateRequest request, ResultHandler<Resource> handler) {
        resourceProvider.createInstance(context, request, handler);
    }

    @Override
    public void handleDelete(ServerContext context, DeleteRequest request, ResultHandler<Resource> handler) {
        String resourceId = getResourceId(request);
        resourceProvider.deleteInstance(context, resourceId, request, handler);
    }

    @Override
    public void handlePatch(ServerContext context, PatchRequest request, ResultHandler<Resource> handler) {
        String resourceId = getResourceId(request);
        resourceProvider.patchInstance(context, resourceId, request, handler);
    }

    @Override
    public void handleQuery(ServerContext context, QueryRequest request, QueryResultHandler handler) {
        resourceProvider.queryCollection(context, request, handler);
    }

    @Override
    public void handleRead(ServerContext context, ReadRequest request, ResultHandler<Resource> handler) {
        String resourceId = getResourceId(request);
        resourceProvider.readInstance(context, resourceId, request, handler);
    }

    @Override
    public void handleUpdate(ServerContext context, UpdateRequest request, ResultHandler<Resource> handler) {
        String resourceId = getResourceId(request);
        resourceProvider.updateInstance(context, resourceId, request, handler);
    }
}
