package com.mesilat.zabbix;

import javax.ws.rs.core.Response.Status;

public class ZabbixResourceException extends Exception {
    private final Status status;

    public Status getStatus() {
        return status;
    }
    
    public ZabbixResourceException(Status status, String message) {
        super(message);
        this.status = status;
    }
    public ZabbixResourceException(Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }
}