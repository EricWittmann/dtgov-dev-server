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
import java.security.Principal;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.GregorianCalendar;
import java.util.List;

import javax.security.auth.Subject;
import javax.servlet.DispatcherType;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.SecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.UserIdentity;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.jboss.errai.bus.server.servlet.DefaultBlockingServlet;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.jboss.weld.environment.servlet.BeanManagerResourceBindingListener;
import org.jboss.weld.environment.servlet.Listener;
import org.oasis_open.docs.s_ramp.ns.s_ramp_v1.BaseArtifactEnum;
import org.oasis_open.docs.s_ramp.ns.s_ramp_v1.BaseArtifactType;
import org.oasis_open.docs.s_ramp.ns.s_ramp_v1.ExtendedArtifactType;
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
        Class.forName("org.slf4j.LoggerFactory"); //$NON-NLS-1$
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
        return 8080;
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#preConfig()
     */
    @Override
    protected void preConfig() {
        // Use an in-memory config for s-ramp
        System.setProperty("sramp.modeshape.config.url", "classpath://" + JCRRepository.class.getName() //$NON-NLS-1$ //$NON-NLS-2$
                + "/META-INF/modeshape-configs/inmemory-sramp-config.json"); //$NON-NLS-1$

        // Don't do any resource caching!
        System.setProperty("overlord.resource-caching.disabled", "true"); //$NON-NLS-1$ //$NON-NLS-2$

        // Configure the S-RAMP client
        System.setProperty(DtgovUIConfig.SRAMP_ATOM_API_ENDPOINT, "http://localhost:" + serverPort() + "/s-ramp-server"); //$NON-NLS-1$ //$NON-NLS-2$
        System.setProperty(DtgovUIConfig.SRAMP_ATOM_API_VALIDATING, "true"); //$NON-NLS-1$
        System.setProperty(DtgovUIConfig.SRAMP_ATOM_API_AUTH_PROVIDER, NoAuthenticationProvider.class.getName());

        // Integrate with the s-ramp browser (not actually running - so this won't really work)
        System.setProperty(DtgovUIConfig.SRAMP_UI_URL_BASE, "http://google.com/s-ramp-ui"); //$NON-NLS-1$

        // Configure the task client
        enableMockTaskClient();
//        enableLiveTaskClient();

        configureDeploymentsUI();
        configureQueriesUI();
    }

    /**
     * Set up some system properties needed by the queries UI.
     */
    private void configureQueriesUI() {
        System.setProperty(DtgovUIConfig.WORKFLOW_ARTIFACT_GROUP_KEY, "org.overlord.dtgov"); //$NON-NLS-1$
        System.setProperty(DtgovUIConfig.WORKFLOW_ARTIFACT_NAME_KEY, "dtgov-workflows"); //$NON-NLS-1$
        System.setProperty(DtgovUIConfig.WORKFLOW_ARTIFACT_VERSION_KEY, "LATEST"); //$NON-NLS-1$
    }

    /**
     * Adds the types and stages to the deployments UI.
     */
    private void configureDeploymentsUI() {
        System.setProperty(DtgovUIConfig.DEPLOYMENT_TYPE_PREFIX + ".switchyard", "SwitchYard Application:ext/SwitchYardApplication"); //$NON-NLS-1$ //$NON-NLS-2$
        System.setProperty(DtgovUIConfig.DEPLOYMENT_TYPE_PREFIX + ".war", "Web Application:ext/JavaWebApplication"); //$NON-NLS-1$ //$NON-NLS-2$

        System.setProperty(DtgovUIConfig.DEPLOYMENT_CLASSIFIER_STAGE_PREFIX + ".dev", "Development:http://www.jboss.org/overlord/deployment-status.owl#Dev"); //$NON-NLS-1$ //$NON-NLS-2$
        System.setProperty(DtgovUIConfig.DEPLOYMENT_CLASSIFIER_STAGE_PREFIX + ".qa", "QA:http://www.jboss.org/overlord/deployment-status.owl#Qa"); //$NON-NLS-1$ //$NON-NLS-2$
        System.setProperty(DtgovUIConfig.DEPLOYMENT_CLASSIFIER_STAGE_PREFIX + ".prod", "Production:http://www.jboss.org/overlord/deployment-status.owl#Prod"); //$NON-NLS-1$ //$NON-NLS-2$
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
        System.setProperty(DtgovUIConfig.TASK_API_ENDPOINT, "http://localhost:8080/dtgov/rest/tasks"); //$NON-NLS-1$
        System.setProperty(DtgovUIConfig.TASK_API_AUTH_PROVIDER, BasicAuthenticationProvider.class.getName());
        System.setProperty(DtgovUIConfig.TASK_API_BASIC_AUTH_USER, "admin"); //$NON-NLS-1$
        System.setProperty(DtgovUIConfig.TASK_API_BASIC_AUTH_PASS, "admin"); //$NON-NLS-1$
    }

    /**
     * @see org.overlord.commons.dev.server.ErraiDevServer#getErraiModuleId()
     */
    @Override
    protected String getErraiModuleId() {
        return "dtgov-ui"; //$NON-NLS-1$
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
        environment.addModule("dtgov-ui", //$NON-NLS-1$
                new WebAppModuleFromIDEDiscoveryStrategy(DtgovUI.class),
                new ErraiWebAppModuleFromMavenDiscoveryStrategy(DtgovUI.class));
        environment.addModule("overlord-commons-uiheader", //$NON-NLS-1$
                new JarModuleFromIDEDiscoveryStrategy(OverlordHeaderDataJS.class, "src/main/resources/META-INF/resources"), //$NON-NLS-1$
                new JarModuleFromMavenDiscoveryStrategy(OverlordHeaderDataJS.class, "/META-INF/resources")); //$NON-NLS-1$
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
        dtgovUI.setSecurityHandler(createSecurityHandler(true));
        dtgovUI.setContextPath("/dtgov-ui"); //$NON-NLS-1$
        dtgovUI.setWelcomeFiles(new String[] { "index.html" }); //$NON-NLS-1$
        dtgovUI.setResourceBase(environment.getModuleDir("dtgov-ui").getCanonicalPath()); //$NON-NLS-1$
        dtgovUI.setInitParameter("errai.properties", "/WEB-INF/errai.properties"); //$NON-NLS-1$ //$NON-NLS-2$
        dtgovUI.setInitParameter("login.config", "/WEB-INF/login.config"); //$NON-NLS-1$ //$NON-NLS-2$
        dtgovUI.setInitParameter("users.properties", "/WEB-INF/users.properties"); //$NON-NLS-1$ //$NON-NLS-2$
        dtgovUI.addEventListener(new Listener());
        dtgovUI.addEventListener(new BeanManagerResourceBindingListener());
        dtgovUI.addFilter(GWTCacheControlFilter.class, "/app/*", EnumSet.of(DispatcherType.REQUEST)); //$NON-NLS-1$
        dtgovUI.addFilter(ResourceCacheControlFilter.class, "/css/*", EnumSet.of(DispatcherType.REQUEST)); //$NON-NLS-1$
        dtgovUI.addFilter(ResourceCacheControlFilter.class, "/images/*", EnumSet.of(DispatcherType.REQUEST)); //$NON-NLS-1$
        dtgovUI.addFilter(ResourceCacheControlFilter.class, "/js/*", EnumSet.of(DispatcherType.REQUEST)); //$NON-NLS-1$
        dtgovUI.addFilter(org.overlord.dtgov.ui.server.filters.LocaleFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST)); //$NON-NLS-1$
        // Servlets
        ServletHolder erraiServlet = new ServletHolder(DefaultBlockingServlet.class);
        erraiServlet.setInitOrder(1);
        dtgovUI.addServlet(erraiServlet, "*.erraiBus"); //$NON-NLS-1$
        ServletHolder headerDataServlet = new ServletHolder(OverlordHeaderDataJS.class);
        headerDataServlet.setInitParameter("app-id", "dtgov"); //$NON-NLS-1$ //$NON-NLS-2$
        dtgovUI.addServlet(headerDataServlet, "/js/overlord-header-data.js"); //$NON-NLS-1$
        dtgovUI.addServlet(new ServletHolder(DeploymentDownloadServlet.class), "/app/services/deploymentDownload"); //$NON-NLS-1$
        dtgovUI.addServlet(new ServletHolder(DeploymentUploadServlet.class), "/app/services/deploymentUpload"); //$NON-NLS-1$
        dtgovUI.addServlet(new ServletHolder(UiConfigurationServlet.class), "/js/dtgovui-configuration.js"); //$NON-NLS-1$
        // File resources
        ServletHolder resources = new ServletHolder(new MultiDefaultServlet());
        resources.setInitParameter("resourceBase", "/"); //$NON-NLS-1$ //$NON-NLS-2$
        resources.setInitParameter("resourceBases", environment.getModuleDir("dtgov-ui").getCanonicalPath() //$NON-NLS-1$ //$NON-NLS-2$
                + "|" + environment.getModuleDir("overlord-commons-uiheader").getCanonicalPath()); //$NON-NLS-1$ //$NON-NLS-2$
        resources.setInitParameter("dirAllowed", "true"); //$NON-NLS-1$ //$NON-NLS-2$
        resources.setInitParameter("pathInfoOnly", "false"); //$NON-NLS-1$ //$NON-NLS-2$
        String[] fileTypes = new String[] { "html", "js", "css", "png", "gif" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
        for (String fileType : fileTypes) {
            dtgovUI.addServlet(resources, "*." + fileType); //$NON-NLS-1$
        }

        /* *************
         * S-RAMP server
         * ************* */
        ServletContextHandler srampServer = new ServletContextHandler(ServletContextHandler.SESSIONS);
        srampServer.setSecurityHandler(createSecurityHandler(false));
        srampServer.setContextPath("/s-ramp-server"); //$NON-NLS-1$
        ServletHolder resteasyServlet = new ServletHolder(new HttpServletDispatcher());
        resteasyServlet.setInitParameter("javax.ws.rs.Application", SRAMPApplication.class.getName()); //$NON-NLS-1$
        srampServer.addServlet(resteasyServlet, "/*"); //$NON-NLS-1$
        srampServer.addFilter(LocaleFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST)); //$NON-NLS-1$


        handlers.addHandler(dtgovUI);
        handlers.addHandler(srampServer);
    }

    /**
     * @see org.overlord.commons.dev.server.DevServer#postStart(org.overlord.commons.dev.server.DevServerEnvironment)
     */
    @Override
    protected void postStart(DevServerEnvironment environment) throws Exception {
        System.out.println("----------  Seeding  ---------------"); //$NON-NLS-1$

        SrampAtomApiClient client = new SrampAtomApiClient("http://localhost:"+serverPort()+"/s-ramp-server"); //$NON-NLS-1$ //$NON-NLS-2$
        seedOntology(client);
        seedTaskForm(client);
        seedDeployments(client);
        seedWorkflowQueries(client);
        System.out.println("----------  DONE  ---------------"); //$NON-NLS-1$
        System.out.println("Now try:  \n  http://localhost:"+serverPort()+"/dtgov-ui/index.html"); //$NON-NLS-1$ //$NON-NLS-2$
        System.out.println("---------------------------------"); //$NON-NLS-1$
    }

    /**
     * @param client
     */
    private void seedOntology(SrampAtomApiClient client) throws Exception {
        InputStream is = null;

        // Ontology
        try {
            is = DTGovDevServer.class.getResourceAsStream("deployment-status.owl"); //$NON-NLS-1$
            client.uploadOntology(is);
            System.out.println("Deployment status ontology uploaded."); //$NON-NLS-1$
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
            is = DTGovDevServer.class.getResourceAsStream("mock-task.form.html"); //$NON-NLS-1$
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.XmlDocument(), is, "sample-task-taskform.xml"); //$NON-NLS-1$
            artifact.setDescription("The starter task form that goes with the mock task client.  It maps to a task type of 'task-type-1'."); //$NON-NLS-1$
            artifact.setVersion("1.0"); //$NON-NLS-1$
            SrampModelUtils.setCustomProperty(artifact, "task-type", "mock-task"); //$NON-NLS-1$ //$NON-NLS-2$
            client.updateArtifactMetaData(artifact);
            System.out.println("Task form added"); //$NON-NLS-1$
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * @param client
     */
    private void seedWorkflowQueries(SrampAtomApiClient client) throws Exception {
        List<String> workflows = new ArrayList<String>();
        workflows.add("overlord.demo.SimpleReleaseProcess"); //$NON-NLS-1$
        workflows.add("overlord.demo.SimplifiedProjectLifeCycle"); //$NON-NLS-1$
        for (int i = 0; i < 15; i++) {
            ExtendedArtifactType toSave = new ExtendedArtifactType();
            toSave.setArtifactType(BaseArtifactEnum.EXTENDED_ARTIFACT_TYPE);
            toSave.setExtendedType("DtgovWorkflowQuery"); //$NON-NLS-1$
            toSave.setName("Name" + i); //$NON-NLS-1$
            toSave.setDescription("Description" + i); //$NON-NLS-1$

            SrampModelUtils.setCustomProperty(toSave, "query", "s-ramp query " + i); //$NON-NLS-1$ //$NON-NLS-2$

            Double random = (Math.random() * workflows.size());
            SrampModelUtils.setCustomProperty(toSave, "workflow", workflows.get(random.intValue())); //$NON-NLS-1$

            GregorianCalendar gcal = new GregorianCalendar();
            gcal.setTime(new Date());
            try {
                XMLGregorianCalendar xmlCal = DatatypeFactory.newInstance().newXMLGregorianCalendar(gcal);
                toSave.setCreatedTimestamp(xmlCal);
            } catch (DatatypeConfigurationException ee) {

            }

            for (int j = 0; j < 5; j++) {
                SrampModelUtils.setCustomProperty(toSave, "prop.propertyName" + j, "propertyValue" + j); //$NON-NLS-1$ //$NON-NLS-2$
            }
            client.createArtifact(toSave);
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
            is = DTGovDevServer.class.getResourceAsStream("switchyard-app-1.jar"); //$NON-NLS-1$
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.ExtendedDocument("SwitchYardApplication"), is, "switchyard-app-1.jar"); //$NON-NLS-1$ //$NON-NLS-2$
            artifact.setDescription("Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum. Praesent mauris. Fusce nec tellus sed augue semper porta. Mauris massa. Vestibulum lacinia arcu eget nulla. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Curabitur sodales ligula in libero. Sed dignissim lacinia nunc."); //$NON-NLS-1$
            artifact.setVersion("1.0"); //$NON-NLS-1$
            artifact.getClassifiedBy().add("http://www.jboss.org/overlord/deployment-status.owl#DevTest"); //$NON-NLS-1$
            client.updateArtifactMetaData(artifact);

            SrampModelUtils.setCustomProperty(artifact, "my-property-1", "prop-val-1"); //$NON-NLS-1$ //$NON-NLS-2$
            SrampModelUtils.setCustomProperty(artifact, "my-property-2", "prop-val-2"); //$NON-NLS-1$ //$NON-NLS-2$
            client.updateArtifactMetaData(artifact);

            // Now expand the deployment
            ArtifactType type = ArtifactType.valueOf(artifact);
            is.close();
            is = DTGovDevServer.class.getResourceAsStream("switchyard-app-1.jar"); //$NON-NLS-1$
            expander = ZipToSrampArchiveRegistry.createExpander(type, is);
            expander.setContextParam(DefaultMetaDataFactory.PARENT_UUID, artifact.getUuid());
            archive = expander.createSrampArchive();
            client.uploadBatch(archive);

            System.out.println("SwitchYard Application #1 added"); //$NON-NLS-1$
        } finally {
            IOUtils.closeQuietly(is);
            ZipToSrampArchive.closeQuietly(expander);
        }

        // Add switchyard app #2
        try {
            is = DTGovDevServer.class.getResourceAsStream("switchyard-app-2.jar"); //$NON-NLS-1$
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.ExtendedDocument("SwitchYardApplication"), is, "switchyard-app-2.jar"); //$NON-NLS-1$ //$NON-NLS-2$
            artifact.setDescription("Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum. Praesent mauris. Fusce nec tellus sed augue semper porta. Mauris massa. Vestibulum lacinia arcu eget nulla. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Curabitur sodales ligula in libero. Sed dignissim lacinia nunc."); //$NON-NLS-1$
            artifact.setVersion("2.0"); //$NON-NLS-1$
            artifact.getClassifiedBy().add("http://www.jboss.org/overlord/deployment-status.owl#DevPass"); //$NON-NLS-1$
            client.updateArtifactMetaData(artifact);
            System.out.println("SwitchYard Application #2 added"); //$NON-NLS-1$
        } finally {
            IOUtils.closeQuietly(is);
        }

        // Add switchyard app #3
        try {
            is = DTGovDevServer.class.getResourceAsStream("switchyard-app-3.jar"); //$NON-NLS-1$
            BaseArtifactType artifact = client.uploadArtifact(ArtifactType.ExtendedDocument("SwitchYardApplication"), is, "switchyard-app-3.jar"); //$NON-NLS-1$ //$NON-NLS-2$
            artifact.setDescription("Nulla quis sem at nibh elementum imperdiet. Duis sagittis ipsum. Praesent mauris. Fusce nec tellus sed augue semper porta. Mauris massa. Vestibulum lacinia arcu eget nulla. Class aptent taciti sociosqu ad litora torquent per conubia nostra, per inceptos himenaeos. Curabitur sodales ligula in libero. Sed dignissim lacinia nunc."); //$NON-NLS-1$
            artifact.setVersion("3.0"); //$NON-NLS-1$
            artifact.getClassifiedBy().add("http://www.jboss.org/overlord/deployment-status.owl#ProdTest"); //$NON-NLS-1$
            client.updateArtifactMetaData(artifact);
            System.out.println("SwitchYard Application #3 added"); //$NON-NLS-1$
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * @return a security handler
     */
    private SecurityHandler createSecurityHandler(boolean forUI) {
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"overlorduser"}); //$NON-NLS-1$
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*"); //$NON-NLS-1$

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setSessionRenewedOnAuthentication(false);
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName("overlord"); //$NON-NLS-1$
        if (forUI) {
            csh.addConstraintMapping(cm);
        }
        csh.setLoginService(new HashLoginService() {
            @Override
            public UserIdentity login(String username, Object credentials) {
                Credential credential = (credentials instanceof Credential) ? (Credential) credentials
                        : Credential.getCredential(credentials.toString());
                Principal userPrincipal = new KnownUser(username, credential);
                Subject subject = new Subject();
                subject.getPrincipals().add(userPrincipal);
                subject.getPrivateCredentials().add(credential);
                String[] roles = new String[] { "overlorduser", "overlordadmin", "admin.sramp" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                for (String role : roles) {
                    subject.getPrincipals().add(new RolePrincipal(role));
                }
                subject.setReadOnly();
                return _identityService.newUserIdentity(subject, userPrincipal, roles);
            }
        });

        return csh;
    }

}
