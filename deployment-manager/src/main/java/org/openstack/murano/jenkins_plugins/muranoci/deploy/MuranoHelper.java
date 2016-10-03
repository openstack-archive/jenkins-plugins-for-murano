package org.openstack.murano.jenkins_plugins.muranoci.deploy;


import org.apache.http.client.methods.CloseableHttpResponse;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.connectors.httpclient.HttpCommand;
import org.openstack4j.core.transport.HttpMethod;
import org.openstack4j.core.transport.HttpRequest;
import org.openstack4j.openstack.OSFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class MuranoHelper {
    public static final int MURANO_DEFAULT_PORT = 8082;

    private final static Logger LOG = Logger.getLogger(MuranoHelper.class.getName());

    private OSClient.OSClientV2 os = null;

    /**
     * Suppose this is keystone Url
     */
    private String serverUrl;
    private String username;
    private String password;
    private String tenantName;
    /**
     * Default timeout for waiting deployment success
     */
    private int timeout = 3600*1000;

    @DataBoundConstructor
    public MuranoHelper(String serverUrl,
                        String username,
                        String password,
                        String tenantName) {
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.tenantName = tenantName;

        System.out.print(">>>>>: "+this.serverUrl);
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


    /**
     *
     * @param name New environment name
     * @param objectModel object model describing new environment
     * @return new environment id
     * @throws AuthenticationException in case credentiols
     */
    public String deployNewFromObjectModel(String name, String objectModel)
            throws AuthenticationException {

        this.authenticate();

        // Get the UI Definition for Kubernetes. This is hardcoded based on the K8S Definition in Murano
        // TODO: See if this can be changed dynamically
        String token = os.getAccess().getToken().getId();

        String envId = checkIfDeploymentExists(token, name);
        if (envId == null) {
            // Create Env
            envId = this.createEnvironment(token, name);

            // Create Session
            String sessionId = this.createEnvironmentSession(token, envId);

            // Add App to Environment
            addApplicationToEnvironment(token, envId, sessionId, objectModel);

            // Deploy
            deployEnvironment(token, envId, sessionId);
        }

        return envId;
    }


    /**
     * Loop around to see if the deployment is a success. This waits for about 10 secs 300 times hoping that
     * it finishes. This all depends on teh number of nodes and the speed of the boxes. But seems sufficient.
     *
     * @param envId Environemnt Id
     * @return whether the deployment is a success
     * @throws TimeoutException if deployment process still in progress after deadline
     */
    public boolean waitDeploymentResult(String envId) throws TimeoutException {
        String token = getOSClient().getAccess().getToken().getId();

        boolean status = false;
        Instant deadline = Instant.now().plusMillis(timeout);

        while(true) {
            try {
                Thread.sleep(10000);
                String payload = getResponseForJson(
                        getMuranoEnpoint(),
                        MURANO_DEFAULT_PORT,
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
                    if ("success".equals(thisDeployment.get("state"))) {
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
            if (Instant.now().isAfter(deadline)){
                throw new TimeoutException("Environment was not ready in time.");
            }

        }
        return status;
    }

    /**
     * Return the Environment id if it exists
     *
     * @param token
     * @param name
     * @return
     */
    private String checkIfDeploymentExists(String token, String name) {
        // TODO: remove string manipulation
        String payload = getResponseForJson(
                getMuranoEnpoint(),
                MURANO_DEFAULT_PORT,
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
            LogRecord logRecord = new LogRecord(Level.WARNING, "Parse exception: position: " + pe.getPosition());
            logRecord.setThrown(pe);
            LOG.log(logRecord);
        }
        return envId;
    }

    /**
     * Deploy the environment given the environment id and Session Token
     */
    private void deployEnvironment(String token, String envId, String sessionId) {
        String response = getResponseForJson(
                getMuranoEnpoint(),
                MURANO_DEFAULT_PORT,
                "/v1/environments/" + envId + "/sessions/" + sessionId + "/deploy",
                HttpMethod.POST,
                token,
                null,
                null);
    }


    public String getMuranoEnpoint() {
        String string[] = this.serverUrl.split(":");

        return string[0] + ":" + string[1];
    }

    /**
     * Add the app(K8S) to the environment
     * @param token
     * @param envId
     * @param sessionId
     * @param jsonReq
     */
    private void addApplicationToEnvironment(String token, String envId, String sessionId, String jsonReq) {
        String response = getResponseForJson(this.getMuranoEnpoint(),
                MURANO_DEFAULT_PORT,
                "/v1/environments/" + envId + "/services",
                HttpMethod.POST,
                token,
                jsonReq,
                sessionId);
    }

    private String createEnvironmentSession(String token, String envId) {
        String payload = getResponseForJson(this.getMuranoEnpoint(),
                MURANO_DEFAULT_PORT,
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
        String payload = getResponseForJson(
                getMuranoEnpoint(),
                MURANO_DEFAULT_PORT,
                "/v1/environments",
                HttpMethod.POST, token, reqPayload, null);

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
    private  String getResponseForJson(String url,
                                       int port,
                                       String requestPath,
                                       HttpMethod method,
                                       String token,
                                       String jsonPayload,
                                       String muranoSessionId) {
        HttpRequest request = HttpRequest.builder().method(method)
                .endpoint(url + ":" + port)
                .path(requestPath)
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
            ex.printStackTrace();
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


    /**
     * Authenticate to the Openstack instance given the credentials in constructor
     *
     * @throws AuthenticationException in case of authentication failure.
     */
    public void authenticate() throws AuthenticationException {
        this.os = OSFactory.builderV2()
                .endpoint(this.serverUrl)
                .credentials(this.username, this.password)
                .tenantName(this.tenantName)
                .authenticate();
    }


    /**
     * Helper object to return the OSClient
     * @return OSClient V2
     */
    public OSClient.OSClientV2 getOSClient() {
       return this.os;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}

