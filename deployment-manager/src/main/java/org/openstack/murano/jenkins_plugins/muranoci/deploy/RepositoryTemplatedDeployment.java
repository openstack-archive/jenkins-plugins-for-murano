package org.openstack.murano.jenkins_plugins.muranoci.deploy;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepositoryTemplatedDeployment extends MuranoDeployment {
    /**
     * The file in the repository that contains muranoci configuration.
     */
    public static final String CI_CONFG_FILENAME = ".murano.yml";

    /**
     *
     */
    private final String environment;

    /**
     * The specific Implemenation of <code>MuranoDeployment</code> that
     * gets object model from the file within the repo.
     *
     * @param environment The name of the environment within the .murano.yml config
     */
    @DataBoundConstructor
    public RepositoryTemplatedDeployment(
            String environment) {

        this.environment = environment;
    }

    public String getEnvironment() {
        return environment;
    }

    /**
     * Denotes that this is a cloud deployment plugin.
     */
    @Extension
    public static class DescriptorImpl extends AbstractMuranoDeploymentDescriptor {
        public DescriptorImpl() {
            this(RepositoryTemplatedDeployment.class);
        }

        public DescriptorImpl(Class<? extends RepositoryTemplatedDeployment> clazz) {
            super(clazz);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return Messages.RepositoryTemplatedMuranoDeployment_DisplayName();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isApplicable(Descriptor descriptor) {
            return true;
        }
    }


    public void readObjectModel(FilePath workspace) throws IOException {
        String config = null;
        try {
            config = new FilePath(workspace, ".murano.yml").readToString();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        YAMLFactory factory = new YAMLFactory();
        ObjectMapper mapper = new ObjectMapper(factory);
        HashMap<String, Object> map = mapper.readValue(config, HashMap.class);
        Object model = ((Map<String,Object>)((Map<String,Object>)map).get("environments")).get(this.environment);

        JsonFactory jsonFactory = new JsonFactory();
        ObjectMapper mapperModel = new ObjectMapper(jsonFactory);
        String string = mapperModel.writeValueAsString(model);
        System.out.println(string);
        this.setObjectModel(string);
    }
}
