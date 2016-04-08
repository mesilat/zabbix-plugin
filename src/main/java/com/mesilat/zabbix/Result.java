package com.mesilat.zabbix;

import com.atlassian.confluence.json.json.Json;
import com.atlassian.confluence.json.json.JsonObject;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Result {
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILURE = "FAILURE";

    @XmlElement
    private String status;
    @XmlElement
    private String message;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Json toJson() {
        JsonObject json = new JsonObject();
        json.setProperty("status", getStatus());
        json.setProperty("message", getMessage());
        return json;
    }
    
    public Result() {
    }

    public Result(String status) {
        this.status = status;
    }

    public Result(String status, String message) {
        this.status = status;
        this.message = message;
    }
}
