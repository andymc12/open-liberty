/*******************************************************************************
 * Copyright (c) 2017, 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.microprofile.openapi.fat.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.websphere.simplicity.config.Application;
import com.ibm.websphere.simplicity.config.HttpEndpoint;
import com.ibm.websphere.simplicity.config.ServerConfiguration;

import componenttest.topology.impl.LibertyServer;

/**
 * Utility methods used by OpenAPI Tests
 */
public class OpenAPITestUtil {

    private final static int TIMEOUT = 30000;

    /**
     * Change Liberty features (Mark is set first on log. Then wait for feature updated message using mark)
     *
     * @param server - Liberty server
     * @param features - Liberty features to enable
     * @throws Exception
     */
    public static void changeFeatures(LibertyServer server, String... features) throws Exception {
        List<String> featuresList = Arrays.asList(features);
        server.setMarkToEndOfLog();
        server.changeFeatures(featuresList);
        assertNotNull("Features weren't updated successfully", server.waitForStringInLogUsingMark("CWWKG0017I.* | CWWKG0018I.*"));
    }

    /**
     * @param server - Liberty server
     * @param name - The name of a feature to remove e.g. openapi-3.0
     */
    public static void removeFeature(LibertyServer server, String name) {
        try {
            server.setMarkToEndOfLog();
            ServerConfiguration config = server.getServerConfiguration();
            Set<String> features = config.getFeatureManager().getFeatures();
            if (!features.contains(name))
                return;
            features.remove(name);
            server.updateServerConfiguration(config);
            assertNotNull("Config wasn't updated successfully",
                          server.waitForStringInLogUsingMark("CWWKG0017I.* | CWWKG0018I.*"));
        } catch (Exception e) {
            Assert.fail("Unable to remove feature:" + name);
        }
    }

    /**
     * @param server - Liberty server
     * @param name - The name of a feature to add e.g. openapi-3.0
     */
    public static void addFeature(LibertyServer server, String name) {
        try {
            server.setMarkToEndOfLog();
            ServerConfiguration config = server.getServerConfiguration();
            Set<String> features = config.getFeatureManager().getFeatures();
            if (features.contains(name))
                return;
            features.add(name);
            server.updateServerConfiguration(config);
            assertNotNull("Config wasn't updated successfully",
                          server.waitForStringInLogUsingMark("CWWKG0017I.* | CWWKG0018I.*"));
        } catch (Exception e) {
            Assert.fail("Unable to add feature:" + name);
        }
    }

    /**
     * Wait for the message stating the app has been processed by the application processor adding.
     *
     * @param server - Liberty server
     * @throws Exception
     */
    public static void waitForApplicationProcessorAddedEvent(LibertyServer server, String appName) {
        String s = server.waitForStringInTraceUsingMark("Application Processor: Adding application ended: appInfo=.*[" + appName + "]", TIMEOUT);
        assertNotNull("FAIL: Application processor didn't successfully finish adding the app " + appName, s);
    }

    /**
     * Wait for the message stating the app has been processed by the application processor.
     *
     * @param server - Liberty server
     * @throws Exception
     */
    public static void waitForApplicationProcessorProcessedEvent(LibertyServer server, String appName) {
        String s = server.waitForStringInTraceUsingMark("Application Processor: Processing application ended: appInfo=.*[" + appName + "]", TIMEOUT);
        assertNotNull("FAIL: Application processor didn't successfully finish adding the app " + appName, s);
    }

    /**
     * Wait for the message stating the app has been processed by the application processor removing.
     *
     * @param server - Liberty server
     * @throws Exception
     */
    public static void waitForApplicationProcessorRemovedEvent(LibertyServer server, String appName) {
        String s = server.waitForStringInTraceUsingMark("Application Processor: Removing application ended: appInfo=.*[" + appName + "]", TIMEOUT);
        assertNotNull("FAIL: Application processor didn't successfully finish removing the app " + appName, s);
    }

    public static void waitForApplicationAdded(LibertyServer server, String appName) {
        String s = server.waitForStringInTraceUsingMark("Processign application ended: appInfo=.*[" + appName + "]", TIMEOUT);
        assertNotNull("FAIL: Application processor didn't successfully process the app " + appName, s);
    }

    public static Application removeApplication(LibertyServer server, String appName) {
        Application webApp = null;
        try {
            ServerConfiguration config = server.getServerConfiguration();
            webApp = config.getApplications().removeById(appName);
            server.updateServerConfiguration(config);
            server.waitForConfigUpdateInLogUsingMark(null);
            waitForApplicationProcessorRemovedEvent(server, appName);
            if (!webApp.getType().equals("ear")) {
                assertNotNull("FAIL: App didn't report is has been removed.",
                              server.waitForStringInLogUsingMark("CWWKT0017I.*" + appName));
            }
            assertNotNull("FAIL: App didn't report is has been stopped.",
                          server.waitForStringInLogUsingMark("CWWKZ0009I.*" + appName));
        } catch (Exception e) {
            fail("FAIL: Could not remove the application " + appName);
        }
        return webApp;
    }

