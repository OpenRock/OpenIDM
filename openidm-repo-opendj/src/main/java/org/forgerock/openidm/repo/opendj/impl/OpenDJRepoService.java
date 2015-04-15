package org.forgerock.openidm.repo.opendj.impl;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.Request;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.openidm.repo.crest.CRESTRepoService;
import org.forgerock.util.time.TimeService;

public class OpenDJRepoService extends CRESTRepoService {
    @Override
    protected String getResourceId(Request request) {
        // TODO - calculate the resource id based on the request
        return null;
    }

    /**
     * Called when the bundle is stopped. Performs any necessary cleanup tasks.
     */
    void cleanup() { }
}
