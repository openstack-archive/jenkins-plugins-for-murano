package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import hudson.model.Descriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

import static java.util.Objects.requireNonNull;

public abstract class AbstractMuranoDeploymentDescriptor
        extends Descriptor<MuranoDeployment> {

    protected AbstractMuranoDeploymentDescriptor(Class<? extends MuranoDeployment> clazz) {
        super(requireNonNull(clazz));
        load();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean configure(StaplerRequest req, JSONObject json) throws FormException {
        json = json.getJSONObject(getDisplayName());
//        rootUrl = json.getString("rootUrl");
//        servicePath = json.getString("servicePath");
        save();
        return true;
    }

    public abstract boolean isApplicable(Descriptor descriptor);
    public abstract String getDisplayName();
}
