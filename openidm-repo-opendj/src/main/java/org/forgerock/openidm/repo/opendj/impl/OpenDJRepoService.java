package org.forgerock.openidm.repo.opendj.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forgerock.opendj.grizzly.GrizzlyTransportProvider;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.ActionRequest;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.DeleteRequest;
import org.forgerock.json.resource.PatchRequest;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.ReadRequest;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.openidm.config.enhanced.EnhancedConfig;
import org.forgerock.openidm.config.enhanced.JSONEnhancedConfig;
import org.forgerock.openidm.core.ServerConstants;
import org.forgerock.openidm.repo.RepositoryService;
import org.forgerock.openidm.repo.crest.TypeHandler;
import org.forgerock.openidm.router.RouteBuilder;
import org.forgerock.openidm.router.RouteEntry;
import org.forgerock.openidm.router.RouterRegistry;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Repository service implementation using OpenDJ
 * 
 * Currently only servicing requests on managed/user
 */
@Component(name = OpenDJRepoService.PID, immediate=true, policy=ConfigurationPolicy.REQUIRE, enabled=true)
@Service (value = {/*RepositoryService.class, */ RequestHandler.class})
@Properties({
    @Property(name = "service.description", value = "Repository Service using OpenDJ"),
    @Property(name = "service.vendor", value = ServerConstants.SERVER_VENDOR_NAME)/*,
    @Property(name = ServerConstants.ROUTER_PREFIX, value = "/repo/managed/user/*")*/ })
public class OpenDJRepoService implements RepositoryService, RequestHandler {

    final static Logger logger = LoggerFactory.getLogger(OpenDJRepoService.class);
    
    public static final String PID = "org.forgerock.openidm.repo.opendj";

    static OpenDJRepoService bootSvc = null;

    /**
     * The current OpenDJ configuration
     */
    private JsonValue existingConfig;

    /**
     * Used for parsing the configuration
     */
    private EnhancedConfig enhancedConfig = new JSONEnhancedConfig();

    /**
     * Router registry used to register additional routes while we can't support /repo/*.
     *
     * TODO - remove this once we support /repo/*
     */
    @Reference(policy = ReferencePolicy.STATIC)
    protected RouterRegistry routerRegistry;

    /** @see #getTypeHandler(Request) */
    private TypeHandler getTypeHandler(CreateRequest request) {
        return typeHandlers.get("managed/user");
//        return typeHandlers.get(request.getResourceName());
    }

    /** Extract type from request and return the assocaited handler */
    private TypeHandler getTypeHandler(Request request) {
        return typeHandlers.get("managed/user");
//        return typeHandlers.get(request.getResourceNameObject().parent().toString());
    }

    /**
     * Map of pre-configured queryFilters in the form of key: queryId value: tokenized queryFilter.
     *
     * TODO - these should probably be object specific
     */
    private Map<String, JsonValue> configQueries = new HashMap<>();

    /**
     * Map of handlers for each configured type
     */
    private Map<String, TypeHandler> typeHandlers;

    @Activate
    void activate(ComponentContext compContext) throws Exception {
        logger.info("Activating Service with configuration {}", compContext.getProperties());

        try {
            existingConfig = enhancedConfig.getConfigurationAsJson(compContext);
        } catch (RuntimeException ex) {
            logger.warn("Configuration invalid and could not be parsed, can not start OrientDB repository: " 
                    + ex.getMessage(), ex);
            throw ex;
        }

    	// Setup and activate an embedded OpenDJ server if configured
        JsonValue embeddedConfig = existingConfig.get("embeddedConfig");
        if (embeddedConfig != null && !embeddedConfig.isNull()) {
        	logger.info("Setting up embedded OpenDJ server");
//        	if (EmbeddedOpenDJ.isInstalled()) {
//                System.out.println("DB_SETUP_ALD");
//            } else {
//                try {
//                    SetupProgress.setWriter(new OutputStreamWriter(System.out));
//                    EmbeddedOpenDJ.setup(OpenDJConfig.getOdjRoot());
//
//                    // Determine if we are a secondary install
//                    if (EmbeddedOpenDJ.isMultiNode()) {
//                        EmbeddedOpenDJ.setupReplication(OpenDJConfig.getOpenDJSetupMap(),
//                                ExistingServerConfig.getOpenDJSetupMap(OpenDJConfig.getExistingServerUrl(),
//                                		embeddedConfig.get(Constants.USERNAME).asString(),
//                                		embeddedConfig.get(Constants.PASSWORD).asString()));
//                        EmbeddedOpenDJ.registerServer(OpenDJConfig.getHostUrl());
//                    }
//
//                    EmbeddedOpenDJ.shutdownServer();
//                } catch (Exception ex) {
//                    System.err.println("DB_SETUP_FAIL" + ex.getMessage());
//                    System.exit(Constants.EXIT_INSTALL_FAILED);
//                }
//            }
        	
        }
        
        //  Initialize the repo service
        init(existingConfig);

        logger.info("Repository started.");
    }

