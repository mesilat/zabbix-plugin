package com.mesilat.zabbix;

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
import com.mesilat.zabbix.client.ZabbixClient;
import com.mesilat.zabbix.client.ZabbixEvent;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/events")
public class ZabbixEventResource extends ZabbixResourceBase {
    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@QueryParam("server") Integer serverId, @QueryParam("trigger") String triggerId, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);
        ConfluenceUserPreferences preferences = getPreferences(userKey);
        DateFormatter dateFormatter = getDateFormatter(preferences);

        return getEvents(userKey, serverId, triggerId, dateFormatter);       
    }

    private Response getEvents(final UserKey userKey, final Integer serverId, final String triggerId, DateFormatter dateFormatter) {
        try {
            ZabbixConnectionDescriptor conn = this.getConnection(userKey, serverId);
            ZabbixClient client = getClient(conn);
            List<ZabbixEvent> e = client.getEventsForTrigger(triggerId, new DateFormatterImpl(dateFormatter));
            
            JSONObject root = new JSONObject();
            root.put("results", toArray(e, dateFormatter, conn.getUrl()));
            return Response.ok(root.toString()).build();
        } catch (ZabbixResourceException | ZabbixClientException | JSONException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }
    private JSONArray toArray(List<ZabbixEvent> events, DateFormatter dateFormatter, String baseUrl) throws JSONException {
        JSONArray arr = new JSONArray();
        for (ZabbixEvent event : events) {
            arr.put(toObject(event, dateFormatter, baseUrl));
        }
        return arr;
    }
    private JSONObject toObject(ZabbixEvent event, DateFormatter dateFormatter, String baseUrl) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("event-id", event.getEventId());
        obj.put("object-id", event.getObjectId());
        obj.put("timestamp", dateFormatter.format(event.getClock()));
        obj.put("url", String.format("%s/tr_events.php?triggerid=%s&eventid=%s", baseUrl, event.getObjectId(), event.getEventId()));
        return obj;
    }

    public ZabbixEventResource(I18nResolver resolver, UserManager userManager,
            PluginLicenseManager licenseManager, UserAccessor userAccessor,
            FormatSettingsManager formatSettingsManager, LocaleManager localeManager,
            ActiveObjects ao, SettingsManager settingsManager) {
        super(resolver, userManager, licenseManager, userAccessor,
            formatSettingsManager, localeManager, ao, settingsManager);
    }
}