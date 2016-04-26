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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Enumeration;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.forgerock.json.resource.InternalServerErrorException;
import org.forgerock.openidm.cluster.ClusterUtils;
import org.forgerock.openidm.core.IdentityServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialized file based key stores (JKS, JCEKS).
 */
class FileBasedKeyStoreInitializer implements KeyStoreInitializer {

    private static final Logger logger = LoggerFactory.getLogger(FileBasedKeyStoreInitializer.class);
    private static final String OPENIDM_KEYSTORE_PASSWORD = "openidm.keystore.password";
    private static final String OPENIDM_INSTANCE_TYPE = "openidm.instance.type";
    private static final String OPENIDM_KEYSTORE_TYPE = "openidm.keystore.type";
    private static final String OPENIDM_KEYSTORE_PROVIDER = "openidm.keystore.provider";
    private static final String OPENIDM_KEYSTORE_LOCATION = "openidm.keystore.location";
    private static final String OPENIDM_CONFIG_CRYPTO_ALIAS = "openidm.config.crypto.alias";

    @Override
    public InitializedKeyStore init() throws InternalServerErrorException {
        String password = IdentityServer.getInstance().getProperty(OPENIDM_KEYSTORE_PASSWORD);
        if (password != null) { // optional
            String instanceType =
                    IdentityServer.getInstance().getProperty(OPENIDM_INSTANCE_TYPE, ClusterUtils.TYPE_STANDALONE);
            String type = IdentityServer.getInstance().getProperty(OPENIDM_KEYSTORE_TYPE, KeyStore.getDefaultType());
            String provider = IdentityServer.getInstance().getProperty(OPENIDM_KEYSTORE_PROVIDER);
            String location = IdentityServer.getInstance().getProperty(OPENIDM_KEYSTORE_LOCATION);
            String alias = IdentityServer.getInstance().getProperty(OPENIDM_CONFIG_CRYPTO_ALIAS);

            try {
                KeyStore ks =
                        (provider == null || provider.trim().length() == 0 ? KeyStore
                                .getInstance(type) : KeyStore.getInstance(type, provider));
                InputStream in = openStream(location);
                if (null != in) {
                    char[] clearPassword = Main.unfold(password);
                    ks.load(in, clearPassword);
                    if (instanceType.equals(ClusterUtils.TYPE_STANDALONE)
                            || instanceType.equals(ClusterUtils.TYPE_CLUSTERED_FIRST)) {
                        Key key = ks.getKey(alias, clearPassword);
                        if (key == null) {
                            // Initialize the keys
                            logger.debug("Initializing secrety key entry in the keystore");
                            generateDefaultKey(ks, alias, location, clearPassword);
                        }
                    }
                    Enumeration<String> aliases = ks.aliases();
                    while (aliases.hasMoreElements()) {
                        logger.info("Available cryptography key: {}", aliases.nextElement());
                    }
                }
                return new InitializedKeyStore(ks, password);
            } catch (IOException ioe) {
                logger.error("IOException when loading KeyStore file of type: " + type
                        + " provider: " + provider + " location:" + location, ioe);
                throw new InternalServerErrorException("IOException when loading KeyStore file of type: "
                        + type + " provider: " + provider + " location:" + location
                        + " message: " + ioe.getMessage(), ioe);
            } catch (GeneralSecurityException gse) {
                logger.error("GeneralSecurityException when loading KeyStore file", gse);
                throw new InternalServerErrorException(
                        "GeneralSecurityException when loading KeyStore file of type: " + type
                                + " provider: " + provider + " location:" + location
                                + " message: " + gse.getMessage(), gse);
            }
        }
        throw new InternalServerErrorException("No keystore password provided in configuration");
    }

    /**
     * Opens a connection to the specified URI location and returns an input
     * stream with which to read its content. If the URI is not absolute, it is
     * resolved against the root of the local file system. If the specified
     * location is or contains {@code null}, this method returns {@code null}.
     *
     * @param location
     *            the location to open the stream for.
     * @return an input stream for reading the content of the location, or
     *         {@code null} if no location.
     * @throws IOException
     *             if there was exception opening the stream.
     */
    private InputStream openStream(String location) throws IOException {
        InputStream result = null;
        if (location != null) {
            File configFile =
                    IdentityServer.getFileForPath(location, IdentityServer.getInstance()
                            .getInstallLocation());
            if (configFile.exists()) {
                result = new FileInputStream(configFile);
            } else {
                logger.error("ERROR - KeyStore not found under CryptoService#location {}",
                        configFile.getAbsolutePath());
            }
        }
        return result;
    }

    /**
     * Generates a default secret key entry in the keystore.
     *
     * @param ks the keystore
     * @param alias the alias of the secret key
     * @param location the keystore location
     * @param password the keystore password
     * @throws IOException if an error occurs dealing with the keystore file.
     * @throws GeneralSecurityException if unable to create or store the default key.
     */
    private void generateDefaultKey(KeyStore ks, String alias, String location, char[] password)
            throws IOException, GeneralSecurityException {
        SecretKey newKey = KeyGenerator.getInstance("AES").generateKey();
        ks.setEntry(alias, new KeyStore.SecretKeyEntry(newKey), new KeyStore.PasswordProtection(password));
        try (OutputStream out = new FileOutputStream(location)) {
            ks.store(out, password);
        }
    }
}
