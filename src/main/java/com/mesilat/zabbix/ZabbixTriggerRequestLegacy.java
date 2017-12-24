package com.mesilat.zabbix;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class ZabbixTriggerRequestLegacy {
    @XmlElement
    private String token;
    @XmlElement
    private String[] hostIds;

    public String getToken() {
        return token;
    }
    public void setToken(String token) {
        this.token = token;
    }
    public String[] getHostIds() {
        return hostIds;
    }
    public void setHostIds(String[] hostIds) {
        this.hostIds = hostIds;
    }
}