package com.mirantis.plugins.murano;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configuration Helper to understand the Mirantis UI definition
 */
public class ConfigurationSection {
    Map<String, List<Map>> conf;

    public ConfigurationSection() {
        conf = new HashMap();
    }

    public void addSection(String sectionName, List fields) {
        conf.put(sectionName, fields);
    }

    public List<Map> getSection(String sectionName){
        return conf.get(sectionName);
    }

    public Set getSections(){
        return conf.keySet();
    }
}

