package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;

/**
 * After a successful build, this plugin deploys to Murano Environment via the
 * Deployment Manager API.
 */
public class MuranoManagerDeployer extends Recorder {

    @DataBoundConstructor
    public MuranoManagerDeployer() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener)
            throws IOException, InterruptedException {
        if (build.getResult() != Result.SUCCESS) {
            return true;
        }

        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }


    @Extension(ordinal = -1.0)
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.MuranoManagerDeployer_DisplayName();
        }
    }

    /**
     * {@link BuildStepDetailsProvider} for the Cloud Manager Deployer.
     */
    @Extension
    public static class DetailsProvider extends BuildStepDetailsProvider<MuranoManagerDeployer> {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDetails(MuranoManagerDeployer deployer) {
            return "MuranoManagerDeployer";
        }
    }
}
