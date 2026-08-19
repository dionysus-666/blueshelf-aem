package com.blueshelf.core.models;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.Self;

import javax.servlet.http.Cookie;

/**
 * Tells HTL whether we are rendering for the page editor.
 *
 * Correlation: AEM's {@code WCMMode.fromRequest(request)} (EDIT / PREVIEW / DISABLED). AEM sets it from the
 * {@code wcmmode} request parameter or cookie; the editor iframe loads pages with {@code ?wcmmode=edit}.
 * In edit mode components render extra wrapper markup (AEM: {@code cq} decoration tags) so the editor can
 * find and overlay them. On publish, WCMMode is always DISABLED.
 */
@Model(adaptables = SlingHttpServletRequest.class)
public class EditContext {

    @Self
    private SlingHttpServletRequest request;

    public boolean isEdit() {
        String param = request.getParameter("wcmmode");
        if (param != null) {
            return "edit".equalsIgnoreCase(param);
        }
        Cookie c = request.getCookie("wcmmode");
        return c != null && "edit".equalsIgnoreCase(c.getValue());
    }
}
