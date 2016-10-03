package org.openstack.murano.jenkins_plugins.muranoci.deploy.credentials;


import com.cloudbees.plugins.credentials.CredentialsNameProvider;

public class OpenstackCredentialsNameProvider extends CredentialsNameProvider<OpenstackCredentialsImpl> {

    @Override
    public String getName(OpenstackCredentialsImpl openstackCredentials) {
        return openstackCredentials.getName();
    }
}