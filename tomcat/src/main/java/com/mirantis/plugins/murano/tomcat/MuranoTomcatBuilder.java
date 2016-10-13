package com.mirantis.plugins.murano.tomcat;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUser;
import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.mirantis.plugins.murano.tomcat.client.OpenstackClient;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.io.output.TeeOutputStream;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.openstack4j.connectors.httpclient.HttpCommand;
import org.openstack4j.core.transport.HttpMethod;
import org.openstack4j.core.transport.HttpRequest;
import org.yaml.snakeyaml.Yaml;

import javax.servlet.ServletException;
import java.io.*;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by raja on 20/09/16.
 */
public class MuranoTomcatBuilder extends Notifier {
    private static String privateKeyIdentifier = "muranodemo";
    private String serverUrl;
    private String username;
    private String password;
    private String tenantName;
    private String flavor;
    private String keypairs;
    private String images;
    private String clusterName;
    private String playbook;
    private OpenstackClient client;

    @DataBoundConstructor
    public MuranoTomcatBuilder(String serverUrl,
                         String username,
                         String password,
                         String tenantName,
                         String clusterName,
                         String flavor,
                         String keypairs,
                         String images,
                         String playbook) {
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.tenantName = tenantName;
        this.flavor = flavor;
        this.keypairs = keypairs;
        this.images = images;
        this.clusterName = clusterName;
        this.playbook = playbook;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getTenantName() {
        return tenantName;
    }

    public String getFlavor() {
        return flavor;
    }

    public String getImages() {
        return this.images;
    }

    public String getKeypairs() {
        return this.keypairs;
    }

    public String getPlaybook() {
        return this.playbook;
    }

    public String getClusterName() {
        return this.clusterName;
    }


    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) throws IOException, InterruptedException {

        client = new OpenstackClient(serverUrl,
                username,
                password,
                tenantName);
        if (!client.authenticate()) {
            listener.getLogger().println("Cannot connect to Openstack server. Pls check configuration");
            return false;
        }

        // Get the images to cache them
        client.getImages();

        FilePath workspace = build.getWorkspace();
        FilePath privKeyFile = null;
        StandardUsernameCredentials c = CredentialsProvider.findCredentialById(privateKeyIdentifier, StandardUsernameCredentials.class, build);
        if (c instanceof SSHUserPrivateKey) {
            SSHUserPrivateKey privateKey = (SSHUserPrivateKey) c;
            List<String> keys = privateKey.getPrivateKeys();
            for (String s: keys) {
                privKeyFile = workspace.createTextTempFile("ssh", ".key", s, false);
                privKeyFile.chmod(0400);
            }
        }

        // Get the UI Definition for Tomcat. This is hardcoded based on the Tomcat Definition in Murano
        // TODO: See if this can be changed dynamically
        String token = client.getOSClient().getAccess().getToken().getId();
        HttpRequest request = HttpRequest.builder().method(HttpMethod.GET)
                .endpoint(serverUrl + ":9292")
                .path("/v3/artifacts/murano/v1/725cb06b-c69b-46c0-8768-e2f5cdb14e30/ui_definition/download") // Tomcat id
                .header("X-Auth-Token", token)
                .build();
        HttpCommand command = HttpCommand.create(request);
        CloseableHttpResponse response = null;
        try {
            response = command.execute();
        } catch(Exception ex) {
            ex.printStackTrace();;
            return false;
        }
        if (response == null) {
            listener.getLogger().println("Error getting response for UI definition");
            return false;
        }

        StringBuffer jsonString = new StringBuffer();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    response.getEntity().getContent()));

            //Print the raw output of murano api from the server
            String output;

            while ((output = br.readLine()) != null) {
                jsonString.append(output + "\n");
            }
        } catch(Exception ex) {
            listener.getLogger().println("Error getting output for UI definition");
            return false;
        }

        // Fill in the templates according to the jenkins job
        // TODO: Could be done only if the image needs to be created. Otehrwise its a waste of resouces.
        Yaml yaml = new Yaml();
        String appTemplate = "";
        ConfigurationSection sections = new ConfigurationSection();
        Map templates = new HashMap();
        Map<String, Map<String, Object>> values = (Map<String, Map<String, Object>>) yaml.load(jsonString.toString());
        for (String key : values.keySet()) {
            System.out.println(key);
            if (key.equals("Forms")) {
                List<Map> formElements = (ArrayList<Map>)values.get(key);

                for (Map type: formElements) {
                    String typeName = (String)type.keySet().toArray()[0]; // gets the form types like : appConfiguration, instanceConfiguration etc

                    Map<String, Map<String, String>> fields = (HashMap<String, Map<String,String>>) type.get(typeName);
                    List<Map> fieldArr = (ArrayList<Map>)fields.get("fields");

                    sections.addSection(typeName, fieldArr);

                }
            } else if (key.equals("Application")) {
                System.out.println("Application section : ");
                Map<String, Object> applicationTemplate = (Map<String, Object>)values.get(key);
                appTemplate = "{" + getApplicationJsonTemplate(applicationTemplate) + "}";

            } else if (key.equals("Templates")) {
                System.out.println("Templates");
                Map<String, Object> templateMap = values.get(key);
                for(String templateName: templateMap.keySet()) {
                    Object templateValue = templateMap.get(templateName);
                    if (templateValue instanceof Map) {
                        Map<String, Object> internalTemplateHash = (Map<String, Object>) templateValue;
                        //String templateExpanded = "{" + getApplicationJsonTemplate(internalTemplateHash) + "}";
                        templates.put(templateName, internalTemplateHash);
                    }
                }
            }
        }

        String envId = checkIfDeploymentExists(token, this.clusterName);
        if (envId == null) {
            listener.getLogger().println("Creating new enviroment");
            // No Environment, create the cluster
            String filledTemplate = fillTemplate(appTemplate, build.getId());

            // Create Env
            envId = this.createEnvironment(token, this.clusterName);

            // Create Session
            String sessionId = this.createEnvironmentSession(token, envId);

            // Add App to Environment
            addApplicationToEnvironment(token, envId, sessionId, filledTemplate);

            // Deploy
            deployEnvironment(token, envId, sessionId);
            listener.getLogger().println("Waiting for Deployment to finish...");
            if (!isDeploymentSuccess(token, envId)) {
                listener.getLogger().println("Taking longer to deploy, please check the murano console");
                return false;
            }
            listener.getLogger().println("Deployed Tomcat");
        }


        //  Get the Floating IP to deploy the image
        String floatingIp = null;
        try {
            floatingIp = getFloatingIp(token, envId);
        } catch(Exception ex) {
            listener.getLogger().println("Error getting Floating ip");
            return false;
        }

        // Call Ansible Playbook to execute
        return callAnsiblePlaybook(launcher, floatingIp, privKeyFile, workspace, listener.getLogger());

    }

    private List<String> generateCommandArgs(FilePath privateKeyPath,
                                             FilePath workspace,
                                             String floatingIp) {
        List<String> args = new ArrayList<String>();

        // Build command line

        // TODO: See if ansible-playbook is available somewhere else
        String ansiblePlaybook = "ansible-playbook";

        args.add(ansiblePlaybook);

        // --private-key
        if (privateKeyPath != null) {
            args.add("--private-key=" + privateKeyPath.toString());
        }

        // Add inventory information
        args.add("-i");
        args.add(floatingIp + ",");

        args.add("-e");
        args.add("localFile=" + new FilePath(workspace, "./target/petclinic.war"));
        args.add(new FilePath(workspace, this.playbook).toString());

        return args;
    }

    private boolean callAnsiblePlaybook(Launcher launcher,
                                        String floatingIp,
                                        FilePath privKeyFile,
                                        FilePath workspace,
                                        PrintStream jenkinsLogger) throws IOException, InterruptedException {
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
        TeeOutputStream stdoutTee = new TeeOutputStream(stdoutStream, jenkinsLogger);
        TeeOutputStream stderrTee = new TeeOutputStream(stderrStream, jenkinsLogger);

        // Build a launcher
        Launcher.ProcStarter settings = launcher.launch();
        settings.cmds(this.generateCommandArgs(privKeyFile, workspace, floatingIp));
        settings.stdout(stdoutTee);
        settings.stderr(stderrTee);
        // settings.pwd(containingFolder);
        // settings.envs(joinedEnvVars);

        // Launch
        Proc proc = settings.start();

        // Wait for exit and capture outputs
        int exitCode = proc.join();
        String stdout = stdoutStream.toString();
        String stderr = stderrStream.toString();

        return exitCode == 0;
    }

    private boolean isDeploymentSuccess(String token, String envId) {
        boolean status = false;
        for (int i=0; i<300; i++) {

            try {
                Thread.sleep(10000);
                String payload = getResponseForJsonPost(serverUrl,
                        8082,
                        "/v1/environments/" + envId + "/deployments",
                        HttpMethod.GET,
                        token,
                        null,
                        null);
                JSONParser parser = new JSONParser();
                try {
                    JSONObject deployments = (JSONObject) parser.parse(payload);
                    JSONArray deploymentList = (JSONArray) deployments.get("deployments");
                    JSONObject thisDeployment = (JSONObject) deploymentList.get(0);
                    if ("success".equals((String) thisDeployment.get("state"))) {
                        status = true;
                        break;
                    }
                } catch (ParseException pe) {
                    System.out.println("position: " + pe.getPosition());
                    System.out.println(pe);
                }
            } catch (Exception ex) {
                status = false;
                break;
            }
        }
        return status;
    }

    private String getFloatingIp(String token, String envId) throws Exception {
        String payload = getResponseForJsonPost(serverUrl,
                8082,
                "/v1/environments/" + envId + "/services",
                HttpMethod.GET,
                token,
                null,
                null);
        String floatingIp = null;
        if (payload != null) {
            JSONParser parser = new JSONParser();
            JSONArray array = (JSONArray) parser.parse(payload);
            floatingIp =   (String) ((JSONObject) ((JSONObject) array.get(0)).get("instance")).get("floatingIpAddress");
        }

        return floatingIp;
    }
    /**
     * Return the Environment id if it exists
     *
     * @param token
     * @param name
     * @return
     */
    private String checkIfDeploymentExists(String token, String name) {
        String payload = getResponseForJsonPost(serverUrl,
                8082,
                "/v1/environments",
                HttpMethod.GET,
                token,
                null,
                null);
        String envId = null;
        JSONParser parser = new JSONParser();
        try{
            Object obj = parser.parse(payload);
            JSONObject response = (JSONObject)obj;
            JSONArray environmentArray =  (JSONArray) response.get("environments");
            for (Object env: environmentArray) {
                JSONObject thisEnv = (JSONObject) env;
                String envName = (String) thisEnv.get("name");
                if (envName.equals(name)) {
                    envId = (String) thisEnv.get("id");
                    break;
                }
            }
        }catch(ParseException pe){
            System.out.println("position: " + pe.getPosition());
            System.out.println(pe);
        }
        return envId;
    }

    /**
     * Deploy the environment given the environment id and Session Token
     */
    private void deployEnvironment(String token, String envId, String sessionId) {
        String response = getResponseForJsonPost(serverUrl,
                8082,
                "/v1/environments/" + envId + "/sessions/" + sessionId + "/deploy",
                HttpMethod.POST,
                token,
                null,
                null);
    }

    /**
     * Add the app(K8S) to the environment
     * @param token
     * @param envId
     * @param sessionId
     * @param jsonReq
     */
    private void addApplicationToEnvironment(String token, String envId, String sessionId, String jsonReq) {
        String response = getResponseForJsonPost(serverUrl,
                8082,
                "/v1/environments/" + envId + "/services",
                HttpMethod.POST,
                token,
                jsonReq,
                sessionId);
        System.out.println("add application response  : " + response);
        //return sessionId;
    }

    private String createEnvironmentSession(String token, String envId) {
        String payload = getResponseForJsonPost(serverUrl,
                8082,
                "/v1/environments/" + envId + "/configure",
                HttpMethod.POST,
                token,
                null,
                null);

        String sessionId = "";
        JSONParser parser = new JSONParser();
        try{
            Object obj = parser.parse(payload);
            JSONObject response = (JSONObject)obj;
            sessionId = (String)response.get("id");
        }catch(ParseException pe){
            System.out.println("position: " + pe.getPosition());
            System.out.println(pe);
        }
        System.out.println("Session Id : " + sessionId);
        return sessionId;
    }

    private String createEnvironment(String token, String envname) {
        String reqPayload = "{\"name\":\"" + envname + "\"}";
        String payload = getResponseForJsonPost(serverUrl, 8082, "/v1/environments", HttpMethod.POST, token, reqPayload, null);

        String envId = "";
        JSONParser parser = new JSONParser();
        try{
            Object obj = parser.parse(payload);
            JSONObject response = (JSONObject)obj;
            envId = (String)response.get("id");
        }catch(ParseException pe){
            System.out.println("position: " + pe.getPosition());
            System.out.println(pe);
        }
        System.out.println("Envid : " + envId);
        return envId;
    }

    /**
     * Main helper method to call the Murano API and return the response. Accepts both GET and POST.
     *
     * @param url Base URL to connect
     * @param port Which port is murano listening on
     * @param requestPath Path on Murano URL
     * @param method GET or POST
     * @param token Auth Token
     * @param jsonPayload Payload for the message
     * @param muranoSessionId Optional Session Id
     * @return Response from the Call
     */
    private  String getResponseForJsonPost(String url,
                                           int port,
                                           String requestPath,
                                           HttpMethod method,
                                           String token,
                                           String jsonPayload,
                                           String muranoSessionId) {
        HttpRequest request = HttpRequest.builder().method(method)
                .endpoint(url + ":" + port)
                .path(requestPath) // K8S cluster id
                .header("X-Auth-Token", token)
                .json(jsonPayload)
                .build();
        if (muranoSessionId != null) {
            request.getHeaders().put("X-Configuration-Session", muranoSessionId);
        }
        if (jsonPayload != null) {
            request = request.toBuilder().json(jsonPayload).build();
        }

        HttpCommand command = HttpCommand.create(request);
        CloseableHttpResponse response = null;
        try {
            response = command.execute();
        } catch(Exception ex) {
            ex.printStackTrace();;
            return null;
        }

        StringBuffer jsonString = new StringBuffer();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(
                    response.getEntity().getContent()));

            //Print the raw output of murano api from the server
            String output;

            while ((output = br.readLine()) != null) {
                jsonString.append(output + "\n");
            }
        } catch(Exception ex) {
            return null;
        }

        return jsonString.toString();
    }

    private String generateUUID() {
        SecureRandom ng = new SecureRandom();
        long MSB = 0x8000000000000000L;

        String str = Long.toHexString(MSB | ng.nextLong()) + Long.toHexString(MSB | ng.nextLong());
        return str;

    }

    private String fillTemplate(String template, String buildId) {
        String appTemplate = template.replace("$.appConfiguration.name", "Tomcat_" + buildId);

        appTemplate = appTemplate.replace("$.instanceConfiguration.osImage", client.getImageId(this.images));
        appTemplate = appTemplate.replace("$.instanceConfiguration.flavor", flavor);
        appTemplate = appTemplate.replace("$.appConfiguration.assignFloatingIP", "True");
        appTemplate = appTemplate.replace("$.instanceConfiguration.keyPair", this.keypairs);
        appTemplate = appTemplate.replace("$.instanceConfiguration.availabilityZone", "nova");

        return appTemplate;
    }

    private String getApplicationJsonTemplate(Map<String,Object> template) {
        String templateStr = "";
        boolean firstStr = true;
        for (String k : template.keySet()) {
            if (!firstStr) {
                templateStr += ",";
            }
            firstStr = false;
            Object v = template.get(k);
            if (v instanceof Map) {
                templateStr += "\"" + k + "\": {" + this.getApplicationJsonTemplate((Map<String, Object>)v) ;
                templateStr += "}";
            } else if (v instanceof String) {
                String uuid = generateUUID();
                if (k.equals("type")) {
                    templateStr += "\"" + k + "\":\"" + v + "\"";
                    templateStr += ",\"id\":\"" + uuid + "\"";
                }else if (k.equals("name") && (((String)v).contains("generateHostname"))) {
                    templateStr += "\"name\":\"$.instanceConfiguration.unitNamingPattern-" + uuid + ".com\"";
                }else {
                    templateStr += "\"" + k + "\":\"" + v + "\"";
                }
            }
        }
        return templateStr;
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }


        public FormValidation doCheckServerUrl(@QueryParameter String serverUrl) {
            if (serverUrl.length() == 0)
                return FormValidation.error("Please enter a server url");

            if (serverUrl.indexOf("://") == -1)
                return FormValidation.error("Enter a url of the format http(s)://<server>");

            return FormValidation.ok();
        }

        public FormValidation doTestConnection(@QueryParameter("serverUrl") final String serverUrl,
                                               @QueryParameter("username") final String username,
                                               @QueryParameter("password") final String password,
                                               @QueryParameter("tenantName") final String tenantName)
                throws IOException, ServletException {
            try {
                OpenstackClient client = new OpenstackClient(serverUrl,
                        username,
                        password,
                        tenantName);
                if (client.authenticate()) {
                    return FormValidation.ok("Success");
                } else {
                    return FormValidation.error("Unable to connect to server. Please check credentials");
                }
            } catch (Exception e) {
                return FormValidation.error("Error: "+e.getMessage());
            }
        }

        public ListBoxModel doFillFlavorItems(@QueryParameter("serverUrl") final String serverUrl,
                                              @QueryParameter("username") final String username,
                                              @QueryParameter("password") final String password,
                                              @QueryParameter("tenantName") final String tenantName) {
            ListBoxModel flavor = new ListBoxModel();
            OpenstackClient client = new OpenstackClient(serverUrl,
                    username,
                    password,
                    tenantName);
            client.authenticate();      // Authenticate to Openstack
            if (client.getOSClient() != null) {
                for (String fl: client.getFlavors()) {
                    flavor.add(fl);
                }
            }
            return flavor;
        }

        public ListBoxModel doFillKeypairsItems(@QueryParameter("serverUrl") final String serverUrl,
                                                @QueryParameter("username") final String username,
                                                @QueryParameter("password") final String password,
                                                @QueryParameter("tenantName") final String tenantName) {
            ListBoxModel keypairs = new ListBoxModel();
            OpenstackClient client = new OpenstackClient(serverUrl,
                    username,
                    password,
                    tenantName);
            client.authenticate();      // Authenticate to Openstack
            if (client.getOSClient() != null) {
                for (String fl: client.getKeypairs()) {
                    keypairs.add(fl);
                }
            }
            return keypairs;
        }

        public ListBoxModel doFillImagesItems(@QueryParameter("serverUrl") final String serverUrl,
                                                @QueryParameter("username") final String username,
                                                @QueryParameter("password") final String password,
                                                @QueryParameter("tenantName") final String tenantName) {
            ListBoxModel images = new ListBoxModel();
            OpenstackClient client = new OpenstackClient(serverUrl,
                    username,
                    password,
                    tenantName);
            client.authenticate();      // Authenticate to Openstack
            if (client.getOSClient() != null) {
                for (String image: client.getImages()) {
                    images.add(image);
                }
            }
            return images;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Murano Tomcat Plugin";
        }
    }
}

