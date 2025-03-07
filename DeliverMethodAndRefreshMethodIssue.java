package com.mbag.mbse.rhp.sysspec.modelling.deliver;

import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ILoginHandler2;
import com.ibm.team.repository.client.ILoginInfo2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.client.login.UsernameAndPasswordLoginInfo;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.scm.client.IWorkspaceConnection;
import com.ibm.team.scm.client.IWorkspaceManager;
import com.ibm.team.scm.client.SCMPlatform;
import com.ibm.team.scm.common.IComponent;
import com.ibm.team.scm.common.IComponentHandle;
import com.ibm.team.scm.common.IWorkspaceHandle;
import com.ibm.team.scm.common.WorkspaceComparisonFlags;
import com.ibm.team.scm.common.dto.IChangeHistorySyncReport;
import com.ibm.team.scm.common.dto.IWorkspaceSearchCriteria;
import com.mbag.mbse.rhp.sysspec.modelling.variantintegration.LoginToPlatform;
import com.telelogic.rhapsody.core.IRPApplication;
import com.telelogic.rhapsody.core.IRPModelElement;
import com.telelogic.rhapsody.core.IRPProject;
import com.telelogic.rhapsody.core.RhapsodyAppServer;

public class DeliverMethodAndRefreshMethodIssue {

	public static IRPApplication rhpApp;
	public static IRPProject project;
	public static IRPModelElement selEle;
	private static ITeamRepository teamRepository;
	static LoginToPlatform loginDialog;

	static String serverUri = "https://xyz.com/am";

	static IProgressMonitor monitor = new NullProgressMonitor();
	static IWorkspaceManager workspaceManager;
	static IWorkspaceConnection workspaceConnection;
	static IWorkspaceConnection streamConnection;
	static String streamName = "Name";

	public static void main(String[] args) throws Exception {

		rhpApp = RhapsodyAppServer.getActiveRhapsodyApplication();
		project = rhpApp.activeProject();
		selEle = rhpApp.getSelectedElement();

		project.save();

		TeamPlatform.startup();
		try {
			teamRepository = loginToRepository(serverUri, Constants.username, Constants.password, monitor);
			workspaceManager = SCMPlatform.getWorkspaceManager(teamRepository);
			workspaceConnection = getWorkspaceConnection(workspaceManager, monitor); // from local
			streamConnection = getStreamConnection(workspaceManager, monitor); // from server

			IComponent comp = fetchTheTargetComponent();

			IChangeHistorySyncReport changeSetSync = workspaceConnection.compareTo(streamConnection,
					WorkspaceComparisonFlags.CHANGE_SET_COMPARISON_ONLY, Collections.EMPTY_LIST, monitor);
			System.out.println(changeSetSync);
			System.out.println(changeSetSync.getOutgoingChangeSetCopies());

			// Deliver the change sets
			workspaceConnection.deliver(streamConnection, changeSetSync, Collections.EMPTY_LIST,
					changeSetSync.outgoingChangeSets(comp), monitor);
			System.out.println("Change Set delivered...");

			// Create a baseline and compare the repository workspace with the
			// stream to find the changes and deliver the baselines
			workspaceConnection.createBaseline(fetchTheTargetComponent(), "Demo_BL_prep", "testing", monitor);
			System.out.println("Comparinglines...");
			IChangeHistorySyncReport baselineSync = workspaceConnection.compareTo(streamConnection,
					WorkspaceComparisonFlags.INCLUDE_BASELINE_INFO, Collections.EMPTY_LIST, monitor);

			// Deliver the baselines
			workspaceConnection.deliver(streamConnection, baselineSync, baselineSync.outgoingBaselines(comp),
					baselineSync.outgoingChangeSets(comp), monitor);
			System.out.println("BL delivered...");
			
			//refresh doesnt work
			workspaceConnection.refresh(monitor);

		} catch (TeamRepositoryException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private static IComponent fetchTheTargetComponent() throws TeamRepositoryException {
		@SuppressWarnings("unchecked")
		List<IComponentHandle> components = workspaceConnection.getComponents();
		for (IComponentHandle componentHandle : components) {

			IComponent component = (IComponent) teamRepository.itemManager().fetchCompleteItem(componentHandle,
					IItemManager.DEFAULT, monitor);
			System.out.println(component.getName());
			if (component.getName().equals("name")) {
				return component;
			}
		}
		return null;

	}

	public static IWorkspaceConnection getStreamConnection(IWorkspaceManager workspaceManager, IProgressMonitor monitor)
			throws TeamRepositoryException {
		IWorkspaceSearchCriteria criteria = IWorkspaceSearchCriteria.FACTORY.newInstance();
		criteria.setKind(IWorkspaceSearchCriteria.ALL);

		criteria.setExactName(streamName);
		List<IWorkspaceHandle> streams = workspaceManager.findWorkspaces(criteria, IWorkspaceSearchCriteria.STREAMS,
				monitor);
		if (!streams.isEmpty()) {
			IWorkspaceHandle streamHandle = streams.get(0);
			return workspaceManager.getWorkspaceConnection(streamHandle, monitor);
		}
		return null;
	}

	private static IWorkspaceConnection getWorkspaceConnection(IWorkspaceManager workspaceManager,
			IProgressMonitor monitor) throws TeamRepositoryException {
		IWorkspaceSearchCriteria criteria = IWorkspaceSearchCriteria.FACTORY.newInstance();
		criteria.setKind(IWorkspaceSearchCriteria.ALL);
		criteria.setExactName(processWorkspace());   //process current workspace
		List<IWorkspaceHandle> workspaces = workspaceManager.findWorkspaces(criteria,
				IWorkspaceSearchCriteria.WORKSPACES, monitor);
		if (!workspaces.isEmpty()) {
			IWorkspaceHandle workspaceHandle = workspaces.get(0);
			return workspaceManager.getWorkspaceConnection(workspaceHandle, monitor);
		}
		return null;
	}

	private static ITeamRepository loginToRepository(String repoUri, String userId, String password,
			IProgressMonitor monitor) throws Exception {
		ITeamRepository teamRepository = TeamPlatform.getTeamRepositoryService().getTeamRepository(repoUri);
		teamRepository.registerLoginHandler(getLoginHandler(userId, password));
		teamRepository.login(monitor);
		return teamRepository;
	}

	private static ILoginHandler2 getLoginHandler(final String userName, final String password) {
		return new ILoginHandler2() {
			public ILoginInfo2 challenge(final ITeamRepository repo) {
				return new UsernameAndPasswordLoginInfo(userName, password);
			}
		};
	}

	private static String processWorkspace() {

		String currentDirectory = project.getCurrentDirectory();
		String[] array = currentDirectory.split("\\\\");
		String workspaceName = extractWorkspaceName(array);
		System.out.println("ws name" + workspaceName);
		return workspaceName;
	}

	private static String extractWorkspaceName(String[] array) {
		for (int i = array.length - 1; i > 0; i--) {
			if (array[i].toLowerCase().contains("workspace")) {
				return array[i];
			}
		}
		return null;
	}
}
