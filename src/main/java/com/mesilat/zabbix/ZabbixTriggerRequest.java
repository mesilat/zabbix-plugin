package com.mesilat.zabbix;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ZabbixTriggerRequest {
    @XmlElement
    private String token;
    @XmlElement
    private Integer server;
    @XmlElement
    private String[] hosts;

    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }
    public Integer getServer() {
        return server;
    }
    public void setServer(Integer server) {
        this.server = server;
    }
    public String[] getHosts() {
        return hosts;
    }
    public void setHosts(String[] hosts) {
        this.hosts = hosts;
    }
}