package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import hudson.DescriptorExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;

public abstract class MuranoDeployment
        implements Describable<MuranoDeployment>, ExtensionPoint {
    /**
     * Json data that describes Murano Environment applications
     */
    private String objectModel;


    public MuranoDeployment() {
        super();
    }

    /**
     * Contains data that describes Environment within Openstack Cloud
     * and connection credentials.
     *
     * @param objectModel description of environment to be deployed
     */
    public MuranoDeployment(String objectModel) {

        this.objectModel = requireNonNull(objectModel, "Object Model should not be Null");
    }

    /**
     * Boilerplate, see:
     * https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
     *
     * @return all registered {@link MuranoDeployment}s
     */
    public static DescriptorExtensionList<MuranoDeployment, AbstractMuranoDeploymentDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(MuranoDeployment.class);
    }

    public static List<AbstractMuranoDeploymentDescriptor> getCompatibleDeployments(Descriptor descriptor) {
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

    public String getObjectModel() {
        return objectModel;
    }

    public void setObjectModel(String objectModel) {
        this.objectModel = objectModel;
    }

    /**
     * Boilerplate, see: https://wiki.jenkins-ci.org/display/JENKINS/Defining+a+new+extension+point
     */
    @Override
    public Descriptor<MuranoDeployment> getDescriptor() {
        return (Descriptor<MuranoDeployment>) Jenkins.getInstance().getDescriptor(getClass());
    }

}
