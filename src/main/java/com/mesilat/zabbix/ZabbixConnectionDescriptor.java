package com.mesilat.zabbix;

import net.java.ao.Entity;
import net.java.ao.OneToMany;
import net.java.ao.Preload;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Preload
@Table("zabbix")
public interface ZabbixConnectionDescriptor extends Entity {
    String getUrl();
    void setUrl(String url);
    String getUsername();
    void setUsername(String username);
    String getPassword();
    void setPassword(String password);
    String getOwnerKey();
    void setOwnerKey(String ownerKey);
    @StringLength(1)
    String getDefault();
    @StringLength(1)
    void setDefault(String val);
    @OneToMany
    ZabbixConnectionGrant[] getGrants();
    @OneToMany
    void setGrants(ZabbixConnectionGrant[] grants);
    String getVersion();
    void setVersion(String version);

    public static int compareVersions(String a, String b){
        String[] sa = a.split("\\."), sb = b.split("\\.");
        for (int i = 0;; i++){
            if (sa.length > i && sb.length > i){
                int na = Integer.parseInt(sa[i]), nb = Integer.parseInt(sb[i]);
                if (na != nb){
                    return na - nb;
                }
            } else if (sa.length <= i && sb.length <= i){
                return 0;
            } else if (sa.length <= i){
                return -1;
            } else {
                return 1;
            }
        }
    }
}