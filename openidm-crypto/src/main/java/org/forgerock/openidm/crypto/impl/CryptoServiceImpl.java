/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */

// TODO: Expose as a set of resource actions.
package org.forgerock.openidm.crypto.impl;

import java.security.Key;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;

import org.forgerock.json.crypto.JsonCrypto;
import org.forgerock.json.crypto.JsonCryptoException;
import org.forgerock.json.crypto.JsonCryptoTransformer;
import org.forgerock.json.crypto.JsonEncryptor;
import org.forgerock.json.crypto.simple.SimpleDecryptor;
import org.forgerock.json.crypto.simple.SimpleEncryptor;
import org.forgerock.json.fluent.JsonException;
import org.forgerock.json.fluent.JsonTransformer;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.fluent.JsonValueException;
import org.forgerock.json.resource.ResourceException;
import org.forgerock.openidm.core.IdentityServer;
import org.forgerock.openidm.crypto.CryptoService;
import org.forgerock.openidm.crypto.factory.CryptoUpdateService;
import org.forgerock.openidm.crypto.impl.KeyStoreInitializer.InitializedKeyStore;
import org.forgerock.openidm.util.JsonUtil;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cryptography Service
 *
 */
public class CryptoServiceImpl implements CryptoService, CryptoUpdateService {

    /**
     * Setup logging for the {@link CryptoServiceImpl}.
     */
    private static final Logger logger = LoggerFactory.getLogger(CryptoServiceImpl.class);
    private static final String OPENIDM_KEYSTORE_TYPE = "openidm.keystore.type";

    private UpdatableKeyStoreSelector keySelector;

    private final ArrayList<JsonTransformer> decryptionTransformers = new ArrayList<>();

    public void activate(@SuppressWarnings("unused") BundleContext context) {
        logger.debug("Activating cryptography service");
        try {
            final String keystoreType =
                    IdentityServer.getInstance().getProperty(OPENIDM_KEYSTORE_TYPE, KeyStore.getDefaultType());
            final InitializedKeyStore keyStore = KeyStoreInitializerFactory.getKeyStoreInitializer(keystoreType).init();
            keySelector = new UpdatableKeyStoreSelector(keyStore.getKeyStore(), keyStore.getPassword());
            decryptionTransformers.add(new JsonCryptoTransformer(new SimpleDecryptor(keySelector)));
        } catch (final JsonValueException jve) {
            logger.error("Exception when loading CryptoService configuration", jve);
            throw jve;
        } catch (final ResourceException e) {
            logger.error("Exception when loading CryptoService configuration", e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
    
    public void updateKeySelector(KeyStore ks, String password) {
        keySelector.update(ks, password);
        decryptionTransformers.add(new JsonCryptoTransformer(new SimpleDecryptor(keySelector)));
    }

    public void deactivate(@SuppressWarnings("unused") BundleContext context) {
        decryptionTransformers.clear();
        keySelector = null;
        logger.info("CryptoService stopped.");
    }

    @Override
    public JsonEncryptor getEncryptor(String cipher, String alias) throws JsonCryptoException {
        Key key = keySelector.select(alias);
        if (key == null) {
            String msg = "Encryption key " + alias + " not found";
            logger.error(msg);
            throw new JsonCryptoException(msg);
        }
        return new SimpleEncryptor(cipher, key, alias);
    }

    @Override
    public List<JsonTransformer> getDecryptionTransformers() {
        return decryptionTransformers;
    }

    @Override
    public JsonValue encrypt(JsonValue value, String cipher, String alias)
            throws JsonCryptoException, JsonException {
        JsonValue result = null;
        if (value != null) {
            JsonEncryptor encryptor = getEncryptor(cipher, alias);
            result = new JsonCrypto(encryptor.getType(), encryptor.encrypt(value)).toJsonValue();
        }
        return result;
    }

    @Override
    public JsonValue decrypt(JsonValue value) throws JsonException {
        JsonValue result = null;
        if (value != null) {
            result = new JsonValue(value);
            result.getTransformers().addAll(0, getDecryptionTransformers());
            result.applyTransformers();
            result = result.copy();
        }
        return result;
    }

    @Override
    public JsonValue decrypt(String value) throws JsonException {
        JsonValue jsonValue = JsonUtil.parseStringified(value);
        return decrypt(jsonValue);
    }

    @Override
    public JsonValue decryptIfNecessary(JsonValue value) throws JsonException {
        if (value == null) {
            return new JsonValue(null);
        }
        if (value.isNull() || !isEncrypted(value)) {
            return value;
        }
        return decrypt(value);
    }

    @Override
    public JsonValue decryptIfNecessary(String value) throws JsonException {
        JsonValue jsonValue = null;
        if (value != null) {
            jsonValue = JsonUtil.parseStringified(value);
        }
        return decryptIfNecessary(jsonValue);
    }

    @Override
    public boolean isEncrypted(JsonValue value) {
        return JsonCrypto.isJsonCrypto(value);
    }

    @Override
    public boolean isEncrypted(String value) {
        return JsonUtil.isEncrypted(value);
    }

}
