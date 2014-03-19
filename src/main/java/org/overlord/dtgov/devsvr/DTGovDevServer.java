/*
 * Copyright 2013 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.overlord.dtgov.devsvr;

import java.io.InputStream;
import java.util.EnumSet;

import javax.servlet.DispatcherType;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.errai.bus.server.servlet.DefaultBlockingServlet;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;
import org.oasis_open.docs.s_ramp.ns.s_ramp_v1.BaseArtifactType;
import org.overlord.commons.dev.server.DevServerEnvironment;
import org.overlord.commons.dev.server.ErraiDevServer;
import org.overlord.commons.dev.server.MultiDefaultServlet;
import org.overlord.commons.dev.server.discovery.ErraiWebAppModuleFromMavenDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.JarModuleFromIDEDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.JarModuleFromMavenDiscoveryStrategy;
import org.overlord.commons.dev.server.discovery.WebAppModuleFromIDEDiscoveryStrategy;
import org.overlord.commons.gwt.server.filters.GWTCacheControlFilter;
import org.overlord.commons.gwt.server.filters.ResourceCacheControlFilter;
import org.overlord.commons.ui.header.OverlordHeaderDataJS;
import org.overlord.dtgov.devsvr.mock.MockTaskClient;
import org.overlord.dtgov.ui.server.DtgovUI;
import org.overlord.dtgov.ui.server.DtgovUIConfig;
import org.overlord.dtgov.ui.server.services.sramp.NoAuthenticationProvider;
import org.overlord.dtgov.ui.server.services.tasks.BasicAuthenticationProvider;
import org.overlord.dtgov.ui.server.services.tasks.DtGovTaskApiClient;
import org.overlord.dtgov.ui.server.servlets.DeploymentDownloadServlet;
import org.overlord.dtgov.ui.server.servlets.DeploymentUploadServlet;
import org.overlord.dtgov.ui.server.servlets.UiConfigurationServlet;
import org.overlord.sramp.atom.archive.SrampArchive;
import org.overlord.sramp.atom.archive.expand.DefaultMetaDataFactory;
import org.overlord.sramp.atom.archive.expand.ZipToSrampArchive;
import org.overlord.sramp.atom.archive.expand.registry.ZipToSrampArchiveRegistry;
import org.overlord.sramp.client.SrampAtomApiClient;
import org.overlord.sramp.common.ArtifactType;
import org.overlord.sramp.common.SrampModelUtils;
import org.overlord.sramp.repository.jcr.JCRRepository;
import org.overlord.sramp.server.atom.services.SRAMPApplication;
import org.overlord.sramp.server.filters.LocaleFilter;

/**
 * A dev server for DTGov.
 * @author eric.wittmann@redhat.com
 */
public class DTGovDevServer extends ErraiDevServer {

    /**
     * Main entry point.
     * @param args
     */
    public static void main(String [] args) throws Exception {
        Class.forName("org.slf4j.LoggerFactory");
        DTGovDevServer devServer = new DTGovDevServer(args);
        devServer.enableDebug();
        devServer.go();
    }

