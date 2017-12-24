package com.mesilat.zabbix;

import com.atlassian.activeobjects.external.ActiveObjects;
import com.atlassian.confluence.languages.LocaleManager;
import com.atlassian.confluence.setup.settings.SettingsManager;
import com.atlassian.confluence.user.UserAccessor;
import com.mesilat.format.ItemFormat;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.atlassian.sal.api.user.UserProfile;
import com.mesilat.format.CompiledFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.codehaus.jackson.node.ObjectNode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@Path("/format")
public class FormatResource extends ZabbixResourceBase {
    private final ActiveObjects ao;

    @GET
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response get(@Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);

        try {
            JSONObject results = new JSONObject();
            results.put("results", toArray(findFormats(userKey)));
            return Response.ok(results.toString()).build();
        } catch (JSONException ex) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }
    }

    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + "; charset=utf-8")
    public Response put(final ObjectNode obj, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);

        if (obj.get("name") == null || obj.get("format") == null){
            return Response.status(Response.Status.BAD_REQUEST).entity(
                getResolver().getText("com.mesilat.zabbix-plugin.error.format.mandatory-params-not-found")
            ).build();
        }

        return ao.executeInTransaction(() -> {
            try {
                ItemFormat format;
                if (obj.get("id") == null){
                    format = ao.create(ItemFormat.class);
                    format.setOwnerKey(userKey.getStringValue());
                } else {
                    format = ao.get(ItemFormat.class, obj.get("id").asInt());
                    if (!userKey.getStringValue().equals(format.getOwnerKey())
                            && !getUserManager().isAdmin(userKey)
                    ){
                        return Response.status(Response.Status.FORBIDDEN).entity(
                            getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")
                        ).build();
                    }
                }
                format.setName(obj.get("name").asText());
                format.setFormat(obj.get("format").asText());
                format.setPublicFormat(obj.get("public") != null && obj.get("public").asBoolean()? "Y": "N");
                // Check if format is valid
                CompiledFormat.compile(format.getFormat(), getBaseUrl(), getLocale(userKey));
                format.save();
                JSONObject results = new JSONObject();
                results.put("results", toObject(format));
                return Response.ok(results.toString()).build();
            } catch (JSONException | ParseException ex) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
            }
        });
    }
    
    @DELETE
    public Response delete(@QueryParam("id") Integer id, @Context HttpServletRequest request) {
        UserKey userKey = getUserManager().getRemoteUserKey(request);

        return ao.executeInTransaction(() -> {
            ItemFormat format = ao.get(ItemFormat.class, id);
            if (!userKey.getStringValue().equals(format.getOwnerKey()) && !getUserManager().isAdmin(userKey)){
                return Response.status(Response.Status.FORBIDDEN).entity(
                    getResolver().getText("com.mesilat.zabbix-plugin.error.not-authorized")
                ).build();
            }
            ao.delete(format);
            return Response.ok().build();
        });
    }

    private List<ItemFormat> findFormats(UserKey userKey){
        return ao.executeInTransaction(() -> {
            List<ItemFormat> formats = new ArrayList<>();
            if (getUserManager().isAdmin(userKey)){
                // Owner formats
                for (ItemFormat format : ao.find(ItemFormat.class, "OWNER_KEY = ?", userKey.getStringValue())){
                    formats.add(format);
                }
                // Imported formats
                for (ItemFormat format : ao.find(ItemFormat.class, "OWNER_KEY IS NULL")){
                    formats.add(format);
                }
                // Other formats
                for (ItemFormat format : ao.find(ItemFormat.class, "OWNER_KEY <> ?", userKey.getStringValue())){
                    formats.add(format);
                }
            } else {
                // Owner formats
                for (ItemFormat format : ao.find(ItemFormat.class, "OWNER_KEY = ?", userKey.getStringValue())){
                    formats.add(format);
                }
                // Public formats
                for (ItemFormat format : ao.find(ItemFormat.class, "PUBLIC_FORMAT = 'Y' and (OWNER_KEY <> ? or OWNER_KEY IS NULL)", userKey.getStringValue())){
                    formats.add(format);
                }
            }
            return formats;
        });
    }
    private JSONObject toObject(ItemFormat format) throws JSONException{
        JSONObject obj = new JSONObject();
        obj.put("id", format.getID());
        obj.put("name", format.getName());
        obj.put("format", format.getFormat());
        if (format.getOwnerKey() != null){
            UserProfile owner = getUserManager().getUserProfile(new UserKey(format.getOwnerKey()));
            obj.put("ownerName", owner.getUsername());
            obj.put("ownerFullName", owner.getFullName());
            obj.put("public", "Y".equalsIgnoreCase(format.isPublicFormat()));
        } else {
            obj.put("public", Boolean.TRUE);
        }
        return obj;
    }
    private JSONArray toArray(Collection<ItemFormat> formats) throws JSONException{
        JSONArray arr = new JSONArray();
        for (ItemFormat format: formats){
            arr.put(toObject(format));
        }
        return arr;
    }

    public FormatResource(I18nResolver resolver, UserManager userManager, ActiveObjects ao,
            SettingsManager settingsManager, LocaleManager localeManager,
            UserAccessor userAccessor) {
        super(resolver, userManager, null, userAccessor, null, localeManager, ao, settingsManager);

        this.ao = ao;
    }
}