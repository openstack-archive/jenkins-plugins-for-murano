package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.LinkedList;
import java.util.List;

public abstract class MuranoDeployment
        implements Describable<MuranoDeployment>, ExtensionPoint {

    public MuranoDeployment(String deploymentName) {

    }

    /**
     * Boilerplate, see: https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
     */
    @Override
    public Descriptor<MuranoDeployment> getDescriptor() {
        return (Descriptor<MuranoDeployment>) Jenkins.getInstance().getDescriptor(getClass());
    }

    /**
     * Boilerplate, see: https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
     */
    public static DescriptorExtensionList<MuranoDeployment, AbstractMuranoDeploymentDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(MuranoDeployment.class);
    }

    public static List<AbstractMuranoDeploymentDescriptor> getCompatibleDeployments(
            Descriptor descriptor) {
        LinkedList<AbstractMuranoDeploymentDescriptor> cloudDeployments =
                new LinkedList<>();

        for (AbstractMuranoDeploymentDescriptor deployment : all()) {
            if (!deployment.isApplicable(descriptor)) {
                continue;
            }
            cloudDeployments.add(deployment);
        }

        return cloudDeployments;
    }
}
