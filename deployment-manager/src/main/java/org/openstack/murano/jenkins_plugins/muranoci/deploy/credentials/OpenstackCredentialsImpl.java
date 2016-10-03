package org.openstack.murano.jenkins_plugins.muranoci.deploy.credentials;


import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.cloudbees.plugins.credentials.impl.Messages;
import hudson.Extension;
import hudson.util.FormValidation;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.openstack.murano.jenkins_plugins.muranoci.deploy.MuranoHelper;
import org.openstack4j.api.exceptions.AuthenticationException;

import javax.servlet.ServletException;
import java.io.IOException;

import static java.util.Objects.requireNonNull;

@NameWith(value = OpenstackCredentialsNameProvider.class, priority = 50)
public class OpenstackCredentialsImpl extends BaseStandardCredentials implements OpenstackCredentials {
    private final String name;

    private final String identityServiceEndpoint;
    private final String username;
    private final Secret password;
    private final String tenant;

    @DataBoundConstructor
    public OpenstackCredentialsImpl(
            String id,
            String name,
            String description,
            String identityServiceEndpoint,
            String tenant,
            String username,
            String password) {
        super(id, description);

        this.name = name;
        this.identityServiceEndpoint = identityServiceEndpoint;
        this.username = username;
        this.password = Secret.fromString(requireNonNull(password));
        this.tenant = tenant;
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public Secret getPassword() {
        return this.password;
    }

    @Override
    public String getTenant() {
        return this.tenant;
    }

    @Override
    public String getIdentityServiceEndpoint() {
        return this.identityServiceEndpoint;
    }

    @Extension
    public static class Descriptor
            extends CredentialsDescriptor {

        public FormValidation doTestConnection(@QueryParameter("identityServiceEndpoint") final String identityServiceEndpoint,
                                               @QueryParameter("tenant") final String tenant,
                                               @QueryParameter("username") final String username,
                                               @QueryParameter("password") final String password)
                throws IOException, ServletException {

            MuranoHelper client = new MuranoHelper(
                    identityServiceEndpoint,
                    username,
                    password,
                    tenant);

            try {
                client.authenticate();
            } catch (AuthenticationException ae) {
                return FormValidation.error(
                        "Unable to connect to server. Please check credentials");
            } catch (Exception e) {
                return FormValidation.error("Error: " + e.getMessage());
            }

            return FormValidation.ok("Success");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
//            return Messages.CertificateCredentialsImpl_DisplayName();
            return "Openstack Cloud";
        }
    }
}