package org.openstack.murano.jenkins_plugins.muranoci.deploy.model;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

public class MuranoYaml {

    @JsonProperty
    private Map<String, EnvironmentDescription> environments = null;

    public MuranoYaml() {
    }

    public MuranoYaml(Map<String, EnvironmentDescription> environments) {
        this.environments = environments;
    }

    public Map<String, EnvironmentDescription> getEnvironments() {
        return environments;
    }

    public void setEnvironments(HashMap<String, EnvironmentDescription> environments) {
        this.environments = environments;
    }
}
