package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import hudson.Extension;
import hudson.model.Descriptor;

import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class TemplatedDeployment extends MuranoDeployment {

    @DataBoundConstructor
    public TemplatedDeployment(
            String deploymentName) {
        super(deploymentName);
    }



    /**
     * Denotes that this is a cloud deployment plugin.
     */
    @Extension
    public static class DescriptorImpl extends AbstractMuranoDeploymentDescriptor {
        public DescriptorImpl() {
            this(TemplatedDeployment.class);
        }

        public DescriptorImpl(Class<? extends TemplatedDeployment> clazz) {
            super(clazz);
        }


        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.TemplatedMuranoDeployment_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Descriptor descriptor) {
            return true;
        }

        /**
         * Form validation for the {@code configFilePath} field.
         */
        public FormValidation doCheckConfigFilePath(@QueryParameter String configFilePath) {
            return FormValidation.ok();
        }
    }
}
