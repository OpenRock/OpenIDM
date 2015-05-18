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

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryFilter;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResult;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResourceName;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.openidm.repo.crest.CRESTTypeHandler;
import org.forgerock.openidm.repo.util.TokenHandler;
import org.forgerock.openidm.router.RouteEntry;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A handler for a single type, eg "managed/user".
 */
public class OpenDJTypeHandler extends CRESTTypeHandler {
    /** Properties that should be stored as strings */
    private final Set<String> propertiesToStringify;

    /** Configured queries for this type */
    private final Map<String, JsonValue> queries;

    private static final ObjectMapper mapper = new ObjectMapper();

    private RouteEntry routeEntry;

    @Override
    public RouteEntry getRouteEntry() {
        return routeEntry;
    }

    @Override
    public void setRouteEntry(RouteEntry routeEntry) {
        this.routeEntry = routeEntry;
    }

    /**
     * ResultHandler proxy that converts string fields back to JsonValues
     */
    class ResourceProxyHandler extends ResultTransformerProxyHandler<Resource> {
        ResourceProxyHandler(ResultHandler<Resource> handler) {
            super(handler);
        }

        @Override
        Resource transform(Resource obj) throws ResourceException {
            return destringify(obj, propertiesToStringify);
        }
    }

    /**
     * ResultHandler proxy that converts string fields back to JsonValues
     */
    class JsonValueProxyHandler extends ResultTransformerProxyHandler<JsonValue> {
        JsonValueProxyHandler(ResultHandler<JsonValue> handler) {
            super(handler);
        }

        @Override
        JsonValue transform(JsonValue obj) throws ResourceException {
            return destringify(obj, propertiesToStringify);
        }
    }

    // TODO - merge queries and config?
    //        This would likely involve a rehaul of the way repo queries are defined
    OpenDJTypeHandler(RouteEntry routeEntry, ConnectionFactory connectionFactory, JsonValue config, JsonValue queries) {
        this.routeEntry = routeEntry;
        this.propertiesToStringify = config.get("propertiesToStringify")
                .defaultTo(new HashSet<String>()).asSet(String.class);

        this.queries = new HashMap<>();
        for (String queryId : queries.keys()) {
            this.queries.put(queryId, queries.get(queryId));
        }

        JsonValue rest2ldapConfig = config.get("rest2ldapConfig");
        String baseDn = rest2ldapConfig.get("baseDN").required().asString();

        setResourceProvider(Rest2LDAP.builder()
                .ldapConnectionFactory(connectionFactory)
                .baseDN(baseDn)
                .configureMapping(rest2ldapConfig)
                .build()
        );
    }

    /**
     * Convert stringified properties in the Json object to JsonValues
     *
     * @param jsonValue Json object to de-stringify
     * @param properties Collection of object attributes to destringify
     *
     * @return A new JsonValue with the given properties de-stringified
     *
     * @throws ResourceException
     */
    static JsonValue destringify(final JsonValue jsonValue, final Collection<String> properties) throws ResourceException {
        Map<String, Object> obj = jsonValue.asMap();

        final TypeReference<LinkedHashMap<String,Object>> typeRef = new TypeReference<LinkedHashMap<String,Object>>() {};

        try {
            // FIXME - parameterize
            for (String key : properties) {
                String val = (String) obj.get(key);

                if (val != null) {
                    obj.put(key, mapper.readValue(val, typeRef));
                }
            }
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to convert String property to object", e);
        }

        return new JsonValue(obj);
    }

    /**
     * Convert the given "stringified" properties (made with {@link #stringify(JsonValue, Collection)}
     * in the {@link Resource} to {@link JsonValue}s
     *
     * @param r The resource whose properties we wish to destringify
     * @param properties Collection of property names that need to be destringified
     *
     * @return A new {@link Resource} in a pre-stringified state.
     *
     * @throws ResourceException
     */
    static Resource destringify(Resource r, Collection<String> properties) throws ResourceException {
        return new Resource(r.getId(), r.getRevision(), destringify(r.getContent(), properties));
    }

    /**
     * Convert the given collection of properties to Strings. This can be useful if a datastore does not support
     * embedded objects.
     *
     * @param jsonValue Json object to convert
     * @param properties List of properties that need to be processed
     *
     * @return A new JSON object with stringified properties
     *
     * @throws ResourceException If there is an error converting the property
     */
    static JsonValue stringify(JsonValue jsonValue, Collection<String> properties) throws ResourceException {
        Map<String, Object> obj = jsonValue.asMap();

        for (String property : properties) {
            try {
                Object val = obj.get(property);

                if (val != null) {
                    obj.put(property, mapper.writeValueAsString(val));
                }
            } catch (IOException e) {
                throw new InternalServerErrorException("Failed to convert property '" + property + "' to String", e);
            }
        }

        return new JsonValue(obj);
    }

    @Override
    public void handleDelete(ServerContext context, DeleteRequest request, ResultHandler<Resource> handler) {
        super.handleDelete(context, request, new ResourceProxyHandler(handler));
    }

    @Override
    public void handleAction(ServerContext context, ActionRequest request, ResultHandler<JsonValue> handler) {
        super.handleAction(context, request, new JsonValueProxyHandler(handler));
    }

