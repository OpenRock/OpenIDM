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

import java.util.ArrayList;
import java.util.List;

import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.ActionResponse;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResourceHandler;
import org.forgerock.json.resource.QueryResponse;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceResponse;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.services.context.Context;
import org.forgerock.util.promise.Promise;

/**
 * CREST-based repository implementation using {@link RequestHandler}'s
 */
public abstract class CRESTTypeHandler implements TypeHandler {
    private CollectionResourceProvider resourceProvider;

    public CRESTTypeHandler(CollectionResourceProvider resourceProvider) {
        this.resourceProvider = resourceProvider;
    }

    public CRESTTypeHandler() {}

    protected String getResourceId(final Request request) {
        return request.getResourcePathObject().leaf();
    }

    protected void setResourceProvider(CollectionResourceProvider provider) {
        this.resourceProvider = provider;
    }

    @Override
    public ResourceResponse create(CreateRequest request) throws ResourceException {
        return handleCreate(null, request).getOrThrowUninterruptibly();
    }

    @Override
    public ResourceResponse read(ReadRequest request) throws ResourceException {
        return handleRead(null, request).getOrThrowUninterruptibly();
    }

    @Override
    public ResourceResponse update(UpdateRequest request) throws ResourceException {
        return handleUpdate(null, request).getOrThrowUninterruptibly();
    }

    @Override
    public ResourceResponse delete(DeleteRequest request) throws ResourceException {
        return handleDelete(null, request).getOrThrowUninterruptibly();
    }

    @Override
    public List<ResourceResponse> query(QueryRequest request) throws ResourceException {
        final List<ResourceResponse> results = new ArrayList<>();
        final QueryResourceHandler handler = new QueryResourceHandler() {
            @Override
            public boolean handleResource(ResourceResponse resourceResponse) {
                results.add(resourceResponse);
                return true;
            }
        };

        handleQuery(null, request, handler).getOrThrowUninterruptibly();

        return results;
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleCreate(final Context context, final CreateRequest createRequest) {
        return resourceProvider.createInstance(context, createRequest);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleRead(Context context, ReadRequest readRequest) {
        String resourceId = getResourceId(readRequest);
        return resourceProvider.readInstance(context, resourceId, readRequest);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleUpdate(final Context context, final UpdateRequest updateRequest) {
        String resourceId = getResourceId(updateRequest);
        return resourceProvider.updateInstance(context, resourceId, updateRequest);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handleDelete(final Context context, final DeleteRequest deleteRequest) {
        String resourceId = getResourceId(deleteRequest);
        return resourceProvider.deleteInstance(context, resourceId, deleteRequest);
    }

    @Override
    public Promise<ResourceResponse, ResourceException> handlePatch(final Context context, final PatchRequest patchRequest) {
        String resourceId = getResourceId(patchRequest);
        return resourceProvider.patchInstance(context, resourceId, patchRequest);
    }

    @Override
    public Promise<ActionResponse, ResourceException> handleAction(final Context context, final ActionRequest request) {
        String resourceId = getResourceId(request);
        return resourceProvider.actionInstance(context, resourceId, request);
    }

    @Override
    public Promise<QueryResponse, ResourceException> handleQuery(final Context context, final QueryRequest queryRequest, final QueryResourceHandler queryResourceHandler) {
        return resourceProvider.queryCollection(context, queryRequest, queryResourceHandler);
    }
}