    @Deactivate
    void deactivate(ComponentContext ctx) {
        Set<Map.Entry<String, TypeHandler>> entries = typeHandlers.entrySet();

        for (Map.Entry<String, TypeHandler> entry : entries) {
            if (entry.getValue() != null && entry.getValue().getRouteEntry() != null) {
                entry.getValue().getRouteEntry().removeRoute();
            }
        }
    }

    void init(JsonValue config) {
//        config.add("providerClassLoader", GrizzlyTransportProvider.class.getClassLoader());
        final ConnectionFactory ldapFactory = Rest2LDAP.configureConnectionFactory(
                config.get("connection").required(),
                "root",
                GrizzlyTransportProvider.class.getClassLoader());

        /*
         * Queries
         */

        JsonValue queries = config.get("queries").required();

        for (String queryId : queries.keys()) {
            final JsonValue queryConfig = queries.get(queryId).required();

            // TODO - improve initial config check.
            //        We should be checking if fields is a csv.
            //        Possibly if _queryFilter is valid syntax?
            queryConfig.get("_queryFilter").required();

            configQueries.put(queryId, queryConfig);
        }

        /*
         * Mappings
         */

        JsonValue mappings = config.get("mappings").required();

        final Map<String, TypeHandler> typeHandlers = new HashMap<>();

        for (String type : mappings.keys()) {
            JsonValue mapping = mappings.get(type);
            TypeHandler handler = new OpenDJTypeHandler(null, ldapFactory, mapping, queries);

            /*
             * Since we cannot simply listen on /repo/* while we do not have all objects mapped/implemented
             * We must register individual routes for each handler.
             */

            final RouteEntry routeEntry = routerRegistry.addRoute(RouteBuilder.newBuilder()
                    .withModeStartsWith()
                    .withTemplate("/repo/" + type)
                    .withRequestHandler(handler)
                    .seal());

            handler.setRouteEntry(routeEntry);

            // TODO - breakup queries
            typeHandlers.put(type, handler);
        }

        this.typeHandlers = typeHandlers;
    }

    @Override
    public Resource create(final CreateRequest request) throws ResourceException {
        return getTypeHandler(request).create(request);
    }

    @Override
    public Resource read(final ReadRequest request) throws ResourceException {
        return getTypeHandler(request).read(request);
    }

    @Override
    public Resource update(final UpdateRequest request) throws ResourceException {
        return getTypeHandler(request).update(request);
    }

    @Override
    public Resource delete(final DeleteRequest request) throws ResourceException {
        return getTypeHandler(request).delete(request);
    }

    @Override
    public List<Resource> query(final QueryRequest request) throws ResourceException {
        return getTypeHandler(request).query(request);
    }

    @Override
    public void handleRead(final ServerContext context, final ReadRequest request, final ResultHandler<Resource> handler) {
        getTypeHandler(request).handleRead(context, request, handler);
    }

    @Override
    public void handleUpdate(ServerContext context, final UpdateRequest request, ResultHandler<Resource> handler) {
        getTypeHandler(request).handleUpdate(context, request, handler);
    }

    @Override
    public void handlePatch(final ServerContext context, final PatchRequest request, final ResultHandler<Resource> handler) {
        getTypeHandler(request).handlePatch(context, request, handler);
    }

    @Override
    public void handleAction(final ServerContext context, final ActionRequest request, final ResultHandler<JsonValue> handler) {
        getTypeHandler(request).handleAction(context, request, handler);
    }

    @Override
    public void handleDelete(ServerContext context, DeleteRequest request, ResultHandler<Resource> handler) {
        getTypeHandler(request).handleDelete(context, request, handler);
    }

    @Override
    public void handleCreate(final ServerContext context, final CreateRequest request, final ResultHandler<Resource> handler) {
        getTypeHandler(request).handleCreate(context, request, handler);
    }

    @Override
    public void handleQuery(final ServerContext context, final QueryRequest request, final QueryResultHandler handler) {
        getTypeHandler(request).handleQuery(context, request, handler);
    }
}
