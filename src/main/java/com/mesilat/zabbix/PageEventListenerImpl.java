package com.mesilat.zabbix;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.event.events.content.page.PageCreateEvent;
import com.atlassian.confluence.event.events.content.page.PageUpdateEvent;
import com.atlassian.confluence.pages.Page;
import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@ExportAsService({PageEventListener.class})
@Named("com.mesilat:zabbix-plugin:pageEventListener")
public class PageEventListenerImpl implements PageEventListener, InitializingBean, DisposableBean {
    public static final Logger LOGGER = LoggerFactory.getLogger("com.mesilat.zabbix-plugin");

    private final EventPublisher eventPublisher;
    private final I18nResolver resolver;
    private final ActiveObjects ao;    
    private final TransactionTemplate transactionTemplate;

    @Override
    public void afterPropertiesSet() throws Exception {
        eventPublisher.register(this);
    }
    @Override
    public void destroy() throws Exception {
        eventPublisher.unregister(this);
    }
    @EventListener
    public void onPageCreateEvent(PageCreateEvent event) {
        processPage(event.getPage());
    }
    @EventListener
    public void onPageUpdateEvent(PageUpdateEvent event) {
        processPage(event.getPage());
    }

    public void processPage(Page page) {
        PageProcessor processor = new PageProcessor(page);
        processor.process();
    }

    @Inject
    public PageEventListenerImpl(
        final @ComponentImport EventPublisher eventPublisher,
        final @ComponentImport I18nResolver resolver,
        final @ComponentImport ActiveObjects ao,
        final @ComponentImport TransactionTemplate transactionTemplate
    ) {
        this.eventPublisher = eventPublisher;
        this.resolver = resolver;
        this.ao = ao;
        this.transactionTemplate = transactionTemplate;
    }


    private static final Pattern ZBX_HOST_DEFAULT = Pattern.compile(
        "<ac:structured-macro\\s.*ac:name=\"zabbix-host-selector\".*</ac:structured-macro>"
    );
    private static final Pattern ZBX_HOST_PARAMS = Pattern.compile(
        "\\s*<ac:parameter ac:name=\"(server|host)\">([^<]+)</ac:parameter>\\s*"
    );

    private class PageProcessor implements Runnable {
        private final Page page;

        @Override
        public void run() {
            try {
                transactionTemplate.execute(()->{
                    String body = page.getBodyAsString();
                    Matcher m = ZBX_HOST_DEFAULT.matcher(body);
                    if (!m.find()){
                        return null;
                    }
                    String macro = m.group();
                    m = ZBX_HOST_PARAMS.matcher(macro);
                    Map<String,String> map = new HashMap<>();
                    while (m.find()){
                        map.put(m.group(1), m.group(2));
                    }
                    if (map.containsKey("server") && map.containsKey("host")){
                        ZabbixHostDefault[] _hd = ao.find(ZabbixHostDefault.class, "PAGE_ID = ?", page.getId());
                        ZabbixHostDefault hd = _hd.length > 0? _hd[0]: ao.create(ZabbixHostDefault.class);
                        hd.setHost(map.get("host"));
                        hd.setServer(map.get("server"));
                        hd.setPageId(page.getId());
                        hd.save();
                    }
                    return null;
                });
            } catch (Throwable ex) {
                LOGGER.error(String.format(resolver.getText("com.mesilat.zabbix-plugin.error.processing-page"), page.getId(), page.getTitle()), ex);
            }
        }
        public void process() {
            Thread t = new Thread(this);
            t.start();
        }

        public PageProcessor(Page page){
            this.page = page;
        }
    }
}