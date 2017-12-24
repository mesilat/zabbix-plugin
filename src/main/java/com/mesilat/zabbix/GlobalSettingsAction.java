package com.mesilat.zabbix;

import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.ConfluenceUser;
import com.atlassian.confluence.user.actions.AbstractUserProfileAction;

public class GlobalSettingsAction extends AbstractUserProfileAction {
    @Override
    public String getPageTitle(){
        return "Zabbix Plugin";
    }
    public String getBaseUrl(){
        return settingsManager.getGlobalSettings().getBaseUrl();
    }

    @Override
    public String execute() throws Exception {
        ConfluenceUser user = getUser();
        if (!permissionManager.hasPermission(user, Permission.ADMINISTER, PermissionManager.TARGET_APPLICATION)) {
            return "error";
        } else {
            return "success";
        }
    }
}