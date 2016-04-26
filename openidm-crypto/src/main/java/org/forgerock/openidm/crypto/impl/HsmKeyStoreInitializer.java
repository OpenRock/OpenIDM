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
 * Copyright 2016 ForgeRock AS.
 */

package org.forgerock.openidm.crypto.impl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;

import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openidm.core.IdentityServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.security.pkcs11.SunPKCS11;

class HsmKeyStoreInitializer implements KeyStoreInitializer {

    private static final Logger logger = LoggerFactory.getLogger(HsmKeyStoreInitializer.class);
    private static final String PKCS11_CONFIG = "openidm.security.pkcs11.config";
    private static final String KEYSTORE_PASSWORD = "openidm.keystore.password";
    private static final String PKCS_11 = "PKCS11";

    @Override
    public InitializedKeyStore init() throws ResourceException {
        final String config = IdentityServer.getInstance().getProperty(PKCS11_CONFIG);
        if (config != null) {
            Security.addProvider(new SunPKCS11(config));
            try {
                KeyStore ks = KeyStore.getInstance(PKCS_11);
                String password = IdentityServer.getInstance().getProperty(KEYSTORE_PASSWORD);
                ks.load(null, password.toCharArray());
                return new InitializedKeyStore(ks, password);
            } catch (final KeyStoreException|IOException|NoSuchAlgorithmException|CertificateException e) {
                final String error = "Unable to load pkcs11 keystore";
                logger.error(error, e);
                throw new InternalServerErrorException(error, e);
            }
        } else {
            throw new InternalServerErrorException("No pkcs11 config file provided");
        }
    }
}
