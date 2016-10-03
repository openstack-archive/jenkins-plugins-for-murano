package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.tasks.BuildStep;
import hudson.tasks.Shell;


public abstract class BuildStepDetailsProvider<T extends BuildStep> implements ExtensionPoint {

    protected static String defaultName(BuildStep bs) {
        return bs instanceof Describable<?> ? ((Describable<?>) bs).getDescriptor().getDisplayName()
                : null;
    }

    /**
     * @param bs A given {@link BuildStep}.
     * @return the details of the build step.
     */
    public abstract String getDetails(T bs);

    /**
     * {@link BuildStepDetailsProvider} for {@link Shell}.
     */
    @Extension
    public static class ShellBuildStepDetailsProvider extends BuildStepDetailsProvider<Shell> {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDetails(Shell shell) {
            return shell.getCommand();
        }
    }
}
