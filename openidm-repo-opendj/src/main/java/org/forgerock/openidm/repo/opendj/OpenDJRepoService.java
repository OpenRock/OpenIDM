package org.forgerock.openidm.repo.opendj;

import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.CollectionResourceProvider;
import org.forgerock.json.resource.Request;
import org.forgerock.opendj.rest2ldap.Rest2LDAP;
import org.forgerock.openidm.repo.crest.CRESTRepoService;

public class OpenDJRepoService extends CRESTRepoService {
    public OpenDJRepoService() {
        // TODO - build this properly
        super(getProvider()); //Rest2LDAP calls ...
    }

    private static CollectionResourceProvider getProvider() {
        return null;
    }

    @Override
    String getResourceId(Request request) {
        // TODO - calculate the resource id based on the request
        return null;
    }
}
