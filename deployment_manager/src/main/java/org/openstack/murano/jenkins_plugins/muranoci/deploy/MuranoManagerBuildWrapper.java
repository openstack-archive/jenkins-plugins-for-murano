package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

import static java.util.Objects.requireNonNull;


public class MuranoManagerBuildWrapper extends BuildWrapper {

    @DataBoundConstructor
    public MuranoManagerBuildWrapper() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MuranoDeployment setUp(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        EnvVars environment = build.getEnvironment(listener);

        return new MuranoDeployment(environment);
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
    }

    private final class MuranoDeployment extends Environment {
        private final EnvVars envVars;

        /**
         * Construct the instance with a snapshot of the environment within which it was created in case
         * values that were used to configure it at the start of the build change before the end.
         *
         * @param envVars The set of environment variables used to spin up the ephemeral deployment, so
         *                we can tear it down with the same.
         */
        public MuranoDeployment(EnvVars envVars) {
            this.envVars = requireNonNull(envVars);
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
