package com.blueshelf.core.servlets;

import com.blueshelf.core.models.StyleModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * GET <any component path>.styles.json → { available: [...], selected: [...] } — used by the editor's Styles tab.
 * Bound to "sling/servlet/default" = applies to EVERY resource type (the default servlet's type), narrowed by
 * selector+extension. Handy for cross-cutting author tooling (AEM does the same for e.g. .infinity.json).
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(resourceTypes = "sling/servlet/default", selectors = "styles", extensions = "json", methods = "GET")
public class StylesServlet extends SlingSafeMethodsServlet {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        StyleModel style = request.adaptTo(StyleModel.class);
        // selected ids come from the model (same injection path HTL uses)
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), Map.of(
                "available", style == null ? List.of() : style.getAvailable(),
                "selected", style == null ? List.of() : style.getSelected(),
                "cssClasses", style == null ? "" : style.getCssClasses()));
    }
}
