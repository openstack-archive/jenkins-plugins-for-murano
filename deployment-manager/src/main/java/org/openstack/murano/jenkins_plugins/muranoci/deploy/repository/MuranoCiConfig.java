package org.openstack.murano.jenkins_plugins.muranoci.deploy.repository;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.snakeyaml.Yaml;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import hudson.FilePath;
import org.openstack.murano.jenkins_plugins.muranoci.deploy.model.MuranoYaml;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by kh on 10/3/16.
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
