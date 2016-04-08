package com.mesilat.zabbix;

import com.atlassian.templaterenderer.TemplateRenderer;
import com.atlassian.sal.api.auth.LoginUriProvider;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import java.io.IOException;
import java.net.URI;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ConfigServlet extends HttpServlet {

    private final UserManager userManager;
    private final LoginUriProvider loginUriProvider;
    private final TemplateRenderer renderer;

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        UserKey userKey = userManager.getRemoteUserKey(request);
        if (userKey == null || !userManager.isAdmin(userKey)) {
            response.sendRedirect(loginUriProvider.getLoginUri(getUri(request)).toASCIIString());
        } else {
            response.setContentType("text/html;charset=utf-8");
            renderer.render("config.vm", response.getWriter());
        }
    }

    private URI getUri(HttpServletRequest request) {
        StringBuffer builder = request.getRequestURL();
        if (request.getQueryString() != null) {
            builder.append("?");
            builder.append(request.getQueryString());
        }
        return URI.create(builder.toString());
    }

    public ConfigServlet(UserManager userManager, LoginUriProvider loginUriProvider, TemplateRenderer renderer) {
        this.userManager = userManager;
        this.loginUriProvider = loginUriProvider;
        this.renderer = renderer;
    }
}
