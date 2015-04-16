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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.opendj.ldap.DereferenceAliasesPolicy;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.SearchScope;
import org.forgerock.openidm.core.IdentityServer;
import org.opends.server.core.AddOperation;
import org.opends.server.core.DeleteOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.extensions.ConfigFileHandler;
import org.opends.server.protocols.internal.InternalClientConnection;
import org.opends.server.protocols.internal.InternalSearchOperation;
import org.opends.server.protocols.internal.Requests;
import org.opends.server.protocols.internal.SearchRequest;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.tools.InstallDS;
import org.opends.server.tools.dsreplication.ReplicationCliMain;
import org.opends.server.types.Attribute;
import org.opends.server.types.DN;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.SearchFilter;
import org.opends.server.types.SearchResultEntry;
import org.opends.server.util.EmbeddedUtils;
import org.opends.server.util.ServerConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controls the installation and setup of an embedded OpenDJ server.
 */
public class EmbeddedOpenDJ {    
	
    final static Logger logger = LoggerFactory.getLogger(EmbeddedOpenDJ.class);
    
    private static boolean serverStarted = false;
    private static List<LDAPAttribute> objectClasses;
    private static final String replDN = "cn=all-servers,cn=Server Groups,cn=admin data";
    private static LinkedHashSet<String> serverAttrs;
    
    static {
        initialize();
    }
    
    static void initialize() {
        List<String> valueList = new ArrayList<String>();
        valueList.add(Constants.TOP);
        //valueList.add(Constants.FR_AMSESSIONDB);
        LDAPAttribute ldapAttr= new LDAPAttribute(Constants.OBJECTCLASS, valueList);
        objectClasses = new ArrayList<LDAPAttribute>();
        objectClasses.add(ldapAttr);
        
        serverAttrs = new LinkedHashSet<String>();
        serverAttrs.add("cn");
        serverAttrs.add("jmxPort");
        serverAttrs.add("adminPort");
        serverAttrs.add("ldapPort");
        serverAttrs.add("replPort");
    }
    
    /**
     * Returns <code>true</code> if the server has already been started.
     *
     * @return <code>true</code> if the server has already been started.
     */ 
    public static boolean isStarted() {
        return serverStarted;
    }
    
