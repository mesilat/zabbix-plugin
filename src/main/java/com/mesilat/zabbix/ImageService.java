package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixClientException;

public interface ImageService {
    byte[] getImage(String server, String host, String graph, String width, String height, String period) throws ZabbixResourceException, ZabbixClientException;
    byte[] getImage(String graphId, String width, String height, String period) throws ZabbixResourceException, ZabbixClientException;
    byte[] getMapImage(String server, String map, String severity) throws ZabbixResourceException, ZabbixClientException;
    String getVersion(String server) throws ZabbixResourceException, ZabbixClientException;
}