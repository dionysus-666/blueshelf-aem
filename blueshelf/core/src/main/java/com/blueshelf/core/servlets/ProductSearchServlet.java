package com.blueshelf.core.servlets;

import com.blueshelf.core.services.CatalogService;
import com.blueshelf.core.services.catalog.ProductPage;
import com.blueshelf.core.services.catalog.ProductQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;

/**
 * GET /content/blueshelf/us/en.search.json?q=oled&category=tvs   → JSON for type-ahead / SPA.
 *
 * <p><b>resourceType-bound</b> servlet (the AEM-recommended style): it is selected because the requested
 * resource is a page (cq:Page) AND selector "search" AND extension "json". Benefits over
 * path-bound (/bin/...): the request goes through normal resource resolution, so ACLs apply, it can be
 * cached by Dispatcher per page path, and it can't be reached on publish if the page isn't published.</p>
 *
 * <p>Gotcha: Dispatcher ignores query strings by default for caching unless configured (/ignoreUrlParams);
 * a JSON search endpoint like this must be made uncacheable or keyed by params — Phase 5.</p>
 */
@Component(service = Servlet.class)
// Bound to cq/Page: a page NODE's resource type is "cq:Page" (the page COMPONENT type lives on jcr:content),
// so page-level selector servlets (/content/site/en.search.json) are registered against cq/Page in AEM too.
@SlingServletResourceTypes(resourceTypes = "cq/Page", selectors = "search", extensions = "json", methods = "GET")
public class ProductSearchServlet extends SlingSafeMethodsServlet {

    @Reference
    private CatalogService catalogService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        ProductQuery q = new ProductQuery(request.getParameter("category"), request.getParameter("q"),
                parseInt(request.getParameter("page"), 0), parseInt(request.getParameter("size"), 12), request.getParameter("sort"));
        ProductPage page = catalogService.search(q);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, max-age=30"); // never let Dispatcher/CDN cache personalised-ish search JSON for long
        if (!page.isAvailable()) response.setStatus(503);
        mapper.writeValue(response.getWriter(), page);
    }

    private static int parseInt(String s, int def) {
        try { return s == null ? def : Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }
}
