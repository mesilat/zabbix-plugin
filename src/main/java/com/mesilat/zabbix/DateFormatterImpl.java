package com.mesilat.zabbix;

import com.mesilat.zabbix.client.DateFormatter;
import java.util.Date;

public class DateFormatterImpl implements DateFormatter {
    private final com.atlassian.confluence.core.DateFormatter confluenceDateFormatter;

    @Override
    public String format(Date date) {
        return confluenceDateFormatter.format(date);
    }

    public DateFormatterImpl(com.atlassian.confluence.core.DateFormatter confluenceDateFormatter){
        this.confluenceDateFormatter = confluenceDateFormatter;
    }
}