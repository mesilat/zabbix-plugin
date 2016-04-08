package com.mesilat.zabbix;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Config {

    @XmlElement
    private String url;
    @XmlElement
    private String username;
    @XmlElement
    private String password;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void save(PluginSettings settings) {
        if (getUrl() != null) {
            settings.put(Config.class.getName() + ".url", getUrl());
        } else {
            settings.remove(Config.class.getName() + ".url");
        }
        if (getUsername() != null) {
            settings.put(Config.class.getName() + ".username", getUsername());
        } else {
            settings.remove(Config.class.getName() + ".username");
        }
        if (getPassword() != null) {
            settings.put(Config.class.getName() + ".password", getPassword());
        } else {
            settings.remove(Config.class.getName() + ".password");
        }
    }

    protected boolean isPasswordObfuscated() {
        try {
            PasswordEncryption.unscramble(password);
            return true;
        } catch(Exception ignore) {
            return false;
        }
    }
    
    protected void obfuscatePassword() throws Exception {
        password = PasswordEncryption.scramble(password);
    }

    public Config() {
    }

    public Config(PluginSettings settings) {
        Object obj = settings.get(Config.class.getName() + ".url");
        if (obj != null) {
            url = obj.toString();
        }
        obj = settings.get(Config.class.getName() + ".username");
        if (obj != null) {
            username = obj.toString();
        }
        obj = settings.get(Config.class.getName() + ".password");
        if (obj != null) {
            password = obj.toString();
        }
    }
}
