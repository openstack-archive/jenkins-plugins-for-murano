package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import hudson.Extension;
import hudson.model.Descriptor;

import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.openstack4j.api.exceptions.AuthenticationException;

import javax.servlet.ServletException;
import java.io.IOException;

import static java.util.Objects.requireNonNull;

public class TemplatedDeployment extends MuranoDeployment {

    /**
     * The specific Implemenation of <code>MuranoDeployment</code> that
     * gets object model from the contructor parameter
     * (the direct textarea on Jenkins form)
     *
     * @param objectModel Object model for Murano environment to be deployed
     */
    @DataBoundConstructor
    public TemplatedDeployment(
            String objectModel) {
        super(requireNonNull(objectModel));
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
    }
}
