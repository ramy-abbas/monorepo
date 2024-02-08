package org.sakaiproject.scorm.ui.player.components;

import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.IHeaderContributor;
import org.apache.wicket.markup.html.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.Model;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.sakaiproject.scorm.model.api.SessionBean;
import org.sakaiproject.scorm.service.api.LearningManagementSystem;
import org.sakaiproject.scorm.service.api.ScormResourceService;
import org.sakaiproject.scorm.service.api.ScormSequencingService;
import org.sakaiproject.scorm.ui.UISynchronizerPanel;
import org.sakaiproject.scorm.ui.player.pages.PlayerPage;

public class LaunchPanel extends UISynchronizerPanel implements IHeaderContributor {
	private static final long serialVersionUID = 1L;
	
	protected static final String HEADSCRIPTS = "/library/js/headscripts.js";
	protected static final String RESIZESCRIPT = "scripts/resize.js";
	
	private PlayerPage view;
	private ActivityTree tree;
	
	private WebMarkupContainer contentPanel;
	
	@SpringBean
	LearningManagementSystem lms;
	@SpringBean(name="org.sakaiproject.scorm.service.api.ScormResourceService")
	ScormResourceService resourceService;
	@SpringBean(name="org.sakaiproject.scorm.service.api.ScormSequencingService")
	ScormSequencingService sequencingService;
	
	public LaunchPanel(String id, final SessionBean sessionBean, PlayerPage view) {
		super(id, new Model(sessionBean));
		this.view = view;
				
		add(new SjaxContainer("sjaxContainer", sessionBean, this));
				
		contentPanel = new WebMarkupContainer("scormContent");
		contentPanel.setOutputMarkupId(true);
		contentPanel.setMarkupId("scormContent");
		add(contentPanel);
		
		add(tree = new ActivityTree("tree", sessionBean, this));	
	}
	
	public void synchronizeState(SessionBean sessionBean, AjaxRequestTarget target) {		
		if (null != view) {
			view.synchronizeState(sessionBean, target);
		}
	}

	public PlayerPage getView() {
		return view;
	}
	
	@Override
	public ActivityTree getTree() {
		return tree;
	}
	
	public void renderHead(IHeaderResponse response) {
		response.renderJavascriptReference(HEADSCRIPTS);
		response.renderJavascriptReference(RESIZESCRIPT);
		response.renderOnLoadJavascript("initResizing()");
		response.renderOnEventJavascript("window", "resize", "onResize()");
	}

	@Override
	public WebMarkupContainer getContentPanel() {
		return contentPanel;
	}

	public void setContentPanel(WebMarkupContainer contentPanel) {
		this.contentPanel = contentPanel;
	}
}