    /**
     * Adds an application to the current config, or updates an application with
     * a specific name if it already exists
     *
     * @param name the name of the application
     * @param path the fully qualified path to the application archive on the liberty machine
     * @param type the type of the application (ear/war/etc)
     * @param waitForUpdate boolean controlling if the method should wait for the configuration update event before returning
     * @return the deployed application
     */
    public static Application addApplication(LibertyServer server, String name, String path, String type) throws Exception {
        ServerConfiguration config = server.getServerConfiguration();
        Application app = config.addApplication(name, path, type);
        server.updateServerConfiguration(config);
        return app;
    }

    /**
     * Adds an WAR application inside the '${server.config.dir}/apps/'
     * to the current config, or updates an application with a specific name
     * if it already exists. This method waits for the app to be processed by OpenAPI
     * Application Processor.
     *
     * @param name the name of the application
     * @return the deployed application
     */
    public static Application addApplication(LibertyServer server, String name) throws Exception {
        return addApplication(server, name, "${server.config.dir}/apps/" + name + ".war", "war");
    }

    public static void ensureOpenAPIEndpointIsReady(LibertyServer server) {
        assertNotNull("FAIL: Endpoint is not available at /openapi",
                      server.waitForStringInLog("CWWKT0016I.*" + "/openapi"));
    }

    public static JsonNode readYamlTree(String contents) {
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml(new SafeConstructor());
        return new ObjectMapper().convertValue(yaml.load(contents), JsonNode.class);
    }

    /**
     * Removes all the applications from server.xml
     *
     * @param server
     * @throws Exception
     */
    public static void removeAllApplication(LibertyServer server) throws Exception {
        server.getServerConfiguration().getApplications().stream().forEach(app -> removeApplication(server, app.getName()));
    }

    public static void checkServer(JsonNode root, String... expectedUrls) {
        JsonNode serversNode = root.get("servers");
        assertNotNull(serversNode);
        assertTrue(serversNode.isArray());
        ArrayNode servers = (ArrayNode) serversNode;

        List<String> urls = Arrays.asList(expectedUrls);
        servers.findValues("url").forEach(url -> assertTrue("FAIL: Unexpected server URL " + url, urls.contains(url.asText())));
        assertEquals("FAIL: Found incorrect number of server objects.", urls.size(), servers.size());
    }

    public static void checkPaths(JsonNode root, int expectedCount, String... containedPaths) {
        JsonNode pathsNode = root.get("paths");
        assertNotNull(pathsNode);
        assertTrue(pathsNode.isObject());
        ObjectNode paths = (ObjectNode) pathsNode;

        assertEquals("FAIL: Found incorrect number of server objects.", expectedCount, paths.size());
        List<String> expected = Arrays.asList(containedPaths);
        expected.stream().forEach(path -> assertNotNull("FAIL: OpenAPI document does not contain the expected path " + path, paths.get(path)));
    }

    /**
     * Sets the http and https ports on the server configuration object. Note: After this method is called, you should also call
     * <code>server.updateServerConfiguration(config);</code> for the configuration to take effect.
     *
     * @param config
     * @param httpPort
     * @param httpsPort
     * @throws Exception
     *
     */
    public static void changeServerPorts(LibertyServer server, int httpPort, int httpsPort) throws Exception {
        ServerConfiguration config = server.getServerConfiguration();
        HttpEndpoint http = config.getHttpEndpoints().getById("defaultHttpEndpoint");
        if (http == null) {
            http = new HttpEndpoint();
            http.setId("defaultHttpEndpoint");
            http.setHttpPort(server.getHttpDefaultPort());
            http.setHttpPort(server.getHttpDefaultSecurePort());
            config.getHttpEndpoints().add(http);
        }

        if (http.getHttpPort() == httpPort && http.getHttpsPort() == httpsPort) {
            return;
        }

        http.setHttpPort(httpPort);
        http.setHttpsPort(httpsPort);

        // Set the mark to the current end of log
        server.setMarkToEndOfLog();

        // Save the config and wait for message that was a result of the config change
        server.updateServerConfiguration(config);
        assertNotNull("FAIL: Didn't get expected config update log messages.", server.waitForConfigUpdateInLogUsingMark(null, false));
        server.resetLogMarks();
    }

    public static String[] getServerURLs(LibertyServer server, int httpPort, int httpsPort) {
        return getServerURLs(server, httpPort, httpsPort, null);
    }

    public static String[] getServerURLs(LibertyServer server, int httpPort, int httpsPort, String contextRoot) {
        List<String> servers = new ArrayList<>();
        contextRoot = contextRoot == null ? "" : contextRoot.startsWith("/") ? contextRoot : "/" + contextRoot;
        if (httpPort != -1) {
            servers.add("http://" + server.getHostname() + ":" + httpPort + contextRoot);
        }
        if (httpsPort != -1) {
            servers.add("https://" + server.getHostname() + ":" + httpsPort + contextRoot);
        }
        return servers.toArray(new String[0]);
    }
}
