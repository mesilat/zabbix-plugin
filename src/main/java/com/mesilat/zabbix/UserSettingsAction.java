package com.mesilat.zabbix;

import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.actions.AbstractUserProfileAction;

public class UserSettingsAction extends AbstractUserProfileAction {

    @Override
    public String getPageTitle(){
        return "Zabbix Plugin";
    }

    @Override
    public String execute() throws Exception {
        ConfluenceUser user = this.getUser();
 
        if (user == null) {
            return "error";
        } else {
            return "success";
        }
    }
}