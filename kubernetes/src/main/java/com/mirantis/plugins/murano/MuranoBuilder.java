package com.mirantis.plugins.murano;


import com.mirantis.plugins.murano.client.OpenstackClient;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import hudson.tasks.Publisher;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.ReplicationController;
import io.fabric8.kubernetes.api.model.ReplicationControllerBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import okhttp3.ConnectionSpec;
import okhttp3.OkHttpClient;
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
import java.util.*;

/**
 * Main Jenkins plugin class that acts as a PostBuilder
 */
public class MuranoBuilder extends Notifier {

    private String serverUrl;
    private String username;
    private String password;
    private String tenantName;
    private String dockerImage;
    private String flavor;
    private String keypairs;
    private String clusterName;
    private String slaveCount;
    private String gatewayCount;

    @DataBoundConstructor
    public MuranoBuilder(String serverUrl,
                         String username,
                         String password,
                         String tenantName,
                         String clusterName,
                         String dockerRegistry,
                         String flavor,
                         String keypairs,
                         String dockerImage,
                         String slaveCount,
                         String gatewayCount) {
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.tenantName = tenantName;
        this.dockerImage = dockerImage;
        this.clusterName = clusterName;
        this.flavor = flavor;
        this.keypairs = keypairs;
        this.slaveCount = slaveCount;
        this.gatewayCount = gatewayCount;
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

    public String getDockerImage() {
        return dockerImage;
    }

    public String getFlavor() {
        return flavor;
    }

    public String getClusterName() {
        return clusterName;
    }

    public String getGatewayCount() {
        return gatewayCount;
    }

    public String getSlaveCount() {
        return slaveCount;
    }

    /**
     * Postbuild implementation, gets the parameters from the values filled in the Jenkins
     * Configuration.
     *
     * It the uses the Environment name to check if the environment exists and if it does, uses the
     * same environment to deploy the image. This also assumes that a build solution is available that
     * pushes the Jenkins build to dockerhub or whatever Docker registry has been configured. Thats out
     * of scope of this plugin to build the Docker image.
     *
     * If there is no environment available, it will build the K8S environment and then use it to
     * deploy the image.
     *
     * @param build The Build object from Jenkins
     * @param launcher The Launcher
     * @param listener Listener for Logging events
     * @return whether the post-build was successful
     * @throws IOException
     * @throws InterruptedException
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher,
                           BuildListener listener) throws IOException, InterruptedException {

        // Replace the image so that $BUILD_NUMBER is replaced with an actual image. This is because
        // we tag the docker images with the build numbers
        String image = Util.replaceMacro(this.dockerImage, build.getEnvironment(listener));

        OpenstackClient client = new OpenstackClient(serverUrl,
                username,
                password,
                tenantName);
        if (!client.authenticate()) {
            listener.getLogger().println("Cannot connect to Openstack server. Pls check configuration");
            return false;
        }

        // Get the UI Definition for Kubernetes. This is hardcoded based on the K8S Definition in Murano
        // TODO: See if this can be changed dynamically
        String token = client.getOSClient().getAccess().getToken().getId();
        HttpRequest request = HttpRequest.builder().method(HttpMethod.GET)
                .endpoint(serverUrl + ":9292")
                .path("/v3/artifacts/murano/v1/f07d4447-d479-401d-86f2-902f3e260f60/ui_definition/download") // K8S cluster id
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
            String filledTemplate = fillTemplatesForKubernetes(templates,
                    appTemplate,
                    build.getId(),
                    this.flavor,
                    this.keypairs);

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
            listener.getLogger().println("Deployed K8S");
        }


        //  Get the Floating IP to deploy the image
        String kubeMasterAddress = null;
        try {
            kubeMasterAddress = getFloatingIp(token, envId);
            kubeMasterAddress = "http://" + kubeMasterAddress + ":8080";       // By default the mirantis instance runs on 8080
        } catch(Exception ex) {
            listener.getLogger().println("Error getting Floating ip");
            return false;
        }

        return deployImage(kubeMasterAddress, this.clusterName, image, listener.getLogger());
    }

    /**
     * Loop around to see if the deployment is a success. This waits for about 10 secs 300 times hoping that
     * it finishes. This all depends on teh number of nodes and the speed of the boxes. But seems sufficient.
     *
     * @param token Environment Token
     * @param envId Environemnt Id
     * @return whether the deployment is a success
     */
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

    // ServiceName is the same as Cluster Name
    private boolean deployImage(String floatingIp, String serviceName, String image, PrintStream logger) {
        Config config = new ConfigBuilder()
                .withMasterUrl(floatingIp)
                .withNamespace("default")       // Use default namespace
                .build();

        // Redo the dance till the cleartext is setup with kubernetes libraries.
        // TODO: This is fixed in https://github.com/fabric8io/kubernetes-client/issues/498
        DefaultKubernetesClient client = new DefaultKubernetesClient(config);
        ArrayList<ConnectionSpec> specs = new ArrayList<ConnectionSpec>();
        specs.add(ConnectionSpec.CLEARTEXT);
        OkHttpClient httpClient = client.getHttpClient().newBuilder().connectionSpecs(specs).build();
        client = new DefaultKubernetesClient(httpClient, config);

        Service service = client.services().withName(serviceName).get();
        if (service == null) {
            // There is no service, create one with port on 9999 targeting 8080 internally
            logger.println("There is no service named: " + serviceName + ", Creating one");
            HashMap<String,String> labels = new HashMap<String,String>();
            labels.put("server", "javawebapp");
            client.services().createNew().
                    withNewMetadata().withName(serviceName).endMetadata().
                    withNewSpec().
                    addNewPort().withPort(9999).withNewTargetPort().withIntVal(8080).endTargetPort().endPort().
                    withSelector(labels).
                    withType("NodePort").
                    endSpec().
                    done();
            service = client.services().withName(serviceName).get();
            if (service == null) {
                logger.print("Still cannot create servce, Aborting");
                return false;
            }
        } else {
            logger.println("Obtained Service with name: " + serviceName);
        }

        // Create the RC
        logger.print("Creating Replication Controller now");

        ReplicationController rc = client.replicationControllers().withName(serviceName + "-rc").get();
        if (rc == null) {
            logger.println("No RC Found with name: " + serviceName + "-rc, Creating one");
            rc = new ReplicationControllerBuilder().
                    withNewMetadata().withName(serviceName + "-rc").addToLabels("server", "javawebapp").endMetadata().
                    withNewSpec().withReplicas(1).
                    withNewTemplate().
                    withNewMetadata().addToLabels("server", "javawebapp").endMetadata().
                    withNewSpec().
                    addNewContainer().withName(serviceName).withImage(image).
                    addNewPort().withContainerPort(8080).endPort().
                    endContainer().
                    endSpec().
                    endTemplate().
                    endSpec().
                    build();
            client.replicationControllers().create(rc);
            logger.println("Created image with: " + image);
        } else {
            logger.println("RC Found with name: " + serviceName + "-rc, updating with new image");
            client.replicationControllers().
                    withName(serviceName + "-rc").
                    rolling().updateImage(image);
            logger.println("Updated image to : " + image);
        }
        return true;
    }


    /* Standard output is of form
       [{"gatewayCount": 1, "gatewayNodes": [{"instance": {"availabilityZone": "nova", "openstackId": "184c7bca-b757-429e-8565-96a4b32927c8", "name": "$.instanceConfiguration.unitNamingPattern-e094401173c7716f88cc36d62cbb31fe.com", "securityGroupName": null, "image": "ubuntu14.04-x64-kubernetes", "assignFloatingIp": true, "floatingIpAddress": "172.17.10.116", "keyname": "Raja_macbookpro", "?": {"classVersion": "0.0.0", "name": null, "package": "io.murano", "type": "io.murano.resources.LinuxMuranoInstance", "_actions": {}, "id": "ed3fc4457be3056cf500fcd216dbf25c"}, "ipAddresses": ["10.0.31.5", "172.17.10.116"], "flavor": "m1.medium", "networks": {"useFlatNetwork": false, "primaryNetwork": null, "useEnvironmentNetwork": true, "customNetworks": []}, "sharedIps": []}, "?": {"classVersion": "0.0.0", "name": null, "package": "io.murano.apps.docker.kubernetes.KubernetesCluster", "type": "io.murano.apps.docker.kubernetes.KubernetesGatewayNode", "_actions": {}, "id": "b8b3ebc3dd3cce4deed702c48c9475bf"}}], "?": {"classVersion": "0.0.0", "status": "ready", "name": null, "package": "io.murano.apps.docker.kubernetes.KubernetesCluster", "type": "io.murano.apps.docker.kubernetes.KubernetesCluster", "_actions": {"e7c2d5b5cf8291fdb0149188f2b8b9ee_scaleNodesUp": {"enabled": true, "name": "scaleNodesUp"}, "e7c2d5b5cf8291fdb0149188f2b8b9ee_scaleGatewaysDown": {"enabled": true, "name": "scaleGatewaysDown"}, "e7c2d5b5cf8291fdb0149188f2b8b9ee_scaleGatewaysUp": {"enabled": true, "name": "scaleGatewaysUp"}, "e7c2d5b5cf8291fdb0149188f2b8b9ee_exportConfig": {"enabled": true, "name": "exportConfig"}, "e7c2d5b5cf8291fdb0149188f2b8b9ee_scaleNodesDown": {"enabled": true, "name": "scaleNodesDown"}}, "id": "e7c2d5b5cf8291fdb0149188f2b8b9ee"}, "serviceEndpoints": [], "nodeCount": 1, "dockerRegistry": null, "masterNode": {"instance": {"availabilityZone": "nova", "openstackId": "ee92608e-8eb7-41ac-a53a-e8c5a8c107b4", "name": "$.instanceConfiguration.unitNamingPattern-92a53dbcd69636a793587c564e2f708a.com", "securityGroupName": null, "image": "ubuntu14.04-x64-kubernetes", "assignFloatingIp": true, "floatingIpAddress": "172.17.10.115", "keyname": "Raja_macbookpro", "?": {"classVersion": "0.0.0", "name": null, "package": "io.murano", "type": "io.murano.resources.LinuxMuranoInstance", "_actions": {}, "id": "dabbc3a6f0fa18d781ed88369b738a90"}, "ipAddresses": ["10.0.31.4", "172.17.10.115"], "flavor": "m1.medium", "networks": {"useFlatNetwork": false, "primaryNetwork": null, "useEnvironmentNetwork": true, "customNetworks": []}, "sharedIps": []}, "?": {"classVersion": "0.0.0", "name": null, "package": "io.murano.apps.docker.kubernetes.KubernetesCluster", "type": "io.murano.apps.docker.kubernetes.KubernetesMasterNode", "_actions": {}, "id": "882094bb08fa2f78c0607e613d79f428"}}, "minionNodes": [{"instance": {"availabilityZone": "nova", "openstackId": "db281114-019c-4b57-9fb1-11ae7e3b4fbc", "name": "$.instanceConfiguration.unitNamingPattern-d1b3affe5146ad9dcaa4a55d8d69725b.com", "securityGroupName": null, "image": "ubuntu14.04-x64-kubernetes", "assignFloatingIp": true, "floatingIpAddress": "172.17.10.117", "keyname": "Raja_macbookpro", "?": {"classVersion": "0.0.0", "name": null, "package": "io.murano", "type": "io.murano.resources.LinuxMuranoInstance", "_actions": {}, "id": "f890a089cbf645f8f3599fffb6553fb6"}, "ipAddresses": ["10.0.31.6", "172.17.10.117"], "flavor": "m1.medium", "networks": {"useFlatNetwork": false, "primaryNetwork": null, "useEnvironmentNetwork": true, "customNetworks": []}, "sharedIps": []}, "?": {"classVersion": "0.0.0", "name": null, "package": "io.murano.apps.docker.kubernetes.KubernetesCluster", "type": "io.murano.apps.docker.kubernetes.KubernetesMinionNode", "_actions": {}, "id": "d967c9cf3c53fd1deaae4c4af2de6379"}, "exposeCAdvisor": true}], "name": "K8ST1"}]
     */
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
            floatingIp =
                    (String) ((JSONArray) ((JSONObject) ((JSONObject) ((JSONObject) array.get(0)).get("masterNode")).
                            get("instance")).get("ipAddresses")).get(0);
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

    private String fillTemplatesForKubernetes(Map templates,
                                              String template,
                                              String buildId,
                                              String flavor,
                                              String keypair) {
        String appTemplate = template.replace("$.appConfiguration.name", "Kubernetes_" + buildId);
        appTemplate = appTemplate.replace("$.appConfiguration.minionCount", this.slaveCount);
        appTemplate = appTemplate.replace("$.appConfiguration.gatewayCount", this.gatewayCount);
        appTemplate = appTemplate.replace("dockerRegistry", "");

        // Setup KubeMaster
        Map<String,Object> masterTemplate = (Map<String,Object>) templates.get("masterNode");
        String kubeMasterTemplate = "{" + getApplicationJsonTemplate(masterTemplate) + "}";
        kubeMasterTemplate = kubeMasterTemplate.replaceAll("$.instanceConfiguration.unitNamingPattern-.*?.com", "K8sMaster" + buildId); //Name
        kubeMasterTemplate = kubeMasterTemplate.replace("$.instanceConfiguration.flavor", flavor);
        kubeMasterTemplate = kubeMasterTemplate.replace("$.appConfiguration.assignFloatingIP", "True");
        kubeMasterTemplate = kubeMasterTemplate.replace("$.instanceConfiguration.keyPair", keypair);
        kubeMasterTemplate = kubeMasterTemplate.replace("$.instanceConfiguration.availabilityZone", "nova");
        appTemplate = appTemplate.replace("\"masterNode\":\"$masterNode\"", "\"masterNode\":" +  kubeMasterTemplate);

        // Kube Minion
        StringBuffer sbMinionTemplate = new StringBuffer();
        String delim = "";

        for(int i = 1; i<= Integer.parseInt(this.slaveCount); i++) {
            Map<String,Object> minionTemplate = (Map<String,Object>) templates.get("minionNode");
            String kubeMinionTemplate = "{" + getApplicationJsonTemplate(minionTemplate) + "}";

            kubeMinionTemplate = kubeMinionTemplate.replaceAll("\\$.instanceConfiguration.unitNamingPattern-.*?.com", "K8sMinion-" + generateUUID()); //Name
            kubeMinionTemplate = kubeMinionTemplate.replace("$.instanceConfiguration.flavor", flavor);
            kubeMinionTemplate = kubeMinionTemplate.replace("$.appConfiguration.assignFloatingIP", "True");
            kubeMinionTemplate = kubeMinionTemplate.replace("$.instanceConfiguration.keyPair", keypair);
            kubeMinionTemplate = kubeMinionTemplate.replace("$.instanceConfiguration.availabilityZone", "nova");

            sbMinionTemplate.append(delim).append(kubeMinionTemplate);
            delim = ",";
        }
        appTemplate = appTemplate.replace("\"minionNodes\":\"repeat($minionNode, $.appConfiguration.maxMinionCount)\"", "\"minionNodes\": [" + sbMinionTemplate.toString() + "]");

        // Kube Gateway
        StringBuffer sbGatewayTemplate = new StringBuffer();
        delim = "";

        for(int i = 1; i<= Integer.parseInt(this.gatewayCount); i++) {
            Map<String,Object> gatewayTemplate = (Map<String,Object>) templates.get("gatewayNode");
            String gatewayMinionTemplate = "{" + getApplicationJsonTemplate(gatewayTemplate ) + "}";

            gatewayMinionTemplate = gatewayMinionTemplate.replaceAll("\\$.instanceConfiguration.unitNamingPattern-.*?.com", "K8sGateway-" + generateUUID()); //Name
            gatewayMinionTemplate = gatewayMinionTemplate.replace("$.instanceConfiguration.flavor", flavor);
            gatewayMinionTemplate = gatewayMinionTemplate.replace("$.appConfiguration.assignFloatingIP", "True");
            gatewayMinionTemplate = gatewayMinionTemplate.replace("$.instanceConfiguration.keyPair", keypair);
            gatewayMinionTemplate = gatewayMinionTemplate.replace("$.instanceConfiguration.availabilityZone", "nova");

            sbGatewayTemplate.append(delim).append(gatewayMinionTemplate);
            delim = ",";
        }
        appTemplate = appTemplate.replace("\"gatewayNodes\":\"repeat($gatewayNode, $.appConfiguration.maxGatewayCount)\"", "\"gatewayNodes\": [" + sbGatewayTemplate.toString() + "]");

        return appTemplate;
    }

    private String generateUUID() {
        SecureRandom ng = new SecureRandom();
        long MSB = 0x8000000000000000L;

        String str = Long.toHexString(MSB | ng.nextLong()) + Long.toHexString(MSB | ng.nextLong());
        return str;

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

        /**
         * This human readable name is used in the configuration screen.
         */
        public String getDisplayName() {
            return "Murano Kubernetes Plugin";
        }
    }
}

