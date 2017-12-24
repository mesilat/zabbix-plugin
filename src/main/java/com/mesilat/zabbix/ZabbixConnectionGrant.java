package com.mesilat.zabbix;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.Table;

@Preload
@Table("zabbix_grant")
public interface ZabbixConnectionGrant extends Entity {
    ZabbixConnectionDescriptor getConnectionDescriptor();
    void setConnectionDescriptor(ZabbixConnectionDescriptor connectionDescriptor);
    String getGrantee();
    void setGrantee(String grantee);
}