package org.forgerock.openidm.repo.opendj.impl;

import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;

import com.forgerock.opendj.grizzly.GrizzlyTransportProvider;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.ConfigurationPolicy;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Service;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.QueryFilter;
import org.forgerock.json.resource.QueryRequest;
import org.forgerock.json.resource.QueryResultHandler;
import org.forgerock.json.resource.Request;
import org.forgerock.json.resource.RequestHandler;
import org.forgerock.json.resource.ServerContext;
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
    @Property(name = ServerConstants.ROUTER_PREFIX, value = "/repo/managed/user") })
public class OpenDJRepoService extends CRESTRepoService {

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

    private Map<String, JsonValue> configuredQueries = new HashMap<>();

    @Override
    protected String getResourceId(Request request) {
        // TODO - calculate the resource id based on the request
        return null;
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

        // FIXME - we probably need to store these as strings.
        //         We can then tokenize and make QueryFilter on every request depending on fields
        for (String queryId : queries.keys()) {
            String queryFilter = queries.get(queryId).asString();

            queryFilters.put(queryId, QueryFilter.valueOf(queryFilter));
        }

        JsonValue mappings = config.get("mappings").required();

        // FIXME - this needs to be broken up to support multiple mappings

        JsonValue managedUser = mappings.get("managed/user");
        String baseDn = managedUser.get("baseDN").required().asString();
        CollectionResourceProvider managedUserProvider = Rest2LDAP.builder()
                .ldapConnectionFactory(ldapFactory)
                .baseDN(baseDn)
                .configureMapping(managedUser)
                .build();

        setResourceProvider(managedUserProvider);
    }

    @Override
    public void handleQuery(ServerContext context, QueryRequest request, QueryResultHandler handler) {
        if (request.getQueryFilter() == null) {
            throw new UnsupportedOperationException("OpenDJ repo currently only supports query operations via queryFilter");
        }

        super.handleQuery(context, request, handler);
    }
}
