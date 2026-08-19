package com.blueshelf.core.servlets;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * A miniature HTML Library Manager: serves a {@code cq:ClientLibraryFolder} as ONE css/js file.
 *
 * GET /apps/blueshelf/clientlibs/clientlib-site.css  →  [dependencies' css...] + files listed in css.txt
 * GET /apps/blueshelf/clientlibs/clientlib-site.js   →  same for js.txt
 *
 * What AEM adds on top (know these for interviews): minification (YUI/GCC), `/etc.clientlibs` proxy for
 * /apps & /libs libs with {@code allowProxy=true} (so publish never exposes /apps), long-term cache keys
 * ("lc-<hash>-lc" in the path), `embed` vs `dependencies` (embed = inline into this file, dependencies =
 * separate &lt;link&gt; tags), categories resolution across the whole repository, channel/media attributes,
 * and the {@code ui:includeClientLib}/{@code clientlib.html} HTL template.
 *
 * Gotcha people hit constantly: the folder node type MUST be cq:ClientLibraryFolder and the files MUST be
 * listed in css.txt/js.txt (with {@code #base=css}) — otherwise the category silently resolves to nothing.
 */
@Component(service = Servlet.class)
@SlingServletResourceTypes(resourceTypes = "cq/ClientLibraryFolder", extensions = {"css", "js"}, methods = "GET")
public class ClientLibraryServlet extends SlingSafeMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ClientLibraryServlet.class);
    private static final String[] SEARCH_ROOTS = {"/apps", "/libs"};

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        String ext = request.getRequestPathInfo().getExtension();
        Resource lib = request.getResource();
        response.setContentType("js".equals(ext) ? "application/javascript" : "text/css");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "public, max-age=300");

        StringBuilder out = new StringBuilder();
        Set<String> visited = new LinkedHashSet<>();
        render(lib, ext, out, visited, request.getResourceResolver());
        response.getWriter().write(out.toString());
    }

    private void render(Resource lib, String ext, StringBuilder out, Set<String> visited, ResourceResolver rr) throws IOException {
        if (!visited.add(lib.getPath())) return; // cycle guard
        // 1. dependencies / embed first (order matters for CSS cascade + JS globals)
        for (String prop : new String[]{"dependencies", "embed"}) {
            String[] cats = lib.getValueMap().get(prop, String[].class);
            if (cats == null) continue;
            for (String cat : cats) {
                // NOTE: we resolve with the REQUEST's resolver; anonymous can't read /apps itself (only the
                // clientlibs folder we opened via repoinit), so also scan relative to this library's parent.
                // AEM's HTML Library Manager uses a service user + an index for this.
                for (Resource dep : findByCategory(rr, cat, lib.getParent())) {
                    render(dep, ext, out, visited, rr);
                }
            }
        }
        // 2. own files from css.txt / js.txt
        Resource list = lib.getChild(ext + ".txt");
        if (list == null) return;
        String base = "";
        for (String line : readText(list).split("\\R")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#base=")) { base = line.substring(6).trim(); continue; }
            if (line.startsWith("#")) continue;
            String rel = (base.isEmpty() ? "" : base + "/") + line;
            Resource file = lib.getChild(rel);
            if (file == null) { LOG.warn("clientlib {}: missing file {}", lib.getPath(), rel); continue; }
            out.append("/* ").append(file.getPath()).append(" */\n").append(readText(file)).append('\n');
        }
    }

    /** Find all clientlib folders declaring a category (AEM keeps an index of these; we scan /apps,/libs). */
    static List<Resource> findByCategory(ResourceResolver rr, String category, Resource nearRoot) {
        List<Resource> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        List<Resource> roots = new ArrayList<>();
        if (nearRoot != null) roots.add(nearRoot);
        for (String root : SEARCH_ROOTS) { Resource r = rr.getResource(root); if (r != null) roots.add(r); }
        for (Resource r : roots) scan(r, category, found, seen, 0);
        return found;
    }

    private static void scan(Resource r, String category, List<Resource> found, Set<String> seen, int depth) {
        if (depth > 8 || !seen.add(r.getPath())) return;
        if ("cq:ClientLibraryFolder".equals(r.getValueMap().get("jcr:primaryType", String.class))) {
            String[] cats = r.getValueMap().get("categories", String[].class);
            if (cats != null && Arrays.asList(cats).contains(category)) found.add(r);
            return; // don't descend into a clientlib
        }
        for (Resource c : r.getChildren()) scan(c, category, found, seen, depth + 1);
    }

    private static String readText(Resource file) throws IOException {
        try (InputStream in = file.adaptTo(InputStream.class)) {
            return in == null ? "" : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
