/**
 * Copyright (c) 2003-2017 The Apereo Foundation
 *
 * Licensed under the Educational Community License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://opensource.org/licenses/ecl2
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sakaiproject.portal.entityprovider;

import java.io.IOException;
import java.io.StringWriter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.apache.velocity.runtime.RuntimeConstants;

import org.sakaiproject.authz.api.Member;
import org.sakaiproject.authz.api.Role;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.coursemanagement.api.CourseManagementService;
import org.sakaiproject.coursemanagement.api.EnrollmentSet;
import org.sakaiproject.coursemanagement.api.Section;
import org.sakaiproject.entitybroker.entityprovider.capabilities.AutoRegisterEntityProvider;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Describeable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.Outputable;
import org.sakaiproject.entitybroker.entityprovider.capabilities.ActionsExecutable;
import org.sakaiproject.entitybroker.util.AbstractEntityProvider;
import org.sakaiproject.entitybroker.EntityReference;
import org.sakaiproject.entitybroker.EntityView;
import org.sakaiproject.entitybroker.entityprovider.annotations.EntityCustomAction;
import org.sakaiproject.entitybroker.entityprovider.extension.ActionReturn;
import org.sakaiproject.entitybroker.entityprovider.extension.Formats;
import org.sakaiproject.entitybroker.exception.EntityException;

import org.sakaiproject.memory.api.Cache;
import org.sakaiproject.memory.api.MemoryService;
import org.sakaiproject.memory.api.SimpleConfiguration;
import org.sakaiproject.profile2.logic.ProfileConnectionsLogic;
import org.sakaiproject.profile2.logic.ProfileLogic;
import org.sakaiproject.profile2.logic.ProfileLinkLogic;
import org.sakaiproject.profile2.logic.ProfilePrivacyLogic;
import org.sakaiproject.profile2.model.BasicConnection;
import org.sakaiproject.profile2.model.SocialNetworkingInfo;

import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.profile2.model.BasicPerson;
import org.sakaiproject.profile2.model.ProfileImage;
import org.sakaiproject.profile2.model.UserProfile;
import org.sakaiproject.profile2.types.PrivacyType;
import org.sakaiproject.profile2.util.ProfileConstants;

import org.sakaiproject.search.api.SearchList;
import org.sakaiproject.search.api.SearchResult;
import org.sakaiproject.search.api.SearchService;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.SiteService.SelectionType;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.Resource;
import org.sakaiproject.util.ResourceLoader;
import org.sakaiproject.portal.api.BullhornService;
import org.sakaiproject.portal.beans.BullhornAlert;
import org.sakaiproject.portal.beans.PortalNotifications;
import org.sakaiproject.velocity.util.SLF4JLogChute;

/**
 * An entity provider to serve Portal information
 * 
 */
@Setter @Slf4j
public class PortalEntityProvider extends AbstractEntityProvider implements AutoRegisterEntityProvider, Outputable, ActionsExecutable, Describeable {

	public final static String PREFIX = "portal";
	public final static String TOOL_ID = "sakai.portal";
	private final static String CONNECTIONSEARCH_CACHE = "org.sakaiproject.portal.entityprovider.connectionSearchCache";
	private final static String WORKSPACE_IDS_KEY = "workspaceIds";

	private BullhornService bullhornService;
	private MemoryService memoryService;
	private SearchService searchService;
	private SessionManager sessionManager;
	private ProfileConnectionsLogic profileConnectionsLogic;
	private ProfileLogic profileLogic;
	private ProfileLinkLogic profileLinkLogic;

	@Setter
	private ProfilePrivacyLogic privacyLogic;

	@Setter
	private UserDirectoryService userDirectoryService;

	@Setter
	private ServerConfigurationService serverConfigurationService;

	@Setter
	private SiteService siteService;

	@Setter
	private CourseManagementService cms;