    /**
     * Are we setting a secondary node that needs to replicate with the first?
     * 
     * @return True if we have an existing server id
     */
    public static boolean isMultiNode() {
        String replFlag = OpenDJConfig.getOpenDJSetupMap().get(Constants.EXISTING_SERVER_URL);
        
        if (replFlag != null) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Returns true if OpenDJ is configured
     * 
     * @return <code>true</code> if the server has already been installed.
     */
    public static boolean isInstalled() {
        File dbDir = new File(OpenDJConfig.getOdjRoot() + "/db/userRoot");
        
        return dbDir.exists() && dbDir.isDirectory();
    }
    
    /**
     * Sets up embedded OpenDJ during initial installation :
     * <ul>
     * <li>lays out the filesystem directory structure needed by OpenDJ
     * <li>sets up port numbers for ldap and replication
     * <li>invokes <code>EmbeddedUtils</code> to start the embedded server.
     * </ul>
     *
     *  @param odj the root directory where opendj will be installed.
     *  @param servletCtx Servlet Context to read deployed war contents.
     *  @throws Exception on encountering errors.
     */
    public static void setup(String odjRoot) throws Exception {
        new File(odjRoot).mkdir();

        String opendjDir = IdentityServer.getInstance().getInstallLocation().getAbsolutePath() + "/db/opendj";
        
        logger.debug("DB_DJ_START");
        ZipFile opendjZip = new ZipFile(opendjDir + "/openDJ-2.6.0.zip");
        Enumeration files = opendjZip.entries();

        while (files.hasMoreElements()) {
            ZipEntry file = (ZipEntry) files.nextElement();
            File f = new File(odjRoot + "/" + file.getName());

            if (file.isDirectory()) {
                f.mkdir();
                continue;
            }

            BufferedInputStream is =
                    new BufferedInputStream(opendjZip.getInputStream(file), 10000);
            BufferedOutputStream fos =
                    new BufferedOutputStream(new java.io.FileOutputStream(f), 10000);

            try {
                while (is.available() > 0) {
                    fos.write(is.read());
                }
            } catch (IOException ioe) {
                logger.error("DB_DJ_ZIP_ERR", ioe);
                throw ioe;
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (Exception ex) {
                        //No handling requried
                    }
                }

                if (fos != null) {
                    try {
                        fos.close();
                    } catch (Exception ex) {
                        //No handling requried
                    }
                }
            }

            if (file.getName().endsWith("sh") || file.getName().startsWith("bin")) {
                f.setExecutable(true);
            }
        }
        
        // create tag swapped files
        /*String[] tagSwapFiles = {
            "../ldif/amsessiondb_suffix.ldif.template"
        };*/

        /*for (int i = 0 ; i < tagSwapFiles.length; i++) {
            String fileIn = tagSwapFiles[i];
            FileReader fin = new FileReader(fileIn);

            StringBuilder sbuf = new StringBuilder();
            char[] cbuf = new char[1024];
            int len;
            
            while ((len = fin.read(cbuf)) > 0) {
                sbuf.append(cbuf, 0, len);
            }
            
            FileWriter fout = null;

            try {
                fout = new FileWriter(odjRoot + "/" +
                        tagSwapFiles[i].substring(0, tagSwapFiles[i].indexOf(".template")));
                String inpStr = sbuf.toString();
                fout.write(tagSwap(inpStr));
            } catch (IOException ioe) {
                logger.error("DB_DJ_SWP_ERR", ioe);
                throw ioe;
            } finally {
                if (fin != null) {
                    try {
                        fin.close();
                    } catch (Exception ex) {
                        //No handling required
                    }
                }
                if (fout != null) {
                    try {
                        fout.close();
                    } catch (Exception ex) {
                        //No handling required
                    }
                }
            }
        }*/
        
        // copy schema files
        String[] opendjSchemaFiles = {
            "config.ldif"
        };

        for (int i = 0 ; i < opendjSchemaFiles.length; i++) {
            String jarFileName = opendjDir + "/ldif/" + opendjSchemaFiles[i];
            ReadableByteChannel inChannel =
                    Channels.newChannel(new FileInputStream(jarFileName));
            FileChannel outChannel = new FileOutputStream(odjRoot + "/config/schema/" + opendjSchemaFiles[i]).getChannel();

            try {
                channelCopy(inChannel, outChannel);
            } catch (IOException ioe) {
                logger.error("DB_DJ_SCH_ERR", ioe);
                throw ioe;
            } finally {
                if (inChannel != null) {
                    try {
                        inChannel.close();
                    } catch (Exception ex) {
                        //No handling requried
                    }
                }

                if (outChannel != null) {
                    try {
                        outChannel.close();
                    } catch (Exception ex) {
                        //No handling requried
                    }
                }
            }
        }

        // now setup OpenDJ
        System.setProperty("org.opends.quicksetup.Root", odjRoot);
        System.setProperty(ServerConstants.PROPERTY_SERVER_ROOT, odjRoot);
        System.setProperty(ServerConstants.PROPERTY_INSTANCE_ROOT, odjRoot);
        EmbeddedOpenDJ.setupOpenDS(odjRoot + "/config/config.ldif", OpenDJConfig.getOpenDJSetupMap());

        Object[] params = { odjRoot };
        EmbeddedOpenDJ.startServer(odjRoot);

        // Check: If adding a new server to a existing cluster
       /*if (OpenDJConfig.getExistingServerUrl() == null) {
            // Default: single / first server.
            logger.debug("DB_DJ_CONF_FIR");
            int ret = EmbeddedOpenDJ.loadLDIF(OpenDJConfig.getOpenDJSetupMap(), odjRoot, "../ldif/amsessiondb_suffix.ldif");
            
            if (ret == 0) {
                logger.debug("DB_DJ_SUF_OK");
            } else {
                logger.error("DB_DJ_SUF_FAIL " + ret);
                throw new StoreException("DB_DJ_SUF_FAIL " + ret);
            }
            
            registerServer(OpenDJConfig.getHostUrl());
        }*/
    }
    
