package org.openstack.murano.jenkins_plugins.muranoci.deploy.model;

public class EnvironmentDescription {
    private String modelFile = null;

    public EnvironmentDescription() {
    }

    public EnvironmentDescription(String modelPath) {
        this.modelFile = modelPath;
    }

    public String getModelFile() {
        return modelFile;
    }

    public void setModelFile(String modelFile) {
        this.modelFile = modelFile;
    }
}
