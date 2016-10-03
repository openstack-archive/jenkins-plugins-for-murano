package org.openstack.murano.jenkins_plugins.muranoci.deploy.credentials;


import com.cloudbees.plugins.credentials.Credentials;
import hudson.util.Secret;

public interface OpenstackCredentials extends Credentials {
    String getName();
    String getDescription();
    String getUsername();
    Secret getPassword();
    String getTenant();
    String getIdentityServiceEndpoint();
}