package org.openstack.murano.jenkins_plugins.muranoci.deploy.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import hudson.FilePath;
import org.openstack.murano.jenkins_plugins.muranoci.deploy.model.MuranoYaml;

import java.io.IOException;

/**
 * Class for getting information from a CI-config.
 */
public class MuranoCiConfig {
    /**
     * The file in the repository that contains muranoci configuration.
     */
    public static final String CI_CONFG_FILENAME = ".murano.yml";

    private final MuranoYaml data;

    public MuranoCiConfig(MuranoYaml data) {
        this.data = data;
    }

    public MuranoYaml getData() {
        return data;
    }

    public static MuranoCiConfig read(FilePath workspace) throws IOException {
        String configYaml = null;
        try {
            configYaml = new FilePath(workspace, CI_CONFG_FILENAME).readToString();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        final YAMLFactory factory = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        MuranoYaml data = mapper.readValue(configYaml, MuranoYaml.class);

        return new MuranoCiConfig(data);
    }
}
