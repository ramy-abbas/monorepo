package edu.nyu.classes.seats.handlers;

import edu.nyu.classes.seats.api.SeatsService;
import edu.nyu.classes.seats.models.Meeting;
import edu.nyu.classes.seats.models.SeatAssignment;
import edu.nyu.classes.seats.models.SeatGroup;
import edu.nyu.classes.seats.models.SeatSection;
import edu.nyu.classes.seats.storage.SeatsStorage;
import edu.nyu.classes.seats.storage.db.DBConnection;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.site.cover.SiteService;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.cover.UserDirectoryService;
import org.sakaiproject.component.cover.HotReloadConfigurationService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class StudentMeetingsHandler implements Handler {

    protected String redirectTo = null;

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Map<String, Object> context) throws Exception {
        String siteId = (String)context.get("siteId");
        DBConnection db = (DBConnection)context.get("db");
        User currentUser = UserDirectoryService.getCurrentUser();

        response.addHeader("X-Poll-Frequency",
                           HotReloadConfigurationService.getString("seats.student-poll-ms", "20000"));

        JSONObject result = new JSONObject();

        if ("Student".equals(SecurityService.getUserEffectiveRole(SiteService.siteReference(siteId)))) {
            // instructor pretending to be a student
            result.put("roleSwap", true);
        }

        JSONArray meetings = new JSONArray();
        result.put("meetings", meetings);

        for (SeatSection section : SeatsStorage.siteSeatSections(db, siteId)) {
            JSONObject obj = new JSONObject();
            obj.put("netid", currentUser.getEid());
            obj.put("studentName", currentUser.getDisplayName());
            obj.put("sectionId", section.id);
            obj.put("sectionName", section.name);
            obj.put("sectionShortName", section.shortName);

            for (SeatGroup group : section.listGroups()) {
                if (group.listMembers().stream().anyMatch(m -> m.netid.equals(currentUser.getEid()))) {
                    obj.put("groupId", group.id);
                    obj.put("groupName", group.name);
                    obj.put("groupDescription", group.description);

                    for (Meeting meeting : group.listMeetings()) {
                        obj.put("meetingId", meeting.id);

                        Optional<SeatAssignment> seatAssignment = meeting.listSeatAssignments().stream().filter(a -> a.netid.equals(currentUser.getEid())).findFirst();
                        if (seatAssignment.isPresent()) {
                            obj.put("seat", seatAssignment.get().seat);
                            obj.put("editableUntil", seatAssignment.get().editableUntil);
                        } else {
                            obj.put("seat", null);
                            obj.put("editableUntil", null);
                        }

                        meetings.add(obj.clone());
                    }
                }
            }
        }

        try {
            response.getWriter().write(result.toString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    @Override
    public boolean isSiteUpdRequired() {
        return false;
    }
}


