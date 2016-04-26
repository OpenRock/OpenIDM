/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock AS. All Rights Reserved
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

package org.forgerock.openidm.security.impl;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.forgerock.openidm.security.KeyStoreHandler;

/**
 * A NAME does ...
 */
public class JcaKeyStoreHandler implements KeyStoreHandler {

    private static final String NONE = "none";

    private String location;
    private String password;
    private String type;
    private KeyStore store;
    private boolean isPkcs11;

    public JcaKeyStoreHandler(String type, String location, String password) throws Exception {
        this.location = location;
        this.password = password;
        this.type = type;
        this.isPkcs11 = isPkcs11();
        init();
    }

    void init() throws IOException, KeyStoreException, CertificateException,
            NoSuchAlgorithmException {
        if (isPkcs11) {
            store = KeyStore.getInstance(type);
            store.load(null, password.toCharArray());
        } else {
            try(InputStream in = new FileInputStream(location)){
                store = KeyStore.getInstance(type);
                store.load(in, password.toCharArray());
            }
        }
    }

    @Override
    public KeyStore getStore() {
        return store;
    }

    @Override
    public void setStore(KeyStore keystore) throws Exception {
        store = keystore;
        store();
    }

    @Override
    public void store() throws Exception {
        if (isPkcs11) {
            store.store(null, password.toCharArray());
        } else {
            try(OutputStream out = new FileOutputStream(location)){
                store.store(out, password.toCharArray());
            }
        }
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getLocation() {
        return location;
    }

    public String getType() {
        return type;
    }

    private boolean isPkcs11() {
        final String[] parts = location.split("/");
        if (parts != null) {
            return NONE.equalsIgnoreCase(parts[parts.length-1]);
        }
        return false;
    }
}
