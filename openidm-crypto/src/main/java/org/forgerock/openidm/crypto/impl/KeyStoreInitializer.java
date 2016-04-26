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

import java.security.KeyStore;

import org.forgerock.json.resource.ResourceException;

/**
 * An interface to initialize various keystore types.
 */
interface KeyStoreInitializer {

    /**
     * Initializes a keystore.
     * @return A {@link InitializedKeyStore}.
     * @throws ResourceException If unable to initialize a keystore.
     */
    InitializedKeyStore init() throws ResourceException;

    /**
     * A contains an initialized keystore and password.
     */
    class InitializedKeyStore {
        private KeyStore keyStore;
        private String password;

        /**
         * Constructs a InitializedKeyStore given a {@link KeyStore} and a password.
         * @param keyStore The {@link KeyStore}.
         * @param password The password.
         */
        InitializedKeyStore(final KeyStore keyStore, final String password){
            this.keyStore = keyStore;
            this.password = password;
        }

        /**
         * Gets the initialized {@link KeyStore}.
         * @return A {@link KeyStore}.
         */
        KeyStore getKeyStore() {
            return keyStore;
        }

        /**
         * Gets the key store password.
         * @return The key store password.
         */
        String getPassword() {
            return password;
        }
    }
}