    /**
     * Constructor.
     * @param args
     */
    public DTGovDevServer(String [] args) {
        super(args);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#serverPort()
     */
    @Override
    protected int serverPort() {
        return 8088;
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#preConfig()
     */
    @Override
    protected void preConfig() {
        // Use an in-memory config for s-ramp
        System.setProperty("sramp.modeshape.config.url", "classpath://" + JCRRepository.class.getName()
                + "/META-INF/modeshape-configs/inmemory-sramp-config.json");

        // Don't do any resource caching!
        System.setProperty("overlord.resource-caching.disabled", "true");

        // Configure the S-RAMP client
        System.setProperty(DtgovUIConfig.SRAMP_ATOM_API_ENDPOINT, "http://localhost:" + serverPort() + "/s-ramp-server");
        System.setProperty(DtgovUIConfig.SRAMP_ATOM_API_VALIDATING, "true");
        System.setProperty(DtgovUIConfig.SRAMP_ATOM_API_AUTH_PROVIDER, NoAuthenticationProvider.class.getName());

        // Integrate with the s-ramp browser (not actually running - so this won't really work)
        System.setProperty(DtgovUIConfig.SRAMP_UI_URL_BASE, "http://google.com/s-ramp-ui");

        // Configure the task client
        enableMockTaskClient();
//        enableLiveTaskClient();

        configureDeploymentsUI();
    }

    /**
     * Adds the types and stages to the deployments UI.
     */
    private void configureDeploymentsUI() {
        System.setProperty(DtgovUIConfig.DEPLOYMENT_TYPE_PREFIX + ".switchyard", "SwitchYard Application:ext/SwitchYardApplication");
        System.setProperty(DtgovUIConfig.DEPLOYMENT_TYPE_PREFIX + ".war", "Web Application:ext/JavaWebApplication");

        System.setProperty(DtgovUIConfig.DEPLOYMENT_CLASSIFIER_STAGE_PREFIX + ".dev", "Development:http://www.jboss.org/overlord/deployment-status.owl#Dev");
        System.setProperty(DtgovUIConfig.DEPLOYMENT_CLASSIFIER_STAGE_PREFIX + ".qa", "QA:http://www.jboss.org/overlord/deployment-status.owl#Qa");
        System.setProperty(DtgovUIConfig.DEPLOYMENT_CLASSIFIER_STAGE_PREFIX + ".prod", "Production:http://www.jboss.org/overlord/deployment-status.owl#Prod");
    }

    /**
     * Enables the mock task client (does not require jbpm or any sort of REST based task api endpoint).
     */
    protected void enableMockTaskClient() {
        System.setProperty(DtgovUIConfig.TASK_CLIENT_CLASS, MockTaskClient.class.getName());
    }

    /**
     * Enables the live jbpm/dtgov based task client.  This requires the dtgov REST based task api
     * to be running.
     */
    protected void enableLiveTaskClient() {
        System.setProperty(DtgovUIConfig.TASK_CLIENT_CLASS, DtGovTaskApiClient.class.getName());
        System.setProperty(DtgovUIConfig.TASK_API_ENDPOINT, "http://localhost:8080/dtgov/rest/tasks");
        System.setProperty(DtgovUIConfig.TASK_API_AUTH_PROVIDER, BasicAuthenticationProvider.class.getName());
        System.setProperty(DtgovUIConfig.TASK_API_BASIC_AUTH_USER, "eric");
        System.setProperty(DtgovUIConfig.TASK_API_BASIC_AUTH_PASS, "eric");
    }

    /**
     * @see org.overlord.commons.dev.server.ErraiDevServer#getErraiModuleId()
     */
    @Override
    protected String getErraiModuleId() {
        return "dtgov-ui";
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#createDevEnvironment()
     */
    @Override
    protected DevServerEnvironment createDevEnvironment() {
        return new DTGovDevServerEnvironment(args);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#addModules(org.overlord.commons.dev.server.DevServerEnvironment)
     */
    @Override
    protected void addModules(DevServerEnvironment environment) {
        environment.addModule("dtgov-ui",
                new WebAppModuleFromIDEDiscoveryStrategy(DtgovUI.class),
                new ErraiWebAppModuleFromMavenDiscoveryStrategy(DtgovUI.class));
        environment.addModule("overlord-commons-uiheader",
                new JarModuleFromIDEDiscoveryStrategy(OverlordHeaderDataJS.class, "src/main/resources/META-INF/resources"),
                new JarModuleFromMavenDiscoveryStrategy(OverlordHeaderDataJS.class, "/META-INF/resources"));
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#addModulesToJetty(org.overlord.commons.dev.server.DevServerEnvironment, org.eclipse.jetty.server.handler.ContextHandlerCollection)
     */
    @Override
    protected void addModulesToJetty(DevServerEnvironment environment, ContextHandlerCollection handlers) throws Exception {
        super.addModulesToJetty(environment, handlers);
        /* *********
         * DTGov UI
         * ********* */
        ServletContextHandler dtgovUI = new ServletContextHandler(ServletContextHandler.SESSIONS);
        dtgovUI.setContextPath("/dtgov-ui");
        dtgovUI.setWelcomeFiles(new String[] { "index.html" });
        dtgovUI.setResourceBase(environment.getModuleDir("dtgov-ui").getCanonicalPath());
        dtgovUI.setInitParameter("errai.properties", "/WEB-INF/errai.properties");
        dtgovUI.setInitParameter("login.config", "/WEB-INF/login.config");
        dtgovUI.setInitParameter("users.properties", "/WEB-INF/users.properties");
        dtgovUI.addEventListener(new Listener());
        dtgovUI.addEventListener(new BeanManagerResourceBindingListener());
        dtgovUI.addFilter(GWTCacheControlFilter.class, "/app/*", EnumSet.of(DispatcherType.REQUEST));
        dtgovUI.addFilter(ResourceCacheControlFilter.class, "/css/*", EnumSet.of(DispatcherType.REQUEST));
        dtgovUI.addFilter(ResourceCacheControlFilter.class, "/images/*", EnumSet.of(DispatcherType.REQUEST));
        dtgovUI.addFilter(ResourceCacheControlFilter.class, "/js/*", EnumSet.of(DispatcherType.REQUEST));
        dtgovUI.addFilter(org.overlord.dtgov.ui.server.filters.LocaleFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        // Servlets
        ServletHolder erraiServlet = new ServletHolder(DefaultBlockingServlet.class);
        erraiServlet.setInitOrder(1);
        dtgovUI.addServlet(erraiServlet, "*.erraiBus");
        ServletHolder headerDataServlet = new ServletHolder(OverlordHeaderDataJS.class);
        headerDataServlet.setInitParameter("app-id", "dtgov");
        dtgovUI.addServlet(headerDataServlet, "/js/overlord-header-data.js");
        dtgovUI.addServlet(new ServletHolder(DeploymentDownloadServlet.class), "/app/services/deploymentDownload");
        dtgovUI.addServlet(new ServletHolder(DeploymentUploadServlet.class), "/app/services/deploymentUpload");
        dtgovUI.addServlet(new ServletHolder(UiConfigurationServlet.class), "/js/dtgovui-configuration.js");
        // File resources
        ServletHolder resources = new ServletHolder(new MultiDefaultServlet());
        resources.setInitParameter("resourceBase", "/");
        resources.setInitParameter("resourceBases", environment.getModuleDir("dtgov-ui").getCanonicalPath()
                + "|" + environment.getModuleDir("overlord-commons-uiheader").getCanonicalPath());
        resources.setInitParameter("dirAllowed", "true");
        resources.setInitParameter("pathInfoOnly", "false");
        String[] fileTypes = new String[] { "html", "js", "css", "png", "gif" };
        for (String fileType : fileTypes) {
            dtgovUI.addServlet(resources, "*." + fileType);
        }

        /* *************
         * S-RAMP server
         * ************* */
        ServletContextHandler srampServer = new ServletContextHandler(ServletContextHandler.SESSIONS);
        srampServer.setContextPath("/s-ramp-server");
        ServletHolder resteasyServlet = new ServletHolder(new HttpServletDispatcher());
        resteasyServlet.setInitParameter("javax.ws.rs.Application", SRAMPApplication.class.getName());
        srampServer.addServlet(resteasyServlet, "/*");
        srampServer.addFilter(LocaleFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));


        handlers.addHandler(dtgovUI);
        handlers.addHandler(srampServer);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#postStart(org.overlord.commons.dev.server.DevServerEnvironment)
     */
    @Override
    protected void postStart(DevServerEnvironment environment) throws Exception {
        System.out.println("----------  Seeding  ---------------");

        SrampAtomApiClient client = new SrampAtomApiClient("http://localhost:"+serverPort()+"/s-ramp-server");
        seedOntology(client);
        seedTaskForm(client);
        seedDeployments(client);

        System.out.println("----------  DONE  ---------------");
        System.out.println("Now try:  \n  http://localhost:"+serverPort()+"/dtgov-ui/index.html");
        System.out.println("---------------------------------");
    }

    /**
     * @param client
     */
    private void seedOntology(SrampAtomApiClient client) throws Exception {
        InputStream is = null;

        // Ontology
        try {
            is = DTGovDevServer.class.getResourceAsStream("deployment-status.owl");
            client.uploadOntology(is);
            System.out.println("Deployment status ontology uploaded.");
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * @param client
     */
    private void seedTaskForm(SrampAtomApiClient client) throws Exception {
        InputStream is = null;

        try {
            is = DTGovDevServer.class.getResourceAsStream("mock-task.form.html");
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.XmlDocument(), is, "sample-task-taskform.xml");
            artifact.setDescription("The starter task form that goes with the mock task client.  It maps to a task type of 'task-type-1'.");
            artifact.setVersion("1.0");
            SrampModelUtils.setCustomProperty(artifact, "task-type", "mock-task");
            client.updateArtifactMetaData(artifact);
            System.out.println("Task form added");
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * @param client
     */
    private void seedDeployments(SrampAtomApiClient client) throws Exception {
        InputStream is = null;

        // Add switchyard app #1
        ZipToSrampArchive expander = null;
        SrampArchive archive = null;
        try {
            is = DTGovDevServer.class.getResourceAsStream("switchyard-app-1.jar");
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.ExtendedDocument("SwitchYardApplication"), is, "switchyard-app-1.jar");
            artifact.setDescription("Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum. Praesent mauris. Fusce nec tellus sed augue semper porta. Mauris massa. Vestibulum lacinia arcu eget nulla. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Curabitur sodales ligula in libero. Sed dignissim lacinia nunc.");
            artifact.setVersion("1.0");
            artifact.getClassifiedBy().add("http://www.jboss.org/overlord/deployment-status.owl#DevTest");
            client.updateArtifactMetaData(artifact);

            SrampModelUtils.setCustomProperty(artifact, "my-property-1", "prop-val-1");
            SrampModelUtils.setCustomProperty(artifact, "my-property-2", "prop-val-2");
            client.updateArtifactMetaData(artifact);

            // Now expand the deployment
            ArtifactType type = ArtifactType.valueOf(artifact);
            is.close();
            is = DTGovDevServer.class.getResourceAsStream("switchyard-app-1.jar");
            expander = ZipToSrampArchiveRegistry.createExpander(type, is);
            expander.setContextParam(DefaultMetaDataFactory.PARENT_UUID, artifact.getUuid());
            archive = expander.createSrampArchive();
            client.uploadBatch(archive);

            System.out.println("SwitchYard Application #1 added");
        } finally {
            IOUtils.closeQuietly(is);
            ZipToSrampArchive.closeQuietly(expander);
        }

        // Add switchyard app #2
        try {
            is = DTGovDevServer.class.getResourceAsStream("switchyard-app-2.jar");
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.ExtendedDocument("SwitchYardApplication"), is, "switchyard-app-2.jar");
            artifact.setDescription("Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum. Praesent mauris. Fusce nec tellus sed augue semper porta. Mauris massa. Vestibulum lacinia arcu eget nulla. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Curabitur sodales ligula in libero. Sed dignissim lacinia nunc.");
            artifact.setVersion("2.0");
            artifact.getClassifiedBy().add("http://www.jboss.org/overlord/deployment-status.owl#DevPass");
            client.updateArtifactMetaData(artifact);
            System.out.println("SwitchYard Application #2 added");
        } finally {
            IOUtils.closeQuietly(is);
        }

        // Add switchyard app #3
        try {
            is = DTGovDevServer.class.getResourceAsStream("switchyard-app-3.jar");
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.ExtendedDocument("SwitchYardApplication"), is, "switchyard-app-3.jar");
            artifact.setDescription("Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum. Praesent mauris. Fusce nec tellus sed augue semper porta. Mauris massa. Vestibulum lacinia arcu eget nulla. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Curabitur sodales ligula in libero. Sed dignissim lacinia nunc.");
            artifact.setVersion("3.0");
            artifact.getClassifiedBy().add("http://www.jboss.org/overlord/deployment-status.owl#ProdTest");
            client.updateArtifactMetaData(artifact);
            System.out.println("SwitchYard Application #3 added");
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

}
