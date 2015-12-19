/**
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 ForgeRock AS. All Rights Reserved
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
 *
 */

package org.forgerock.openidm.repo.opendj.embedded;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.forgerock.json.JsonValue;
import org.forgerock.openidm.core.IdentityServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenDJ configuration
 */
public class OpenDJConfig {
	
	final static Logger logger = LoggerFactory.getLogger(ExistingServerConfig.class);
	
    private static Map<String, String> openDJSetupMap; 
    
    private final static String DEFAULT_OPENDJ_ROOT = "opendjdb"; 
    private final static String DEFAULT_SUFFIX = "dc=openidmdb,dc=com";
    private final static String DEFAULT_OPENDJ_ADMIN_PORT = "4444";
    private final static String DEFAULT_OPENDJ_LDAP_PORT =" 60389";
    private final static String DEFAULT_OPENDJ_JMX_PORT = "2689";
    private final static String DEFAULT_OPENDJ_REPL_PORT = "9898";
    private final static String DEFAULT_OPENDJ_DS_MGR_DN = "cn=Directory Manager";
    private final static String DEFAULT_OPENDJ_DS_MGR_PASSWD = "password";
    
    static {
        initialize(new JsonValue(new HashMap<String, Object>()));
    }
    
    static void initialize(JsonValue config) {
    	
        String dbRoot = IdentityServer.getInstance().getInstallLocation().getAbsolutePath() + "/db/opendj/";      
        
        openDJSetupMap = new HashMap<String, String>();
        openDJSetupMap.put(Constants.OPENDJ_ADMIN_PORT, config.get(Constants.OPENDJ_ADMIN_PORT).defaultTo(DEFAULT_OPENDJ_ADMIN_PORT).asString());
        openDJSetupMap.put(Constants.OPENDJ_LDAP_PORT, config.get(Constants.OPENDJ_LDAP_PORT).defaultTo(DEFAULT_OPENDJ_LDAP_PORT).asString());
        openDJSetupMap.put(Constants.OPENDJ_JMX_PORT, config.get(Constants.OPENDJ_JMX_PORT).defaultTo(DEFAULT_OPENDJ_JMX_PORT).asString());
        openDJSetupMap.put(Constants.OPENDJ_REPL_PORT, config.get(Constants.OPENDJ_REPL_PORT).defaultTo(DEFAULT_OPENDJ_REPL_PORT).asString());
        openDJSetupMap.put(Constants.OPENDJ_DS_MGR_DN, config.get(Constants.OPENDJ_DS_MGR_DN).defaultTo(DEFAULT_OPENDJ_DS_MGR_DN).asString());
        openDJSetupMap.put(Constants.OPENDJ_DS_MGR_PASSWD, config.get(Constants.OPENDJ_DS_MGR_PASSWD).defaultTo(DEFAULT_OPENDJ_DS_MGR_PASSWD).asString());
        openDJSetupMap.put(Constants.OPENDJ_ROOT, dbRoot + config.get(Constants.OPENDJ_ROOT).defaultTo(DEFAULT_OPENDJ_ROOT).asString());
        openDJSetupMap.put(Constants.OPENDJ_SUFFIX, config.get(Constants.OPENDJ_SUFFIX).defaultTo(DEFAULT_SUFFIX).asString());
        
        String url = config.get(Constants.EXISTING_SERVER_URL).asString();
        
        if (url != null && url.length() > 0) {
            try {
                URL existingServerUrl = new URL(url);
                openDJSetupMap.put(Constants.EXISTING_SERVER_URL, existingServerUrl.toString());
            } catch (MalformedURLException mue) {
                System.err.println("DB_SETUP_URL " + Constants.EXISTING_SERVER_URL + " " + url);
                System.exit(Constants.EXIT_INVALID_URL);
            }
        }
        
        url = config.get(Constants.HOST_URL).asString();
        
        if (url != null && url.length() > 0) {
            try {
                URL hostUrl = new URL(url);
                openDJSetupMap.put(Constants.HOST_URL, url);
                openDJSetupMap.put(Constants.HOST_PROTOCOL, hostUrl.getProtocol());
                openDJSetupMap.put(Constants.HOST_FQDN, hostUrl.getHost());
                openDJSetupMap.put(Constants.HOST_PORT, Integer.toString(hostUrl.getPort()));
                openDJSetupMap.put(Constants.HOST_URI, hostUrl.getPath());
            } catch (MalformedURLException mue) {
                System.err.println("DB_SETUP_URL " + Constants.EXISTING_SERVER_URL + " " + url);
                System.exit(Constants.EXIT_INVALID_URL);
            }
        }
    }
    
    public static Map<String, String> getOpenDJSetupMap() {
        return openDJSetupMap;
    }
    
    public static String getHostUrl() {
        return openDJSetupMap.get(Constants.HOST_URL);
    }
    
    public static String getOdjRoot() {
        return openDJSetupMap.get(Constants.OPENDJ_ROOT);
    }
    
    public static String getSessionDBSuffix() {
        return openDJSetupMap.get(Constants.OPENDJ_SUFFIX);
    }
    
    public static String getExistingServerUrl() {
        return openDJSetupMap.get(Constants.EXISTING_SERVER_URL);
    }
    
    public static boolean canUseAsPort(String hostname, int port) {
        boolean canUseAsPort = false;
        ServerSocket serverSocket = null;
        
        try {
            InetSocketAddress socketAddress =
                new InetSocketAddress(hostname, port);
            serverSocket = new ServerSocket();
            serverSocket.bind(socketAddress);
            canUseAsPort = true;
     
            serverSocket.close();
       
            Socket s = null;
            
            try {
              s = new Socket();
              s.connect(socketAddress, 1000);
              canUseAsPort = false;
       
            } catch (Throwable t) {
            } finally {
              if (s != null) {
                try {
                  s.close();
                } catch (Throwable t) {
                }
              }
            }
     
     
        } catch (IOException ioe) {
            canUseAsPort = false;
        } catch (NullPointerException npe) {      
            canUseAsPort = false;  
        } finally {
            try {
                if (serverSocket != null) {
                    serverSocket.close();
                }
            } catch (Exception ex) { }
        }
     
        return canUseAsPort;
    }
    
    enum AmSessionDbAttr {
        ADMIN_PORT("adminPort"), LDAP_PORT("ldapPort"), JMX_PORT("jmxPort"), REPL_PORT("replPort");
        
        private final String text;
        
        private AmSessionDbAttr(String text) {
            this.text = text;
        }
        
        @Override public String toString() {
            return text;
        }
    }
}
