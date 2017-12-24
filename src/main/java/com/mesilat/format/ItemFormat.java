package com.mesilat.format;

import net.java.ao.Entity;
import net.java.ao.Preload;
import net.java.ao.schema.StringLength;
import net.java.ao.schema.Table;

@Preload
@Table("zabbix_item_formats")
public interface ItemFormat extends Entity {
    String getOwnerKey();
    void setOwnerKey(String ownerKey);
    String getName();
    void setName(String name);
    String getFormat();
    void setFormat(String format);
    @StringLength(1)
    String isPublicFormat();
    @StringLength(1)
    void setPublicFormat(String val);
}