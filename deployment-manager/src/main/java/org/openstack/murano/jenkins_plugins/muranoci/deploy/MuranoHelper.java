package org.openstack.murano.jenkins_plugins.muranoci.deploy;


import org.kohsuke.stapler.DataBoundConstructor;
import org.openstack4j.api.Builders;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.exceptions.AuthenticationException;
import org.openstack4j.model.murano.v1.domain.AppCatalogSession;
import org.openstack4j.model.murano.v1.domain.Deployment;
import org.openstack4j.model.murano.v1.domain.Environment;
import org.openstack4j.openstack.OSFactory;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeoutException;

public class MuranoHelper {
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


    public boolean deployEnvAndWait(String envName, String objectModel) throws TimeoutException {
        Environment env = deployNewFromObjectModel(envName, objectModel);
        return waitDeploymentResult(env);
    }
    /**
     *
     * @param name New environment name
     * @param objectModel object model describing new environment
     * @return new environment id
     * @throws AuthenticationException in case credentials
     */
    Environment deployNewFromObjectModel(String name, String objectModel)
            throws AuthenticationException {
        this.authenticate();

        Environment env = checkIfDeploymentExists(name);

        if (env == null) {
            // Create Env
            env = getOSClient().murano().environments().create(
                    Builders.environment().name(name).build()
            );
        }
        // Create Session
        AppCatalogSession session = getOSClient().murano().sessions().configure(env.getId());

        // Add App to Environment
        getOSClient().murano().services().create(env.getId(), session.getId(), objectModel);

        // Deploy
        getOSClient().murano().sessions().deploy(env.getId(), session.getId());

        return env;
    }


    /**
     * Loop around to see if the deployment is a success. This waits for about 10 secs 300 times hoping that
     * it finishes. This all depends on teh number of nodes and the speed of the boxes. But seems sufficient.
     *
     * @param env Environemnt instance
     * @return whether the deployment is a success
     * @throws TimeoutException if deployment process still in progress after deadline
     */
    boolean waitDeploymentResult(Environment env) throws TimeoutException {
        boolean status = false;
        boolean isTimedOut = false;
        Instant deadline = Instant.now().plusMillis(timeout);

        Deployment deployment;

        while(!isTimedOut) {
            if (Instant.now().isAfter(deadline)){
                isTimedOut = true;
            }

            deployment = getOSClient().murano().deployments().list(env.getId()).get(0);
            String state = deployment.getState();

            if (!state.equals("running")) {
                status = state.equals("success");
                break;
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (isTimedOut) {
            throw new TimeoutException("Timed out. Environment is not ready in time.");
        }

        return status;
    }

    /**
     * Return the Environment if it exists else null
     *
     * @param name Environment name
     * @return Environment instance or null
     */
    private Environment checkIfDeploymentExists(String name) {
        return getEnvByName(name);
    }

    private Environment getEnvByName(String name) {
        List<? extends Environment> envs = getOSClient().murano().environments().list();

        return envs.stream().filter(e -> e.getName().equals(name)).findFirst().orElse(null);
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

    private boolean isTokenExpiring() {
        long oneMinute = 60000;
        Date muniteInFuture = new Date(Date.from(Instant.now()).getTime() + oneMinute);

        return os.getAccess().getToken().getExpires().before(muniteInFuture);
    }

    /**
     * Helper object to return the OSClient
     * @return OSClient V2
     */
    private OSClient.OSClientV2 getOSClient() {
        if (this.os == null || isTokenExpiring()) {
            authenticate();
        }

        return this.os;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}