	private Template formattedProfileTemplate = null;
	// FIXME remove oldFormattedProfileTemplate once we move to nyu-profile-popup
	private Template oldFormattedProfileTemplate = null;
	private Template profileDrawerTemplate = null;

	public void init() {

		VelocityEngine ve = new VelocityEngine();
		ve.setProperty(VelocityEngine.RUNTIME_LOG_LOGSYSTEM, new SLF4JLogChute());

		ve.setProperty("resource.loader", "class");
		ve.setProperty("class.resource.loader.class", "org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
		// No logging. (If you want to log, you need to set an approrpriate directory in an approrpriate
		// velocity.properties file, or the log will be created in the directory in which tomcat is started, or
		// throw an error if it can't create/write in that directory.)
		ve.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogSystem");
		try {
			ve.init();
			// FIXME remove oldFormattedProfileTemplate once we move to nyu-profile-popup
			oldFormattedProfileTemplate = ve.getTemplate("org/sakaiproject/portal/entityprovider/profile-popup.vm");
			formattedProfileTemplate = ve.getTemplate("org/sakaiproject/portal/entityprovider/nyu-profile-popup.vm");
			profileDrawerTemplate = ve.getTemplate("org/sakaiproject/portal/entityprovider/nyu-profile-drawer.vm");
		} catch (Exception e) {
			log.error("Failed to load profile-popup.vm", e);
		}
	}

	public String getEntityPrefix() {
		return PREFIX;
	}

	public String getAssociatedToolId() {
		return TOOL_ID;
	}

	public String[] getHandledOutputFormats() {
		return new String[] { Formats.TXT ,Formats.JSON, Formats.HTML};
	}

	// So far all we do is errors to solve SAK-29531
	// But this could be extended...
	@EntityCustomAction(action = "notify", viewKey = "")
	public PortalNotifications handleNotify(EntityView view) {
		Session s = sessionManager.getCurrentSession();
		if ( s == null ) {
			throw new IllegalArgumentException("Session not found");
		}
		List<String> retval = new ArrayList<String> ();
                String userWarning = (String) s.getAttribute("userWarning");
                if (StringUtils.isNotEmpty(userWarning)) {
			retval.add(userWarning);
		}
		PortalNotifications noti = new PortalNotifications ();
		noti.setError(retval);
		s.removeAttribute("userWarning");
		return noti;
	}

    private ActionReturn getBullhornAlerts(List<BullhornAlert> alerts) {

        ResourceLoader rl = new ResourceLoader("bullhorns");

		if (alerts.size() > 0) {
			Map<String, Object> data = new HashMap();
			data.put("alerts", alerts);
			data.put("i18n", rl);

			return new ActionReturn(data);
		} else {
			Map<String, String> i18n = new HashMap();
			i18n.put("noAlerts", rl.getString("noAlerts"));

			Map<String, Object> data = new HashMap();
			data.put("message", "NO_ALERTS");
			data.put("i18n", i18n);

			return new ActionReturn(data);
		}
    }

	@EntityCustomAction(action = "socialAlerts", viewKey = EntityView.VIEW_LIST)
	public ActionReturn getSocialAlerts(EntityView view) {
		return getBullhornAlerts(bullhornService.getSocialAlerts(getCheckedCurrentUser()));
	}

	@EntityCustomAction(action = "clearBullhornAlert", viewKey = EntityView.VIEW_LIST)
	public boolean clearBullhornAlert(Map<String, Object> params) {

		String currentUserId = getCheckedCurrentUser();

		try {
			long alertId = Long.parseLong((String) params.get("id"));
			return bullhornService.clearBullhornAlert(currentUserId, alertId);
		} catch (Exception e) {
			log.error("Failed to clear social alert", e);
		}

		return false;
	}

	@EntityCustomAction(action = "clearAllSocialAlerts", viewKey = EntityView.VIEW_LIST)
	public boolean clearAllSocialAlerts(Map<String, Object> params) {

		String currentUserId = getCheckedCurrentUser();

		try {
			return bullhornService.clearAllSocialAlerts(currentUserId);
		} catch (Exception e) {
			log.error("Failed to clear all social alerts", e);
		}

		return false;
	}

	@EntityCustomAction(action = "academicAlerts", viewKey = EntityView.VIEW_LIST)
	public ActionReturn getAcademicAlerts(EntityView view) {
		return getBullhornAlerts(bullhornService.getAcademicAlerts(getCheckedCurrentUser()));
	}

	@EntityCustomAction(action = "clearAllAcademicAlerts", viewKey = EntityView.VIEW_LIST)
	public boolean clearAllAcademicAlerts(Map<String, Object> params) {

		String currentUserId = getCheckedCurrentUser();

		try {
			return bullhornService.clearAllAcademicAlerts(currentUserId);
		} catch (Exception e) {
			log.error("Failed to clear all academic alerts", e);
		}

		return false;
	}

	@EntityCustomAction(action = "bullhornCounts", viewKey = EntityView.VIEW_LIST)
	public ActionReturn getBullhornCounts(EntityView view) {

		String currentUserId = getCheckedCurrentUser();

		Map<String, Integer> counts = new HashMap();
		counts.put("academic", bullhornService.getAcademicAlertCount(currentUserId));
		counts.put("social", bullhornService.getSocialAlertCount(currentUserId));

		return new ActionReturn(counts);
	}

	private String getCheckedCurrentUser() throws SecurityException {

		String currentUserId = developerHelperService.getCurrentUserId();

		if (StringUtils.isBlank(currentUserId)) {
			throw new SecurityException("You must be logged in to use this service");
		} else {
            return currentUserId;
        }
    }

	@EntityCustomAction(action="formatted",viewKey=EntityView.VIEW_SHOW)
	public ActionReturn getFormattedProfile(EntityReference ref, Map<String, Object> params) {

		String currentUserId = getCheckedCurrentUser();

		ResourceLoader rl = new Resource().getLoader("org.sakaiproject.portal.api.PortalService", "profile-popup");

		UserProfile userProfile = (UserProfile) profileLogic.getUserProfile(ref.getId());

		String connectionUserId = userProfile.getUserUuid();

		VelocityContext context = new VelocityContext();
		context.put("i18n", rl);
		context.put("profileUrl", profileLinkLogic.getInternalDirectUrlToUserProfile(connectionUserId));

		// CLASSES-3639 disable this business
//		SocialNetworkingInfo socialInfo = userProfile.getSocialInfo();
//		String facebookUrl = socialInfo.getFacebookUrl();
//		if (StringUtils.isEmpty(facebookUrl)) facebookUrl = "";
//		context.put("facebookUrl", facebookUrl);
//		String twitterUrl = socialInfo.getTwitterUrl();
//		if (StringUtils.isEmpty(twitterUrl)) twitterUrl = "";
//		context.put("twitterUrl", twitterUrl);
//
//		String email = userProfile.getEmail();
//		if (StringUtils.isEmpty(email)) email = "";
//		context.put("email", email);

		context.put("currentUserId", currentUserId);
		context.put("connectionUserId", connectionUserId);
        boolean connectionsEnabled = serverConfigurationService.getBoolean("profile2.connections.enabled",
            ProfileConstants.SAKAI_PROP_PROFILE2_CONNECTIONS_ENABLED);

        if (connectionsEnabled && !currentUserId.equals(connectionUserId)) {
            int connectionStatus = profileConnectionsLogic.getConnectionStatus(currentUserId, connectionUserId);

            if (connectionStatus == ProfileConstants.CONNECTION_CONFIRMED) {
                context.put("connected" , true);
            } else if (connectionStatus == ProfileConstants.CONNECTION_REQUESTED) {
                context.put("requested" , true);
            } else if (connectionStatus == ProfileConstants.CONNECTION_INCOMING) {
                context.put("incoming" , true);
            } else {
                context.put("unconnected" , true);
            }
        }

		// NYU things!
		context.put("you", currentUserId.equals(connectionUserId));
		BasicPerson person = profileLogic.getBasicPerson(connectionUserId);
		context.put("pictureUrl", "/direct/profile/" + connectionUserId + "/image/thumb");
		context.put("displayName", userProfile.getDisplayName());
		String eid;
		try {
			eid = userDirectoryService.getUserEid(connectionUserId);
			context.put("eid", eid);
		} catch (UserNotDefinedException e) {
			// FIXME do something else?
			throw new RuntimeException("User is not real");
		}
		String siteId = (String)params.get("siteId");
		addSectionAndRoleToContext(context, siteId, connectionUserId);
		addConnectionDataToContext(context, currentUserId, connectionUserId);


		StringWriter writer = new StringWriter();

		// FIXME remove switch when we roll out to all sites
		Template templateToUse = oldFormattedProfileTemplate;
		try {
			if ("true".equals(siteService.getSiteVisit(siteId).getProperties().getProperty("course_profile_panel"))) {
				templateToUse = formattedProfileTemplate;
			}
		} catch (IdUnusedException e) {
			// ignore
		} catch (PermissionException e) {
			// ignore
		}

		try {
			templateToUse.merge(context, writer);
			return new ActionReturn(Formats.UTF_8, Formats.HTML_MIME_TYPE, writer.toString());
		} catch (IOException ioe) {
			throw new EntityException("Failed to format profile.", ref.getReference());
		}
	}

	@EntityCustomAction(action="drawer",viewKey=EntityView.VIEW_SHOW)
	public ActionReturn getProfileDrawer(EntityReference ref, Map<String, Object> params) {

		String siteId = (String)params.get("siteId");

		String currentUserId = developerHelperService.getCurrentUserId();

		ResourceLoader rl = new ResourceLoader(currentUserId, "profile-popup");

		UserProfile userProfile = (UserProfile) profileLogic.getUserProfile(ref.getId());

		String connectionUserId = userProfile.getUserUuid();

		VelocityContext context = new VelocityContext();
		context.put("currentUserId", currentUserId);
		context.put("connectionUserId", connectionUserId);
		context.put("you", currentUserId.equals(connectionUserId));

		context.put("displayName", userProfile.getDisplayName());
		context.put("profileUrl", profileLinkLogic.getInternalDirectUrlToUserProfile(connectionUserId));
		context.put("pictureUrl", "/direct/profile/" + connectionUserId + "/image");

		try {
			context.put("eid", userDirectoryService.getUserEid(connectionUserId));
		} catch (UserNotDefinedException e) {
			context.put("eid", "");
		}

		if (privacyLogic.isActionAllowed(connectionUserId, currentUserId, PrivacyType.PRIVACY_OPTION_BASICINFO)) {
			boolean showAge = privacyLogic.isBirthYearVisible(connectionUserId);
			addBasicInfoToContext(context, userProfile, showAge);
		}
		if (privacyLogic.isActionAllowed(connectionUserId, currentUserId, PrivacyType.PRIVACY_OPTION_STAFFINFO)) {
			addStaffInfoToContext(context, userProfile);
		}
		if (privacyLogic.isActionAllowed(connectionUserId, currentUserId, PrivacyType.PRIVACY_OPTION_STUDENTINFO)) {
			addStudentInfoToContext(context, userProfile);
		}
		if (privacyLogic.isActionAllowed(connectionUserId, currentUserId, PrivacyType.PRIVACY_OPTION_PERSONALINFO)) {
			addBiographyToContext(context, userProfile);
		}
		if (privacyLogic.isActionAllowed(connectionUserId, currentUserId, PrivacyType.PRIVACY_OPTION_CONTACTINFO)) {
			addContactInfoToContext(context, userProfile);
		}
		if (privacyLogic.isActionAllowed(connectionUserId, currentUserId, PrivacyType.PRIVACY_OPTION_SOCIALINFO)) {
			addSocialInfoToContext(context, userProfile);
		}
		addSectionAndRoleToContext(context, siteId, connectionUserId);
		addConnectionDataToContext(context, currentUserId, connectionUserId);

		StringWriter writer = new StringWriter();

		try {
			profileDrawerTemplate.merge(context, writer);
			return new ActionReturn(Formats.UTF_8, Formats.HTML_MIME_TYPE, writer.toString());
		} catch (IOException ioe) {
			throw new EntityException("Failed to format profile.", ref.getReference());
		}
	}

	private void addSectionAndRoleToContext(VelocityContext context, String siteId, String connectionUserId) {
		String sections = "";
		String role = "";

		if (!StringUtils.isBlank(siteId)) {
			try {
				Site site = siteService.getSite(siteId);
				Member member = site.getMember(connectionUserId);
				if (member != null) {
					Role memberRole = member.getRole();
					if (memberRole != null) {
						role = memberRole.getId();
					}
					List<String> groups = new ArrayList<>();
					Collection<Group> siteGroups = site.getGroups();
					for (Group group : siteGroups) {
						if (group.getMember(connectionUserId) != null) {
							groups.add(group.getTitle());
						}
					}
					sections = groups.stream().collect(Collectors.joining(", "));
				}
			} catch (IdUnusedException e) {
			}
		}

		context.put("siteId", siteId);
		context.put("sections", sections);
		context.put("role", role);
	}

	private void addConnectionDataToContext(VelocityContext context, String currentUserId, String connectionUserId) {
		boolean connectionsEnabled = serverConfigurationService.getBoolean("profile2.connections.enabled",
			ProfileConstants.SAKAI_PROP_PROFILE2_CONNECTIONS_ENABLED);

		if (connectionsEnabled && !currentUserId.equals(connectionUserId)) {

			int connectionStatus = profileConnectionsLogic.getConnectionStatus(currentUserId, connectionUserId);

			if (connectionStatus == ProfileConstants.CONNECTION_CONFIRMED) {
				context.put("connected", true);
			} else if (connectionStatus == ProfileConstants.CONNECTION_REQUESTED) {
				context.put("requested", true);
			} else if (connectionStatus == ProfileConstants.CONNECTION_INCOMING) {
				context.put("incoming", true);
			} else {
				context.put("unconnected", true);
			}
		}
	}

	private void addBasicInfoToContext(VelocityContext context, UserProfile profile, boolean showAge) {
		context.put("showBasicInfo", true);
		if (StringUtils.isBlank(profile.getNickname())) {
			context.put("preferredName", profile.getDisplayName());
		} else {
			context.put("preferredName", profile.getNickname());
		}

		if (showAge) {
			Date dob = profile.getDateOfBirth();
			if (dob != null) {
				Calendar birthday = new GregorianCalendar();
				birthday.setTime(dob);
				Calendar today = new GregorianCalendar();
				today.setTime(new Date());
				int age = today.get(Calendar.YEAR) - birthday.get(Calendar.YEAR);

				context.put("age", String.valueOf(age));
			}
		}
	}

	private void addStaffInfoToContext(VelocityContext context, UserProfile profile) {
		context.put("showStaffInfo", true);
		context.put("position", profile.getPosition());
		context.put("department", profile.getDepartment());
	}

	private void addStudentInfoToContext(VelocityContext context, UserProfile profile) {
		context.put("showStudentInfo", true);
		context.put("school", profile.getSchool());
		context.put("degree", profile.getCourse());
		context.put("subjects", profile.getSubjects());
	}

	private void addBiographyToContext(VelocityContext context, UserProfile profile) {
		context.put("showBiography", true);
		context.put("personalSummary", profile.getPersonalSummary());
		context.put("staffProfile", profile.getStaffProfile());
	}

	private void addContactInfoToContext(VelocityContext context, UserProfile profile) {
		context.put("showContactInfo", true);
		if (!StringUtils.isBlank(profile.getEmail())) {
			context.put("email", profile.getEmail());
		}
		if (!profile.getPhoneNumbers().isEmpty()) {
			context.put("phoneNumbers", profile.getPhoneNumbers());
		}
	}

	@EntityCustomAction(action="connectionsearch",viewKey=EntityView.VIEW_LIST)
	public ActionReturn searchForConnections(Map<String, Object> params) {

		String currentUserId = getCheckedCurrentUser();

		String query = (String) params.get("query");
		if (StringUtils.isBlank(query)) {
			throw new EntityException("No query supplied", "");
		}

		try {
			Cache cache = getCache(CONNECTIONSEARCH_CACHE);

			List<String> workspaceIds = (List<String>) cache.get(WORKSPACE_IDS_KEY);

			if (workspaceIds == null) {
				log.debug("Cache MISS on {}.", WORKSPACE_IDS_KEY);
				List<String> all = siteService.getSiteIds(SelectionType.ANY, null, null, null, null, null);
				workspaceIds = all.stream().filter(id -> id.startsWith("~")).collect(Collectors.toList());
				cache.put(WORKSPACE_IDS_KEY, workspaceIds);
			} else {
				log.debug("Cache HIT on {}.", WORKSPACE_IDS_KEY);
			}

			if (log.isDebugEnabled()) {
				workspaceIds.forEach(id -> log.debug("workspace id: {}", id));
			}

			SearchList results = searchService.search(query, workspaceIds, 0, 100);

			Set<BasicConnection> hits = results.stream().filter(r -> "profile".equals(r.getTool()))
				.map(r ->
					{
						try {
							return connectionFromUser(userDirectoryService.getUser(r.getId()));
						} catch (UserNotDefinedException unde) {
							log.error("No user for id " + r.getId() + ". Returning null ...");
							return null;
						} catch (Exception e) {
							log.error("Exception caught whilst looking up user " + r.getId() + ". Returning null ...", e);
							return null;
						}
					}).collect(Collectors.toSet());

			if (log.isDebugEnabled()) {
				hits.forEach(hit -> log.debug("User ID: " + hit.getUuid()));
			}

			// Now search the users. TODO: Move to ElasticSearch eventually.
			List<User> users = userDirectoryService.searchUsers(query, 1, 100);
			users.addAll(userDirectoryService.searchExternalUsers(query, 1, 100));

			hits.addAll(users.stream().map(u -> { return connectionFromUser(u); }).collect(Collectors.toList()));

			return new ActionReturn(hits);
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}

		return null;
	}

	private Cache getCache(String name) {

		try {
			return memoryService.getCache(name);
		} catch (Exception e) {
			log.error("Exception whilst retrieving '" + name + "' cache. Returning null ...", e);
			return null;
		}
	}

    private BasicConnection connectionFromUser(User u) {

		BasicConnection bc = new BasicConnection();
		bc.setUuid(u.getId());
		bc.setDisplayName(u.getDisplayName());
		bc.setEmail(u.getEmail());
		bc.setProfileUrl(profileLinkLogic.getInternalDirectUrlToUserProfile(u.getId()));
		bc.setType(u.getType());
		bc.setSocialNetworkingInfo(profileLogic.getSocialNetworkingInfo(u.getId()));
		return bc;
    }

	private void addSocialInfoToContext(VelocityContext context, UserProfile profile) {
		context.put("showSocialInfo", true);
		if (profile.getSocialMedia().isEmpty()) {
			context.put("showSocialInfo", false);
		} else {
			context.put("socialMediaAccounts", profile.getSocialMedia());
		}
	}
}
