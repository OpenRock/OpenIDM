package org.forgerock.openidm.repo.opendj.impl;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.forgerock.opendj.grizzly.GrizzlyTransportProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.BadRequestException;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.CreateRequest;
import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.QueryFilter;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.Requests;
import org.forgerock.json.resource.Resource;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.json.resource.ResultHandler;
import org.forgerock.json.resource.ServerContext;
import org.forgerock.json.resource.UpdateRequest;
import org.forgerock.opendj.ldap.ConnectionFactory;
import org.forgerock.opendj.ldap.spi.TransportProvider;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.openidm.config.enhanced.EnhancedConfig;
import org.forgerock.openidm.config.enhanced.JSONEnhancedConfig;
import org.forgerock.openidm.core.ServerConstants;
import org.forgerock.openidm.repo.crest.CRESTRepoService;
import org.forgerock.openidm.repo.opendj.embedded.Constants;
import org.forgerock.openidm.repo.opendj.embedded.EmbeddedOpenDJ;
import org.forgerock.openidm.repo.opendj.embedded.ExistingServerConfig;
import org.forgerock.openidm.repo.opendj.embedded.OpenDJConfig;
import org.forgerock.openidm.repo.opendj.embedded.SetupProgress;
import org.forgerock.openidm.repo.util.TokenHandler;
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
    @Property(name = "service.vendor", value = ServerConstants.SERVER_VENDOR_NAME),
    @Property(name = ServerConstants.ROUTER_PREFIX, value = "/repo/managed/*") })
public class OpenDJRepoService extends CRESTRepoService {

    final static Logger logger = LoggerFactory.getLogger(OpenDJRepoService.class);
    
    public static final String PID = "org.forgerock.openidm.repo.opendj";

    static OpenDJRepoService bootSvc = null;

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * The current OpenDJ configuration
     */
    private JsonValue existingConfig;

    /**
     * Used for parsing the configuration
     */
    private EnhancedConfig enhancedConfig = new JSONEnhancedConfig();

    class TypeContext {
        private final JsonValue config;

        /**
         * List of properties that should be stored as JSON strings.
         * This can be useful for repositories that do not support fully-unstructured
         * JSON documents such as OpenDJ.
         */
        private Set<String> stringProperties;

        TypeContext(JsonValue config, Set<String> stringProperties) {
            this.config = config;
            this.stringProperties = stringProperties;
        }

        public Set<String> getStringProperties() {
            return stringProperties;
        }

        public void setStringProperties(Set<String> stringProperties) {
            this.stringProperties = stringProperties;
        }
    }

    private Set<String> propertiesToStringify = new HashSet<>();


    /**
     * Map of pre-configured queryFilters in the form of key: queryId value: tokenized queryFilter.
     *
     * TODO - these should probably be object specific
     */
    private Map<String, JsonValue> configQueries = new HashMap<>();

    @Override
    protected String getResourceId(Request request) {
        // TODO - This needs to be updated when we support multiple object types
        return request.getResourceNameObject().leaf();
    }

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

    void init(JsonValue config) {
//        config.add("providerClassLoader", GrizzlyTransportProvider.class.getClassLoader());
        final ConnectionFactory ldapFactory = Rest2LDAP.configureConnectionFactory(
                config.get("connection").required(),
                "root",
                GrizzlyTransportProvider.class.getClassLoader());

        JsonValue queries = config.get("queries").required();

        for (String queryId : queries.keys()) {
            final JsonValue queryConfig = queries.get(queryId).required();

            // TODO - improve initial config check.
            //        We should be checking if fields is a csv.
            //        Possibly if _queryFilter is valid syntax?
            queryConfig.get("_queryFilter").required();

            configQueries.put(queryId, queryConfig);
        }

        JsonValue mappings = config.get("mappings").required();

        // FIXME - this needs to be broken up to support multiple mappings

        JsonValue managedUser = mappings.get("managed/user");
        JsonValue rest2ldapConfig = managedUser.get("rest2ldapConfig");
        propertiesToStringify = managedUser.get("propertiesToStringify").asSet(String.class);
        String baseDn = rest2ldapConfig.get("baseDN").required().asString();
        CollectionResourceProvider managedUserProvider = Rest2LDAP.builder()
                .ldapConnectionFactory(ldapFactory)
                .baseDN(baseDn)
                .configureMapping(rest2ldapConfig)
                .build();

        setResourceProvider(managedUserProvider);
    }

    @Override
    public void handleUpdate(ServerContext context, UpdateRequest request, ResultHandler<Resource> handler) {
        UpdateRequest updateRequest = Requests.copyOfUpdateRequest(request);

        try {
            updateRequest.setContent(stringifyProperties(request.getContent(), propertiesToStringify));
        } catch (ResourceException e) {
            handler.handleError(e);
        }

        super.handleUpdate(context, request, handler);
    }

    @Override
    public void handleCreate(final ServerContext context, final CreateRequest _request, final ResultHandler<Resource> handler) {
        final CreateRequest createRequest = Requests.copyOfCreateRequest(_request);

        try {
            Map<String, Object> obj = stringifyProperties(createRequest.getContent(),
                    propertiesToStringify).asMap();

            // Set id to a new UUID if none is specified (_action=create)
            if (StringUtils.isBlank(createRequest.getNewResourceId())) {
                createRequest.setNewResourceId(UUID.randomUUID().toString());
            }

            obj.put("_id", createRequest.getNewResourceId());

            createRequest.setContent(new JsonValue(obj));

            super.handleCreate(context, createRequest, handler);
        } catch (ResourceException e) {
            handler.handleError(e);
        }
    }

    @Override
    public void handleQuery(ServerContext context, QueryRequest request, QueryResultHandler handler) {
        try {
            // check for a queryId and if so convert it to a queryFilter
            QueryRequest queryRequest = populateQuery(request);

            super.handleQuery(context, queryRequest, handler);
        } catch (ResourceException e) {
            handler.handleError(e);
        }
    }

    private JsonValue unStringifyProperties(JsonValue object, Set<String> props) throws ResourceException {
        Map<String, Object> obj = object.asMap();

        ObjectMapper mapper = new ObjectMapper();
        final TypeReference<LinkedHashMap<String,Object>> typeRef = new TypeReference<LinkedHashMap<String,Object>>() {};

        try {
            for (String key : props) {
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

    private JsonValue stringifyProperties(JsonValue object, Set<String> props) throws ResourceException {
        Map<String, Object> obj = object.asMap();

        try {
            for (String property : propertiesToStringify) {
                Object val = obj.get(property);

                if (val != null) {
                    obj.put(property, mapper.writeValueAsString(val));
                }
            }
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to convert password object to String", e);
        }

        return new JsonValue(obj);
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

        if (configQueries.containsKey(queryRequest.getQueryId())) {
            final JsonValue queryConfig = configQueries.get(queryRequest.getQueryId());

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

                if (StringUtils.isBlank(param)) {
                    throw new BadRequestException("Query expected additional parameter " + token);
                } else {
                    replacements.put(token, param);
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