    @Override
    public void handlePatch(ServerContext context, PatchRequest request, ResultHandler<Resource> handler) {
        super.handlePatch(context, request, new ResourceProxyHandler(handler));
    }

    @Override
    public void handleRead(ServerContext context, ReadRequest request, ResultHandler<Resource> handler) {
        super.handleRead(context, request, new ResourceProxyHandler(handler));
    }

    @Override
    public void handleUpdate(ServerContext context, UpdateRequest _request, ResultHandler<Resource> handler) {
        try {
            final UpdateRequest updateRequest = Requests.copyOfUpdateRequest(_request);
            updateRequest.setContent(stringify(updateRequest.getContent(), propertiesToStringify));

            super.handleUpdate(context, updateRequest, new ResourceProxyHandler(handler));
        } catch (ResourceException e) {
            handler.handleError(e);
        }
    }

    @Override
    public void handleCreate(final ServerContext context, final CreateRequest _request, final ResultHandler<Resource> handler) {
        final CreateRequest createRequest = Requests.copyOfCreateRequest(_request);

        try {
            Map<String, Object> obj = stringify(createRequest.getContent(), propertiesToStringify).asMap();

            // Set id to a new UUID if none is specified (_action=create)
            if (StringUtils.isBlank(createRequest.getNewResourceId())) {
                createRequest.setNewResourceId(UUID.randomUUID().toString());
            }

            obj.put("_id", createRequest.getNewResourceId());

            /*
             * XXX - all nulls are coming in as blank Strings. INVESTIGATE
             */

            Iterator<Map.Entry<String, Object>> iter = obj.entrySet().iterator();

            while (iter.hasNext()) {
                Map.Entry<String, Object> entry = iter.next();
                Object val = entry.getValue();

                if (val instanceof String && StringUtils.isBlank((String) val)) {
                    iter.remove();
                }
            }

            createRequest.setContent(new JsonValue(obj));

            super.handleCreate(context, createRequest, new ResourceProxyHandler(handler));
        } catch (ResourceException e) {
            handler.handleError(e);
        }
    }

    @Override
    protected String getResourceId(Request request) {
        ResourceName name = request.getResourceNameObject();

        // FIXME - this is a hack
        if (name.size() > 0) {
            return name.leaf();
        } else {
            return name.toString();
        }
    }

    @Override
    public void handleQuery(final ServerContext context, final QueryRequest _request, final QueryResultHandler handler) {
        try {
            // check for a queryId and if so convert it to a queryFilter
            final QueryRequest queryRequest = populateQuery(_request);

            // Create a proxy handler so we can run a transformer on results
            final QueryResultHandler proxy = new QueryResultHandler() {
                @Override
                public void handleError(ResourceException error) {
                    handler.handleError(error);
                }

                @Override
                public boolean handleResource(final Resource resource) {
                    try {
                        return handler.handleResource(destringify(resource, propertiesToStringify));
                    } catch (ResourceException e) {
                        handleError(e);

                        // TODO - verify this is what we want
                        return false;
                    }
                }

                @Override
                public void handleResult(QueryResult result) {
                    handler.handleResult(result);
                }
            };

            super.handleQuery(context, queryRequest, proxy);
        } catch (ResourceException e) {
            handler.handleError(e);
        }
    }

    /**
     * If the request has a queryId translate it to the appropriate queryFilter
     * or queryExpression and place in request.
     *
     * @param request
     *
     * @return A new {@link QueryRequest} with a populated queryFilter or queryExpression
     */
    private QueryRequest populateQuery(final QueryRequest request) throws BadRequestException {
        // We only care if there is a queryId
        if (StringUtils.isBlank(request.getQueryId())) {
            return request;
        }

        final QueryRequest queryRequest = Requests.copyOfQueryRequest(request);

        /*
         * FIXME - we need more configurable queries
         */

        if (queries.containsKey(queryRequest.getQueryId())) {
            final JsonValue queryConfig = queries.get(queryRequest.getQueryId());

            /*
             * Process fields
             */

            final JsonValue fields = queryConfig.get("_fields");

            if (!fields.isNull()) {
                // TODO - Can we do this in JsonValue without asString()?
                queryRequest.addField(fields.asString().split(","));
            }

            /*
             * Process queryFilter
             */

            final String tokenizedFilter = queryConfig.get("_queryFilter").asString();

            final TokenHandler handler = new TokenHandler();
            final List<String> tokens = handler.extractTokens(tokenizedFilter);
            Map<String, String> replacements = new HashMap<>();

            for (String token : tokens) {
                final String param = queryRequest.getAdditionalParameter(token);

                if (param != null) {
                    replacements.put(token, param);
                } else {
                    throw new BadRequestException("Query expected additional parameter " + token);
                }
            }

            final String detokenized = handler.replaceTokensWithValues(tokenizedFilter, replacements);

            queryRequest.setQueryFilter(QueryFilter.valueOf(detokenized));

            return queryRequest;
        } else {
            throw new BadRequestException("Requested query " + request.getQueryId() + " does not exist");
        }
    }
}
