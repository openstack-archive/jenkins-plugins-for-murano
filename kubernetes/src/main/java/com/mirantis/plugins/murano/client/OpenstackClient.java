package com.mirantis.plugins.murano.client;

import org.openstack4j.api.OSClient;
import org.openstack4j.model.compute.Flavor;
import org.openstack4j.model.compute.Keypair;
import org.openstack4j.openstack.OSFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client Class to talk to Openstack/Murano APIs
 */
public class OpenstackClient {

    private final static Logger LOG = Logger.getLogger(OpenstackClient.class.getName());
    private String serverUrl;
    private String username;
    private String password;
    private String tenantName;

    private OSClient.OSClientV2 os = null;

    public OpenstackClient(String serverUrl,
                           String username,
                           String password,
                           String tenantName) {
        this.serverUrl = serverUrl;
        this.username = username;
        this.password = password;
        this.tenantName = tenantName;
    }

    /**
     * Authenticate to the Openstack instance given the credentials in constructor
     * @return whether the auth was successful
     */
    public boolean authenticate() {
        boolean success = false;
        try {
            this.os = OSFactory.builderV2()
                    .endpoint(this.serverUrl + ":5000/v2.0")
                    .credentials(this.username, this.password)
                    .tenantName(this.tenantName)
                    .authenticate();
            success = true;
        } catch(Exception ex) {
            LOG.log(Level.SEVERE, "Error connecting to Client", ex);
            success = false;
        }
        return success;
    }

    /**
     * Get all the Flavors the user has access to
     * @return A List of Flavor objects
     */
    public ArrayList<String> getFlavors() {
        ArrayList<String> flavors = new ArrayList<String>();
        if (this.os != null) {
            List<? extends Flavor> flavorsList = this.os.compute().flavors().list();
            for (Flavor f : flavorsList) {
                flavors.add(f.getName());
            }
        }
        return flavors;

    }

    /**
     * Get all the Keypairs the user has access to
     * @return A List of Keypair object
     */
    public ArrayList<String> getKeypairs() {
        ArrayList<String> keypairs = new ArrayList<String>();
        if (this.os != null) {
            List<? extends Keypair> kpList = this.os.compute().keypairs().list();
            for (Keypair k : kpList) {
                keypairs.add(k.getName());
            }
        }
        return keypairs;
    }

    /**
     * Helper object to return the OSClient
     * @return OSClient V2
     */
    public OSClient.OSClientV2 getOSClient() {
        return this.os;
    }
}
