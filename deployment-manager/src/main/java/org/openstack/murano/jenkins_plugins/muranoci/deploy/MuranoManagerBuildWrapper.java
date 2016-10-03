package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.security.ACL;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.http.impl.client.SystemDefaultCredentialsProvider;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.Exported;
import org.openstack.murano.jenkins_plugins.muranoci.deploy.credentials.OpenstackCredentials;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URL;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Objects.requireNonNull;


public class MuranoManagerBuildWrapper extends BuildWrapper implements Serializable {
    private static String MURANO_ENV_NAME = "MuranoCI-";


    private final MuranoDeployment deployment;
    private String credentialsId;

    @DataBoundConstructor
    public MuranoManagerBuildWrapper(MuranoDeployment deployment,
                                     String credentialsId) {

        this.deployment = requireNonNull(deployment);
        this.credentialsId = requireNonNull(credentialsId);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        EnvVars env = build.getEnvironment(listener);
        OpenstackCredentials credentials = getOpenstackCredentials(getCredentialsId());

        try {
            MuranoHelper helper = new MuranoHelper(
                    credentials.getIdentityServiceEndpoint(),
                    credentials.getUsername(),
                    credentials.getPassword().getPlainText(),
                    credentials.getTenant()
            );
            if (env.containsKey("BUILD_ENVIRONMENT_TIMEOUT")) {
                int timeout = Integer.parseInt(env.get("BUILD_ENVIRONMENT_TIMEOUT"));
                helper.setTimeout(timeout);
            }

            //TODO: Remove
            try {
                ((RepositoryTemplatedDeployment) deployment).readObjectModel(build.getWorkspace());
            } catch (Exception io) {
            }

            String name = generateEnvName();

            String envId = helper.deployNewFromObjectModel(
                    name, deployment.getObjectModel());

            boolean result = helper.waitDeploymentResult(envId);
            if (!result) {
                build.setResult(Result.FAILURE);
            }
        } catch (Exception e) {
            e.printStackTrace();
            build.setResult(Result.FAILURE);
        }

        return new JenkinsEnvironmentImpl(env);
    }

    private String generateEnvName() {
        return MURANO_ENV_NAME + new BigInteger(
                32,
                new SecureRandom())
                .toString(16);
    }

    private OpenstackCredentials getOpenstackCredentials(String credentialsId) {
        List<OpenstackCredentials> openstackCredentialsList =
                CredentialsProvider.lookupCredentials(
                        OpenstackCredentials.class,
                        Jenkins.getInstance(),
                        ACL.SYSTEM);
        OpenstackCredentials openstackCredentials = CredentialsMatchers.firstOrNull(
                openstackCredentialsList,
                CredentialsMatchers.allOf(
                        CredentialsMatchers.withId(credentialsId)));

        return openstackCredentials;
    }

    @Exported
    public MuranoDeployment getDeployment() {
        return deployment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * The descriptor for our {@code MuranoManagerBuildWrapper} plugin.
     */
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(AbstractProject<?, ?> project) {
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.MuranoManagerBuildWrapper_DisplayName();
        }


        @SuppressWarnings("unused") // used by stapler
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Jenkins context,
                                                     @QueryParameter String remoteBase) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            List<DomainRequirement> domainRequirements = newArrayList();
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(OpenstackCredentials.class)),
                            CredentialsProvider.lookupCredentials(
                                    StandardCredentials.class,
                                    context,
                                    ACL.SYSTEM,
                                    domainRequirements));
        }

    }

    private final class JenkinsEnvironmentImpl extends Environment {
        private final EnvVars envVars;

        /**
         * Construct the instance with a snapshot of the environment within which it was created in case
         * values that were used to configure it at the start of the build change before the end.
         *
         * @param envVars The set of environment variables used to spin up the ephemeral deployment, so
         *                we can tear it down with the same.
         */
        public JenkinsEnvironmentImpl(EnvVars envVars) {
            this.envVars = requireNonNull(envVars);
        }

        @Override
        public void buildEnvVars(Map<String, String> env) {
            super.buildEnvVars(env);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean tearDown(AbstractBuild build, BuildListener listener)
                throws IOException, InterruptedException {
            return true;
        }
    }
}
