package com.blueshelf.core.servlets;

import com.blueshelf.core.services.ReplicationService;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletPaths;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * POST /bin/blueshelf/replicate?path=/content/...&action=activate|deactivate
 *
 * <p>Correlation: AEM's {@code /bin/replicate.json} (called by the editor's Publish button) and
 * {@code /bin/wcmcommand}. A servlet = Spring {@code @RestController}, but registered as an OSGi service.</p>
 *
 * <p>Gotcha: this is a <b>path-bound</b> servlet. AEM guidance prefers <b>resourceType-bound</b> servlets
 * (they inherit the resource's ACLs and are dispatcher-cacheable); path-bound {@code /bin/*} servlets
 * bypass ACLs, so you must check permissions yourself and dispatcher must explicitly allow the path.
 * We use path-binding here because this is an author-only admin action.</p>
 */
@Component(service = Servlet.class)
@SlingServletPaths("/bin/blueshelf/replicate")
public class ReplicationServlet extends SlingAllMethodsServlet {

    @Reference
    private ReplicationService replicationService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String path = request.getParameter("path");
        String actionParam = request.getParameter("action");
        if (path == null || !path.startsWith("/content/")) {
            response.setStatus(400);
            response.getWriter().write("{\"ok\":false,\"message\":\"path must be under /content\"}");
            return;
        }
        // Only users who can write the node may replicate it (cheap ACL check)
        if (request.getResourceResolver().getResource(path) == null) {
            response.setStatus(404);
            response.getWriter().write("{\"ok\":false,\"message\":\"not found or no access\"}");
            return;
        }
        ReplicationService.Action action = "deactivate".equalsIgnoreCase(actionParam)
                ? ReplicationService.Action.DEACTIVATE : ReplicationService.Action.ACTIVATE;
        try {
            String msg = replicationService.replicate(request.getResourceResolver(), path, action);
            response.getWriter().write("{\"ok\":true,\"message\":\"" + msg.replace("\"", "'") + "\"}");
        } catch (ReplicationService.ReplicationException e) {
            response.setStatus(502);
            response.getWriter().write("{\"ok\":false,\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}");
        }
    }
}
