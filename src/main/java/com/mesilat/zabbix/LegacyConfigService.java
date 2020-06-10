package com.mesilat.zabbix;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.mesilat.format.ItemFormat;
import com.mesilat.util.PasswordEncryption1;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

/**
 * Import legacy configuration options
 */
public class LegacyConfigService implements InitializingBean, DisposableBean, Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix");

    public static final String DEFAULT_FORMAT = "znk': 'v' ('c')'";
    public static final String DEFAULT_SHORT  = "v";
    public static final String DEFAULT_MEDIUM = "v' ('c')'";
    public static final String DEFAULT_LONG   = "znk': 'v' ('c')'";

    private final PluginSettingsFactory pluginSettingsFactory;
    private final ActiveObjects ao;
    private final UserManager userManager;
    private Thread thread;

    @Override
    public void afterPropertiesSet() throws Exception {
        thread = new Thread(this);
        thread.start();
    }
    @Override
    public void destroy() throws Exception {
        if (thread != null && thread.isAlive()){
            thread.interrupt();
        }
        thread = null;
    }
    @Override
    public void run(){
        PluginSettings settings = pluginSettingsFactory.createGlobalSettings();

        while (true){
            try {
                ao.executeInTransaction(()->{
                    UserProfile admin = userManager.getUserProfile("admin");
                    int n = ao.count(ZabbixConnectionDescriptor.class, "DEFAULT = 'Y'");
                    if (n == 0){
                        LegacyConfig legacyConfig = new LegacyConfig(settings);
                        if (legacyConfig.getUrl() != null){
                            ZabbixConnectionDescriptor conn = ao.create(ZabbixConnectionDescriptor.class);
                            conn.setUrl(legacyConfig.getUrl());
                            conn.setUsername(legacyConfig.getUsername());
                            conn.setPassword(legacyConfig.getPassword());
                            conn.setOwnerKey(admin == null? null: admin.getUserKey().getStringValue());
                            conn.setDefault("Y");
                            conn.save();
                            //legacyConfig.delete(settings); // Does not work, need "do in transaction"
                            LOGGER.info("Plugin settings imported from legacy storage");
                        }
                    }

                    n = ao.count(ItemFormat.class);
                    if (n == 0){
                        Map<String, LegacyItemFormat> formats = new HashMap<>();
                        formats.put("default", new LegacyItemFormat("default", DEFAULT_FORMAT));
                        formats.put("long",    new LegacyItemFormat("long",    DEFAULT_LONG));
                        formats.put("medium",  new LegacyItemFormat("medium",  DEFAULT_MEDIUM));
                        formats.put("short",   new LegacyItemFormat("short",   DEFAULT_SHORT));

                        Object obj = settings.get(LegacyItemFormat.BASE + ".all");
                        if (obj instanceof List) {
                            List<String> text = (List<String>)obj;
                            for (String s : text) {
                                String[] ss = s.split("\t");
                                LegacyItemFormat fmt = new LegacyItemFormat(ss[0], ss[1]);
                                formats.put(fmt.getName(), fmt);
                            }
                        }

                        for (LegacyItemFormat legacyFormat : formats.values()){
                            ItemFormat format = ao.create(ItemFormat.class);
                            format.setName(legacyFormat.getName());
                            format.setFormat(legacyFormat.getFormat());
                            format.setPublicFormat("Y");
                            format.save();
                        }
                        LOGGER.info("Item formats imported from legacy storage");
                    }
                    return null;
                });
                return;
            } catch(IllegalStateException ignore){
                try {
                    Thread.sleep(1000); // Wait for AO to initialize...
                } catch (InterruptedException ex) {
                    break;
                }
            }
        }
    }

    public LegacyConfigService(
        final PluginSettingsFactory pluginSettingsFactory,
        final ActiveObjects ao,
        final UserManager userManager
    ){
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.ao = ao;
        this.userManager = userManager;
    }

    public class LegacyConfig {
        private static final String BASE = "com.mesilat.zabbix.Config";
        private String url;
        private String username;
        private String password;

        public String getUrl() {
            return url;
        }
        public void setUrl(String url) {
            this.url = url;
        }
        public String getUsername() {
            return username;
        }
        public void setUsername(String username) {
            this.username = username;
        }
        public String getPassword() {
            return password;
        }
        public void setPassword(String password) {
            this.password = password;
        }

        public void delete(PluginSettings settings) {
            settings.remove(LegacyConfig.class.getName() + ".url");
            settings.remove(LegacyConfig.class.getName() + ".username");
            settings.remove(LegacyConfig.class.getName() + ".password");
            settings.remove(LegacyItemFormat.class.getName() + ".all");
        }

        protected void obfuscatePassword() throws Exception {
            password = PasswordEncryption1.scramble(password);
        }

        public LegacyConfig(PluginSettings settings) {
            Object obj = settings.get(BASE + ".url");
            if (obj != null) {
                url = obj.toString();
            }
            obj = settings.get(BASE + ".username");
            if (obj != null) {
                username = obj.toString();
            }
            obj = settings.get(BASE + ".password");
            if (obj != null) {
                password = obj.toString();
            }
        }
    }
    public class LegacyItemFormat {
        private static final String BASE = "com.mesilat.format.ItemFormat";
        private String name;
        private String format;

        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public String getFormat() {
            return format;
        }
        public void setFormat(String format) {
            this.format = format;
        }

        public LegacyItemFormat(String name, String format) {
            this.name = name;
            this.format = format;
        }
    }
}