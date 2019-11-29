package com.mesilat.zabbix;

import com.mesilat.zabbix.client.ZabbixTrigger;
import com.mesilat.zabbix.client.ZabbixClientException;
import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.core.DateFormatter;
import com.atlassian.confluence.core.FormatSettingsManager;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.ConfluenceUserPreferences;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.upm.api.license.PluginLicenseManager;
import com.mesilat.zabbix.client.StringUtil;
import com.mesilat.zabbix.client.ZabbixClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/triggers")
public class ZabbixTriggerResource extends ZabbixResourceBase {
    private static final String ALL_HOSTS = "__ALL__";
    
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(
        @QueryParam("token") String token,
        @QueryParam("server") Integer server,
        @QueryParam("host") String host,
        @QueryParam("host-group") String group,
        @Context HttpServletRequest request
    ) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        if (!TokenService.isValidToken(userKey, token)){
            return Response.status(Response.Status.FORBIDDEN).entity(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")).build();
        }
        if (server == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Required parameter \"server\" is missing").build();
        }

        try {
            if (ALL_HOSTS.equals(host)) {
                ConfluenceUserPreferences preferences = getPreferences(userKey);
                DateFormatter dateFormatter = getDateFormatter(preferences);
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                ZabbixClient client = getClient(conn);
                List<ZabbixTrigger> triggers = client.getTriggers(new DateFormatterImpl(dateFormatter));
                return Response.ok(toArray(triggers, dateFormatter).toString()).build();
            } else if (host != null) {
                ConfluenceUserPreferences preferences = getPreferences(userKey);
                DateFormatter dateFormatter = getDateFormatter(preferences);
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                ZabbixClient client = getClient(conn);
                List<ZabbixTrigger> triggers = client.getTriggersForHost(host, new DateFormatterImpl(dateFormatter));
                return Response.ok(toArray(triggers, dateFormatter).toString()).build();
            } else if (group != null) {
                ConfluenceUserPreferences preferences = getPreferences(userKey);
                DateFormatter dateFormatter = getDateFormatter(preferences);
                ZabbixConnectionDescriptor conn = getConnection(userKey, server);
                ZabbixClient client = getClient(conn);
                List<ZabbixTrigger> triggers = client.getTriggersForHostGroup(group, new DateFormatterImpl(dateFormatter));
                return Response.ok(toArray(triggers, dateFormatter).toString()).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity("Either parameter \"host\" or \"host-group\" is required").build();
            }
        } catch (ZabbixResourceException | ZabbixClientException | JSONException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }


    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response post(final ZabbixTriggerRequest triggerRequest, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        ConfluenceUserPreferences preferences = getPreferences(userKey);
        DateFormatter dateFormatter = getDateFormatter(preferences);

        if (TokenService.isValidToken(userKey, triggerRequest.getToken())){
            return getTriggers(userKey, triggerRequest, dateFormatter);       
        } else {
            return Response.status(Response.Status.FORBIDDEN).entity(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")).build();
        }
    }

    @Path("/legacy")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response post(final ZabbixTriggerRequestLegacy triggerRequest, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        ConfluenceUserPreferences preferences = getPreferences(userKey);
        DateFormatter dateFormatter = getDateFormatter(preferences);

        if (TokenService.isValidToken(userKey, triggerRequest.getToken())){
            return getTriggers(triggerRequest.getHostIds(), dateFormatter);       
        } else {
            return Response.status(Response.Status.FORBIDDEN).entity(getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")).build();
        }
    }

    private Response getTriggers(final String[] hostIds, DateFormatter dateFormatter) {
        try {
            ZabbixConnectionDescriptor conn = getDefaultConnection();

            // Run query asynchronously
            final Map<String, List<ZabbixTrigger>> triggers = new HashMap<>();
            final List<Thread> threads = new ArrayList<>();
            final List<Exception> errors = new ArrayList<>();
            for (String hostId : hostIds){
                if (!triggers.containsKey(hostId)){
                    triggers.put(hostId, new ArrayList<>());
                    Thread thread = new Thread(() -> {
                        try {
                            ZabbixClient client = getClient(conn);
                            List<ZabbixTrigger> t = client.getTriggersForHostId(hostId, new DateFormatterImpl(dateFormatter));
                            synchronized(triggers){
                                triggers.get(hostId).addAll(t);
                            }
                        } catch (ZabbixResourceException | ZabbixClientException ex) {
                            synchronized(errors){
                                errors.add(ex);
                            }
                        }
                    });
                    thread.start();
                    threads.add(thread);
                }
            }
            for (Thread thread : threads){
                thread.join();
            }
            // Check for errors
            for (Exception ex : errors){
                if (ex instanceof ZabbixResourceException){
                    throw (ZabbixResourceException)ex;
                } else if (ex instanceof ZabbixClientException){
                    throw (ZabbixClientException)ex;
                }
            }

            JSONObject results = new JSONObject();
            for (String hostId : triggers.keySet()) {
                results.put(hostId, toArray(triggers.get(hostId), dateFormatter));
            }

            JSONObject root = new JSONObject();
            root.put("results", results);
            return Response.ok(root.toString()).build();
        } catch (ZabbixResourceException | ZabbixClientException | JSONException | InterruptedException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }
    private Response getTriggers(UserKey userKey, ZabbixTriggerRequest triggerRequest, DateFormatter dateFormatter){
        try {
            ZabbixConnectionDescriptor conn = getConnection(userKey, triggerRequest.getServer());

            // Run query asynchronously
            final Map<String, List<ZabbixTrigger>> triggers = new HashMap<>();
            final List<Thread> threads = new ArrayList<>();
            final List<Exception> errors = new ArrayList<>();
            for (String host : triggerRequest.getHosts()){
                if (!triggers.containsKey(host)){
                    triggers.put(host, new ArrayList<>());
                    Thread thread = new Thread(() -> {
                        try {
                            ZabbixClient client = getClient(conn);
                            if ("__ALL__".equals(host)){
                                List<ZabbixTrigger> t = client.getTriggers(new DateFormatterImpl(dateFormatter));
                                synchronized(triggers){
                                    triggers.get(host).addAll(t);
                                }
                            } else {
                                List<ZabbixTrigger> t = client.getTriggersForHost(host, new DateFormatterImpl(dateFormatter));
                                synchronized(triggers){
                                    triggers.get(host).addAll(t);
                                }
                            }
                        } catch (ZabbixResourceException | ZabbixClientException ex) {
                            synchronized(errors){
                                errors.add(ex);
                            }
                        }
                    });
                    thread.start();
                    threads.add(thread);
                }
            }
            for (Thread thread : threads){
                thread.join();
            }
            // Check for errors
            for (Exception ex : errors){
                if (ex instanceof ZabbixResourceException){
                    throw (ZabbixResourceException)ex;
                } else if (ex instanceof ZabbixClientException){
                    throw (ZabbixClientException)ex;
                }
            }

            JSONObject results = new JSONObject();
            for (String host : triggers.keySet()){
                results.put(host, toArray(triggers.get(host), dateFormatter));
            }

            JSONObject root = new JSONObject();
            root.put("results", results);
            return Response.ok(root.toString()).build();
        } catch (ZabbixResourceException | ZabbixClientException | JSONException | InterruptedException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }
    private JSONArray toArray(List<ZabbixTrigger> triggers, DateFormatter dateFormatter) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ZabbixTrigger trigger : triggers) {
            arr.put(toObject(trigger, dateFormatter));
        }
        return arr;
    }
    private JSONObject toObject(ZabbixTrigger trigger, DateFormatter dateFormatter) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("triggerId", trigger.getTriggerId());
        obj.put("description", trigger.getDescription());
        obj.put("priority", trigger.getPriority().toString());
        obj.put("priorityUpperCase", StringUtil.toFirstCapital(trigger.getPriority().toString()));
        obj.put("iconClass", trigger.getPriority().toIconClass());
        obj.put("timestamp", dateFormatter.format(trigger.getLastChange()));
        obj.put("hostId", trigger.getHostId());
        obj.put("host", trigger.getHost());
        obj.put("hostName", trigger.getHostName());
        obj.put("groupId", trigger.getGroupId());
        obj.put("groupName", trigger.getGroupName());
        obj.put("eventId", trigger.getEventId());
        return obj;
    }

    public ZabbixTriggerResource(I18nResolver resolver, UserManager userManager,
            PluginLicenseManager licenseManager, UserAccessor userAccessor,
            FormatSettingsManager formatSettingsManager, LocaleManager localeManager,
            ActiveObjects ao, SettingsManager settingsManager) {
        super(resolver, userManager, licenseManager, userAccessor,
            formatSettingsManager, localeManager, ao, settingsManager);
    }
}