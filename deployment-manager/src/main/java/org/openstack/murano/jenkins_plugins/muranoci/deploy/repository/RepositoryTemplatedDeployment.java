package org.openstack.murano.jenkins_plugins.muranoci.deploy.repository;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.openstack.murano.jenkins_plugins.muranoci.deploy.AbstractMuranoDeploymentDescriptor;
import org.openstack.murano.jenkins_plugins.muranoci.deploy.Messages;
import org.openstack.murano.jenkins_plugins.muranoci.deploy.MuranoDeployment;
import org.openstack.murano.jenkins_plugins.muranoci.deploy.model.EnvironmentDescription;

import java.io.IOException;

public class RepositoryTemplatedDeployment extends MuranoDeployment {

    /**
     * An environment description name in a config
     */
    private final String environment;

    /**
     * The specific Implemenation of <code>MuranoDeployment</code> that
     * gets object model from the file within the repo.
     *
     * @param environment The name of the environment within the .murano.yml config
     */
    @DataBoundConstructor
    public RepositoryTemplatedDeployment(String environment) {

        this.environment = environment;
    }

    public String getEnvironment() {
        return environment;
    }

    /**
     * Denotes that this is a cloud deployment plugin.
     */
    @Extension
    public static class DescriptorImpl extends AbstractMuranoDeploymentDescriptor {
        public DescriptorImpl() {
            this(RepositoryTemplatedDeployment.class);
        }

        public DescriptorImpl(Class<? extends RepositoryTemplatedDeployment> clazz) {
            super(clazz);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.RepositoryTemplatedMuranoDeployment_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Descriptor descriptor) {
            return true;
        }
    }

    /**
     * Read object model from a file pointed at current job param.
     * The file should be placed at the repo and th3e repo should be
     * fetched before this stage
     *
     * @param workspace working dir for a current Jenkins-job
     * @throws IOException file reading error
     * @throws InterruptedException file reading error
     */
    public void readObjectModel(FilePath workspace) throws IOException, InterruptedException {
        MuranoCiConfig config = MuranoCiConfig.read(workspace);

        EnvironmentDescription description = config.getData().getEnvironments().get(this.environment);

        String modelPath = description.getModelFile();
        String objectModel = new FilePath(workspace, modelPath).readToString();

        this.setObjectModel( objectModel);
    }
}
