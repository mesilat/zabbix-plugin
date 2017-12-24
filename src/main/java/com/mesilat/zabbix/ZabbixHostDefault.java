package com.mesilat.zabbix;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Table;

@Preload
@Table("zabbix_default")
public interface ZabbixHostDefault extends Entity {
    Long getPageId();
    void setPageId(Long pageId);
    String getServer();
    void setServer(String server);
    String getHost();
    void setHost(String host);
}