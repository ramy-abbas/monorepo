package edu.nyu.classes.seats.handlers;

import edu.nyu.classes.seats.Emails;
import edu.nyu.classes.seats.models.Meeting;
import edu.nyu.classes.seats.models.Member;
import edu.nyu.classes.seats.models.SeatGroup;
import edu.nyu.classes.seats.models.SeatSection;
import edu.nyu.classes.seats.storage.Locks;
import edu.nyu.classes.seats.storage.SeatsStorage;
import edu.nyu.classes.seats.storage.db.DBConnection;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.cover.SiteService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

import org.sakaiproject.email.cover.EmailService;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.user.api.User;


public class EmailGroupHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        DBConnection db = (DBConnection)context.get("db");
        String siteId = (String)context.get("siteId");

        RequestParams p = new RequestParams(request);
        String sectionId = p.getString("sectionId", null);
        String groupId = p.getString("groupId", null);

        String subject = p.getString("subject", "");
        String body = p.getString("body", "");

        Optional<SeatSection> section = SeatsStorage.getSeatSection(db, sectionId, siteId);

        if (!section.isPresent()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Optional<SeatGroup> group = section.get().listGroups().stream().filter((g) -> g.id.equals(groupId)).findFirst();

        if (!group.isPresent()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        List<String> netids = group.get().listMembers().stream().map((m) -> m.netid).collect(Collectors.toList());
        List<User> users = UserDirectoryService.getUsersByEids(netids);

        Emails.sendPlaintextEmail(users,
                                  SiteService.getSite(siteId),
                                  subject,
                                  body);

        response.getWriter().write("{}");
    }

    @Override
    public String getContentType() {
        return "text/json";
    }

    @Override
    public boolean hasTemplate() {
        return false;
    }

    @Override
    public Map<String, List<String>> getFlashMessages() {
        return new HashMap<String, List<String>>();
    }

    public Errors getErrors() {
        return null;
    }

    public boolean hasRedirect() {
        return false;
    }

    public String getRedirect() {
        return "";
    }
}