    /*private static String tagSwap(String inpStr) {
        inpStr = inpStr.replaceAll("@" + Constants.OPENDJ_SUFFIX_TAG + "@", OpenDJConfig.getSessionDBSuffix());
        inpStr = inpStr.replaceAll("@" + Constants.OPENDJ_RDN_TAG + "@", calculateRDNValue(OpenDJConfig.getSessionDBSuffix()));
        
        return inpStr;
    }*/
    
    /*private static String calculateRDNValue(String dn) {
        if (dn.indexOf(',') == -1) {
            if (dn.indexOf('=') == -1) {
                return dn;
            } else {
                return dn.substring(dn.indexOf('=') + 1);
            }
        }
        
        if (dn.indexOf('=') == -1) {
            return dn.substring(0, dn.indexOf(','));
        } else {
            return dn.substring(dn.indexOf('=') + 1, dn.indexOf(','));
        }
        
    }*/
    
    protected static void channelCopy(ReadableByteChannel from, WritableByteChannel to)
    throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);

        while (from.read(buffer) != -1) {
            buffer.flip();
            to.write(buffer);
            buffer.compact();
        }

        buffer.flip();

        while (buffer.hasRemaining()) {
            to.write(buffer);
        }
    }
    
    /**
     *  Utility function to preload data in the embedded instance.
     *  Must be called when the directory instance is shutdown.
     *
     *  @param odsRoot Local directory where <code>OpenDS</code> is installed.
     *  @param ldif Full path of the ldif file to be loaded.
     *
     */
    public static int loadLDIF(Map map, String odjRoot, String ldif) {
        int ret = 0;

        try {
            logger.debug("ldif file path: " + ldif);

            String[] args1 = 
            { 
                "-C",                                               // 0
                "org.opends.server.extensions.ConfigFileHandler",   // 1
                "-f",                                               // 2
                odjRoot + "/config/config.ldif",                    // 3
                "-n",                                               // 4
                "userRoot",                                         // 5
                "-l",                                               // 6
                ldif,                                               // 7
                "-Q",                                               // 8
                "--trustAll",                                       // 9
                "-D",                                               // 10
                "cn=Directory Manager",                             // 11
                "-w",                                               // 12
                "password"                                          // 13
            };
            args1[11] = (String) map.get(Constants.OPENDJ_DS_MGR_DN);
            args1[13] = (String) map.get(Constants.OPENDJ_DS_MGR_PASSWD);
            ret = org.opends.server.tools.ImportLDIF.mainImportLDIF(args1, false,
                    SetupProgress.getOutputStream(), SetupProgress.getOutputStream());

            logger.debug("DB_DJ_LD_OK");
        } catch (Exception ex) {
            logger.error("DB_DJ_LD_FAIL", ex);
        }

        return ret;
    }
    
    /**
     * Runs the OpenDJ setup command to create our instance
     *
     * @param configFile path to config.ldif
     * @param map The map of configuration options
     * @throws Exception upon encountering errors.
     */
    public static void setupOpenDS(String configFile, Map<String, String> map)
    throws Exception {
        logger.debug("DB_DJ_SETUP");

        int ret = runOpenDSSetup(map, configFile);

        if (ret == 0) {
            logger.debug("DB_DJ_SETUP_OK");
        } else {
            logger.error("DB_DJ_SETUP_FAIL " + ret);
            throw new StoreException("DB_DJ_SETUP_FAIL " + ret);
        }
    }
    
     /**
      * Runs the OpenDJ setup command like this:
      * $ ./setup --cli --adminConnectorPort 4444
      * --baseDN dc=opensso,dc=java,dc=net --rootUserDN "cn=directory manager"
      * --doNotStart --ldapPort 50389 --skipPortCheck --rootUserPassword xxxxxxx
      * --jmxPort 1689 --no-prompt
      *
      *  @param map Map of properties collected by the configurator.
      *  @return status : 0 == success, !0 == failure
      */
    public static int runOpenDSSetup(Map<String, String> map, String configFile) {
        String[] setupCmd= {
            "--cli",                        // 0
            "--adminConnectorPort",         // 1
            "5444",                         // 2
            "--baseDN",                     // 3
            "o=amsessiondb",                // 4
            "--rootUserDN",                 // 5
            "cn=Directory Manager",         // 6
            "--ldapPort",                   // 7
            "60389",                        // 8
            "--skipPortCheck",              // 9
            "--rootUserPassword",           // 10
            "xxxxxxx",                      // 11
            "--jmxPort",                    // 12
            "2689",                         // 13
            "--no-prompt",                  // 14
            "--configFile",                 // 15
            "/path/to/config.ldif",         // 16
            "--doNotStart",                 // 17
            "--hostname",                   // 18
            "hostname"                      // 19
        };

        setupCmd[2] = map.get(Constants.OPENDJ_ADMIN_PORT);
        setupCmd[4] = OpenDJConfig.getSessionDBSuffix();
        setupCmd[6] = map.get(Constants.OPENDJ_DS_MGR_DN);
        setupCmd[8] = map.get(Constants.OPENDJ_LDAP_PORT);
        setupCmd[13] = map.get(Constants.OPENDJ_JMX_PORT);
        setupCmd[16] = configFile;
        setupCmd[19] = map.get(Constants.HOST_FQDN);

        logger.debug("DB_DJ_SETUP_RUN " + setupCmd);

        setupCmd[11] = map.get(Constants.OPENDJ_DS_MGR_PASSWD);

        int ret = InstallDS.mainCLI(
            setupCmd,
            SetupProgress.getOutputStream(),
            SetupProgress.getOutputStream(),
            null);

        if (ret == 0) {
            logger.debug("DB_DJ_SETUP_FIN");
        } else {
            logger.warn("DB_DJ_SETUP_FAIL2");
        }

        return ret;
    }
    
    public static void registerServer(String serverUrl) 
    throws StoreException {
        InternalClientConnection icConn = InternalClientConnection.getRootConnection();
        
        List<RawAttribute> attrList = createLocalServerEntry();
        StringBuilder dn = new StringBuilder();
        dn.append(Constants.HOST_NAMING_ATTR).append(Constants.EQUALS).append(serverUrl);
        dn.append(Constants.COMMA).append(Constants.HOSTS_BASE_DN);
        dn.append(Constants.COMMA).append(OpenDJConfig.getSessionDBSuffix());
        
        attrList.addAll(objectClasses);
        AddOperation ao = icConn.processAdd(dn.toString(), attrList);
        ResultCode resultCode = ao.getResultCode();
        
        if (resultCode == ResultCode.SUCCESS) {
            logger.debug("DB_SVR_CREATE " + dn);
        } else if (resultCode == ResultCode.ENTRY_ALREADY_EXISTS) {
            logger.warn("DB_SVR_CRE_FAIL " + dn);
        } else {
            final String message = "DB_SVR_CRE_FAIL2 " + dn + " " + resultCode.toString();
            logger.warn(message);
            throw new StoreException(message);
        }
    }
    
    public static Set<OpenDJServer> getServers() 
    throws StoreException {
        InternalClientConnection icConn = InternalClientConnection.getRootConnection();
        Set<OpenDJServer> serverList = new HashSet<OpenDJServer>();
        StringBuilder baseDn = new StringBuilder();
        baseDn.append(Constants.HOSTS_BASE_DN);
        baseDn.append(Constants.COMMA).append(OpenDJConfig.getSessionDBSuffix());
        
        try {
        	SearchRequest searchRequest = Requests.newSearchRequest(
        			DN.valueOf(baseDn.toString()), 
        			SearchScope.SINGLE_LEVEL, 
        			SearchFilter.createFilterFromString("objectclass=*"))
        				.setSizeLimit(0)
        				.setTimeLimit(0)
        				.setTypesOnly(false)
        				.setDereferenceAliasesPolicy(DereferenceAliasesPolicy.NEVER)
        				.addAttribute(serverAttrs);
            InternalSearchOperation iso = icConn.processSearch(searchRequest);
            ResultCode resultCode = iso.getResultCode();

            if (resultCode == ResultCode.SUCCESS) {
                LinkedList<SearchResultEntry> searchResult = iso.getSearchEntries();
                
                if (!searchResult.isEmpty()) {
                    for (SearchResultEntry entry : searchResult) {
                        List<Attribute> attributes = entry.getAttributes();
                        OpenDJServer server = new OpenDJServer();
                        
                        for (Attribute attribute : attributes) {
                            if (attribute.getName().equals("cn")) {
                                server.setHostName(getFQDN(attribute.iterator().next().toString()));
                            } else if (attribute.getName().equals("adminPort")) {
                                server.setAdminPort(attribute.iterator().next().toString());
                            } else if (attribute.getName().equals("jmxPort")) {
                                server.setJmxPort(attribute.iterator().next().toString());
                            } else if (attribute.getName().equals("ldapPort")) {
                                server.setLdapPort(attribute.iterator().next().toString());
                            } else if (attribute.getName().equals("replPort")) {
                                server.setReplPort(attribute.iterator().next().toString());
                            } else {
                                logger.warn("DB_UNK_ATTR " + attribute.getName());
                            }
                        }
                        
                        serverList.add(server);
                    }
                } 
            } else if (resultCode == ResultCode.NO_SUCH_OBJECT) {
                logger.debug("DB_ENT_NOT_P " + baseDn);
                
                return null;
            } else {
                final String message = "DB_ENT_ACC_FAIL " + baseDn + " " + resultCode.toString();
                logger.warn(message);
                throw new StoreException(message);
            }
        } catch (DirectoryException dex) {
            logger.warn("DB_ENT_ACC_FAIL2 " + baseDn);
            throw new StoreException("DB_ENT_ACC_FAIL2 " + baseDn, dex);
        }
        
        return serverList;
    }
    
    private static String getFQDN(String urlHost) {
        URL url = null;
        
        try {
            url = new URL(urlHost);
        } catch (MalformedURLException mue) {
            logger.warn("DB_MAL_URL " + urlHost);
            return urlHost;
        }
        
        return url.getHost();
    }
    
    private static List<RawAttribute> createLocalServerEntry() {
        Map<String, String> odjSetupMap = OpenDJConfig.getOpenDJSetupMap();
        
        List<RawAttribute> attrList = new ArrayList<RawAttribute>(odjSetupMap.size());
        
        for (Map.Entry<String, String> entry : odjSetupMap.entrySet()) {
            if (entry.getKey().equals(Constants.OPENDJ_ADMIN_PORT) ||
                entry.getKey().equals(Constants.OPENDJ_LDAP_PORT) ||
                entry.getKey().equals(Constants.OPENDJ_JMX_PORT) ||
                entry.getKey().equals(Constants.OPENDJ_REPL_PORT)) {
                List<String> valueList = new ArrayList<String>();
                valueList.add(entry.getValue());
                attrList.add(new LDAPAttribute(getAttrName(entry.getKey()), valueList));
            }
        }
        
        return attrList;
    }
    
    private static String getAttrName(String key) {
        if (key.equals(Constants.OPENDJ_ADMIN_PORT)) {
            return OpenDJConfig.AmSessionDbAttr.ADMIN_PORT.toString();
        } else if (key.equals(Constants.OPENDJ_LDAP_PORT)) {
            return OpenDJConfig.AmSessionDbAttr.LDAP_PORT.toString();
        } else if (key.equals(Constants.OPENDJ_JMX_PORT)) {
            return OpenDJConfig.AmSessionDbAttr.JMX_PORT.toString();
        } else if (key.equals(Constants.OPENDJ_REPL_PORT)) {
            return OpenDJConfig.AmSessionDbAttr.REPL_PORT.toString();
        } else {
            logger.warn("DB_INV_MAP " + key);
            return "";
        }
    }
    
    public static void unregisterServer(String serverUrl) {
        InternalClientConnection icConn = InternalClientConnection.getRootConnection();
        
        StringBuilder dn = new StringBuilder();
        dn.append(Constants.HOST_NAMING_ATTR).append(Constants.EQUALS).append(serverUrl);
        dn.append(Constants.COMMA).append(Constants.HOSTS_BASE_DN);
        dn.append(Constants.COMMA).append(OpenDJConfig.getSessionDBSuffix());
        
        DeleteOperation dop = icConn.processDelete(dn.toString());
        ResultCode resultCode = dop.getResultCode();
        
        if (resultCode != ResultCode.SUCCESS) {
            logger.warn("DB_DEL_FAIL " + dn);
        }
    }
    
    public static void setupReplication(Map<String, String> localMap, Map<String, String> remoteMap)
    throws Exception {
        int ret = setupReplicationEnable(localMap, remoteMap);
        
        if (ret == 0) {
            ret = setupReplicationInitialize(localMap, remoteMap);
            logger.debug("DB_REPL_SETUP");
        } else {
            logger.warn("DB_REPL_SETUP_FAIL " + ret);
            throw new StoreException("DB_REPL_SETUP_FAIL " + ret);
        }
    }
    
   /**
      * Setup replication between two OpenDJ amsessiondb stores.
      * $ dsreplication enable
      *    --no-prompt
      *    --host1 host1 --port1 1389 --bindDN1 "cn=Directory Manager"
      *    --bindPassword1 password --replicationPort1 8989
      *    --host2 host2 --port2 2389 --bindDN2 "cn=Directory Manager"
      *    --bindPassword2 password --replicationPort2 8990 
      *    --adminUID admin --adminPassword password 
      *    --baseDN "dc=amsessiondb,dc=com"
      *
      *
      *  @param map Map of configuration properties 
      *  @return status : 0 == success, !0 == failure
      */
    public static int setupReplicationEnable(Map<String, String> localMap, Map<String, String> remoteMap) {
        String[] enableCmd= {
            "enable",                // 0
            "--no-prompt",           // 1
            "--host1",               // 2
            "host1val",              // 3
            "--port1",               // 4
            "port1ival",             // 5
            "--bindDN1",             // 6
            "cn=Directory Manager",  // 7
            "--bindPassword1",       // 8
            "xxxxxxxx",              // 9
            "--replicationPort1",    // 10
            "8989",                  // 11
            "--host2",               // 12
            "host2val",              // 13
            "--port2",               // 14
            "port2ival",             // 15
            "--bindDN2",             // 16
            "cn=Directory Manager",  // 17
            "--bindPassword2",       // 18
            "xxxxxxxx",              // 19
            "--replicationPort2",    // 20
            "8989",                  // 21
            "--adminUID",            // 22
            "admin",                 // 23
            "--adminPassword",       // 24
            "xxxxxxxx",              // 25 
            "--baseDN",              // 26
            "dc=example,dc=com",     // 27
            "--trustAll",            // 28
            "--configFile",          // 29
            "path/to/config.ldif"    // 30
        };
        enableCmd[3] = remoteMap.get(Constants.HOST_FQDN);
        enableCmd[5] = remoteMap.get(Constants.OPENDJ_ADMIN_PORT);
        enableCmd[11] = remoteMap.get(Constants.OPENDJ_REPL_PORT);
        enableCmd[13] = localMap.get(Constants.HOST_FQDN);
        enableCmd[15] = localMap.get(Constants.OPENDJ_ADMIN_PORT);
        enableCmd[21] = localMap.get(Constants.OPENDJ_REPL_PORT);
        enableCmd[27] = localMap.get(Constants.OPENDJ_SUFFIX);
        enableCmd[9] = localMap.get(Constants.OPENDJ_DS_MGR_PASSWD);
        enableCmd[19] = localMap.get(Constants.OPENDJ_DS_MGR_PASSWD);
        enableCmd[25] = localMap.get(Constants.OPENDJ_DS_MGR_PASSWD);
        enableCmd[30] = localMap.get(Constants.OPENDJ_ROOT) + "/config/config.ldif";

        logger.debug("DB_H_A " + enableCmd[3]);
        logger.debug("DB_H_B " + enableCmd[13]);
        logger.debug("DB_P_A " + enableCmd[5]);
        logger.debug("DB_P_B " + enableCmd[15]);
        
        int ret = ReplicationCliMain.mainCLI(
            enableCmd, false, 
            SetupProgress.getOutputStream(), 
            SetupProgress.getOutputStream());         

        if (ret == 0) {
            SetupProgress.reportEnd("DB_REPL_SETUP_OK", null);
        } else {
            SetupProgress.reportEnd("DB_REPL_SETUP_FAILED", null);
        }
        
        return ret;
    }
    
    /**
     * Disable replication between the local OpenDJ amsessiondb store.
     * $ dsreplication disable
     *    --no-prompt
     *    --hostname host1 --port 4444 
     *    --adminUID admin --adminPassword password 
     *    --baseDN "dc=amsessiondb,dc=com" -X -n
     *
     *
     *  @param map Map of configuration properties 
     *  @return status : 0 == success, !0 == failure
     */
    public static int replicationDisable(Map<String, String> localMap) {
        String[] enableCmd= {
            "disable",                // 0
            "--no-prompt",            // 1
            "--hostname",             // 2
            "hostval",                // 3
            "--port",                 // 4
            "portval",                // 5
            "--adminUID",             // 6
            "admin",                  // 7
            "--adminPassword",        // 8
            "xxxxxxxx",               // 9 
            "--baseDN",               // 10
            "dc=example,dc=com",      // 11
            "-X",                     // 12
            "-n",                     // 13
            "--configFile",           // 14
            "path/to/config.ldif"     // 15
        };
        enableCmd[3] = localMap.get(Constants.HOST_FQDN);
        enableCmd[5] = localMap.get(Constants.OPENDJ_ADMIN_PORT);
        enableCmd[9] = localMap.get(Constants.OPENDJ_DS_MGR_PASSWD);
        enableCmd[11] = localMap.get(Constants.OPENDJ_SUFFIX);
        enableCmd[15] = localMap.get(Constants.OPENDJ_ROOT) + "/config/config.ldif";

        logger.debug("DB_REPL_DEL {} {} {}", new Object[]{enableCmd[3], enableCmd[5], enableCmd[11]});
        
        int ret = ReplicationCliMain.mainCLI(
            enableCmd, false, 
            SetupProgress.getOutputStream(), 
            SetupProgress.getOutputStream());         

        if (ret == 0) {
            SetupProgress.reportEnd("DB_REPL_DEL_OK", null);
        } else {
            SetupProgress.reportEnd("DB_REPL_DEL_FAIL", null);
        }
        
        return ret;
    }
    
    /**
      * Syncs replication data between two OpenDJ amsessiondb stores.
      * $ dsreplication initialize 
      *     --baseDN "dc=amsessiondb,dc=com" --adminUID admin --adminPassword pass
      *     --hostSource host1 --portSource 1389
      *     --hostDestination host2 --portDestination 2389
      *     --trustAll
      *
      *  @param map Map of configuration properties 
      *  @return status : 0 == success, !0 == failure
      */
    public static int setupReplicationInitialize(Map<String, String> localMap, Map<String, String> remoteMap) {
        String[] initializeCmd= {
            "initialize",                 // 0
            "--no-prompt",                // 1
            "--baseDN",                   // 2
            "dc=amsessiondb,dc=net",      // 3
            "--adminUID",                 // 4
            "admin",                      // 5
            "--adminPassword",            // 6
            "xxxxxxxx",                   // 7
            "--hostSource",               // 8
            "localhost",                  // 9
            "--portSource",               // 10
            "50389",                      // 11
            "--hostDestination",          // 12
            "localhost",                  // 13
            "--portDestination",          // 14
            "51389",                      // 15
            "--trustAll",                 // 16
            "--configFile",               // 17
            "path/to/config.ldif"         // 18
        };
        initializeCmd[3] = localMap.get(Constants.OPENDJ_SUFFIX);
        initializeCmd[9] = remoteMap.get(Constants.HOST_FQDN);
        initializeCmd[11] = remoteMap.get(Constants.OPENDJ_ADMIN_PORT);
        initializeCmd[13] = localMap.get(Constants.HOST_FQDN);
        initializeCmd[15] = localMap.get(Constants.OPENDJ_ADMIN_PORT);
        initializeCmd[18] = localMap.get(Constants.OPENDJ_ROOT) + "/config/config.ldif";

        initializeCmd[7] = localMap.get(Constants.OPENDJ_DS_MGR_PASSWD);
        int ret = ReplicationCliMain.mainCLI(initializeCmd, false,
            SetupProgress.getOutputStream(), SetupProgress.getOutputStream()); 

        if (ret == 0) {
            logger.debug("DB_REPL_OK");
        } else {
        	logger.debug("DB_REPL_FAIL");
        }
        
        return ret;
    }
            
    /**
     *  Starts the embedded <code>OpenDJ</code> instance.
     *
     *  @param odsRoot File system directory where <code>OpenDJ</code> 
     *                 is installed.
     *
     *  @throws Exception upon encountering errors.
     */
    public static void startServer(String odjRoot)
    throws Exception {
        if (isStarted()) {
            return;
        }
        
        logger.info("DB_DJ_STARTING " + odjRoot);

        DirectoryEnvironmentConfig config = new DirectoryEnvironmentConfig();
        config.setServerRoot(new File(odjRoot));
        config.setForceDaemonThreads(true);
        config.setConfigClass(ConfigFileHandler.class);
        config.setConfigFile(new File(odjRoot + "/config", "config.ldif"));
        logger.debug("DB_DJ_STARTING1");
        EmbeddedUtils.startServer(config);
        logger.debug("DB_DJ_STARTING2");

        int sleepcount = 0;
        
        while (!EmbeddedUtils.isRunning() && (sleepcount < 60)) {
            sleepcount++;
            Thread.sleep(1000);
        }
        
        if (EmbeddedUtils.isRunning()) {
            logger.info("DB_DJ_STARTING2");
        } else {
            logger.error("DB_DJ_START_FAIL");
        }

        serverStarted = true;
    }
    
    /**
     *  Gracefully shuts down the embedded OpenDJ instance.
     *
     *  @throws Exception on encountering errors.
     */
    public static void shutdownServer() 
    throws Exception {
        if (isStarted()) {
            logger.debug("DB_DJ_SHUT");
            DirectoryServer.shutDown(
                "org.forgerock.openam.amsessionstore.db.opendj.EmbeddedOpenDJ",
                LocalizableMessage.EMPTY);
            int sleepcount = 0;
            
            while (DirectoryServer.isRunning() && (sleepcount < 60)) {
                sleepcount++;
                Thread.sleep(1000);
            }
            
            serverStarted = false;
            logger.debug("DB_DJ_SHUT_OK");
        }
    }
    
    private static String concat(String[] args) {
        String ret = "";
        
        for (int i = 0; i < args.length; i++) {
           ret += args[i] + " ";        
        }
        
        return ret;
    }
}
