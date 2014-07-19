/*******************************************************************************
 *  Copyright (c) 2014 Mentor Graphics and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     Mentor Graphics - initial API and implementation
 *******************************************************************************/
package com.codesourcery.internal.installer.ui.pages;

import java.io.File;
import java.text.MessageFormat;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.wizard.IWizardPage;

import com.codesourcery.installer.IInstallConsoleProvider;
import com.codesourcery.installer.IInstallMode;
import com.codesourcery.installer.IInstallWizardPage;
import com.codesourcery.installer.Installer;
import com.codesourcery.installer.console.ConsoleYesNoPrompter;
import com.codesourcery.installer.ui.IInstallSummaryProvider;
import com.codesourcery.internal.installer.IInstallPlan;
import com.codesourcery.internal.installer.InstallMessages;
import com.codesourcery.internal.installer.RepositoryManager;
import com.codesourcery.internal.installer.ui.UIUtils;

/**
 * A page that shows summary information from all pages that support
 * {@link IInstallSummaryProvider}.
 * This page supports console.
 */
public class SummaryPage extends InformationPage implements IInstallConsoleProvider {
	/**
	 * Constructor
	 * 
	 * @param pageName Page name
	 * @param title Page title
	 */
	public SummaryPage(String pageName, String title) {
		super(pageName, title);
		
		// Set information title
		setInformationTitle(InstallMessages.SummaryMessage);
		// Enable scrolling
		setScrollable(true);
	}

	/**
	 * Returns the install summary.
	 * 
	 * @return Install summary
	 */
	private String getInstallSummary() {
		StringBuffer buffer = new StringBuffer();

		IInstallWizardPage[] pages = Installer.getDefault().getInstallManager().getSupportedWizardPages();
		// Get the wizard pages
		for (IWizardPage page : pages) {
			// If the page provides summary information
			if (page instanceof IInstallSummaryProvider) {
				IInstallSummaryProvider provider = (IInstallSummaryProvider)page;
				buffer.append(provider.getInstallSummary());
			}
		}
		
		return buffer.toString();
	}
	
	/**
	 * Updates the summary.
	 */
	private void updateSummary() {
		// Install summary
		if (RepositoryManager.getDefault().hasInstallUnits()) {
			setInformationTitle(InstallMessages.SummaryMessage);
			setInformation(getInstallSummary());
		}
		// Nothing to install
		else {
			setInformationTitle(InstallMessages.Error_NothingToInstall);
		}
	}
	
	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		
		// Set the page complete.  This will enable install if a previous page
		// is moved to even if there is an install problem.  Install will move
		// back to this page if there is an error.
		setPageComplete(true);

		if (visible) {
			// Update status.  This will set the page incomplete on an install
			// error.
			updateInstallStatus();
			// Update summary
			updateSummary();
		}
	}

	/**
	 * Returns the install plan status.
	 * 
	 * @return Status
	 */
	private IStatus getInstallPlanStatus() {
		IStatus status = Status.OK_STATUS;
		
		// Compute install plan
		IInstallPlan plan = RepositoryManager.getDefault().computeInstallPlan(null);
		if (plan != null) {
			// Plan has errors
			if (!plan.getStatus().isOK()) {
				status = new Status(IStatus.ERROR, Installer.ID, plan.getErrorMessage());
			}
			// Plan successful, check required space
			else {
				IPath installLocation = Installer.getDefault().getInstallManager().getInstallLocation();
				if (installLocation != null) {
					File installDirectory = installLocation.toFile();
					while (!installDirectory.exists()) {
						installDirectory = installDirectory.getParentFile();
					}
					long bytesFree = installDirectory.getUsableSpace();
					if (plan.getSize() > bytesFree) {
						String message = MessageFormat.format(
								InstallMessages.SummaryPage_0,
								new Object[] { UIUtils.formatBytes(plan.getSize()),
										UIUtils.formatBytes(bytesFree) });
						status = new Status(IStatus.WARNING, Installer.ID, message);
					}
				}
			}						
		}
		
		return status;
	}
	
	@Override
	public String getConsoleResponse(String input)
			throws IllegalArgumentException {

		// Replace non-console elements
		String summary = InstallMessages.SummaryConsoleMessage + getInstallSummary();
		summary = summary.replace("<b>", "");
		summary = summary.replace("</b>", "");
		summary = summary.replace("<i>", "");
		summary = summary.replace("</i>", "");
		
		IStatus status = getInstallPlanStatus();
		if (!status.isOK()) {
			summary += getStatusMessage(new IStatus[] { status });
		}

		ConsoleYesNoPrompter prompter = new ConsoleYesNoPrompter(summary, InstallMessages.SummaryPage_ProceedPrompt, true);
		String response = prompter.getConsoleResponse(input);
		if (response == null) {
			// Installation cancelled
			if (!prompter.getResult()) {
				throw new IllegalArgumentException(InstallMessages.SummaryPage_InstallationCancelled);
			}
		}
		
		return response;
	}

	/**
	 * Updates the install status.  Any errors or warnings for the install
	 * plan will be displayed.
	 */
	public void updateInstallStatus() {
		// If not running in console mode
		if (!isConsoleMode()) {
			final IStatus[] status = new IStatus[] { Status.OK_STATUS };
			
			// Run validation busy operation
			runOperation(InstallMessages.ValidatingInstall, new Runnable() {
				@Override
				public void run() {
					status[0] = getInstallPlanStatus();
				}
			});
			
			// Show plan error/warning status
			if (!status[0].isOK()) {
				setPageComplete(status[0].getSeverity() != IStatus.ERROR);
				showStatus(status);
			}
			else {
				setPageComplete(true);
				hideStatus();
			}
		}
	}
	
	@Override
	public boolean validate() {
		return !hasStatusError();
	}

	@Override
	public boolean isSupported() {
		IInstallMode mode = Installer.getDefault().getInstallManager().getInstallMode();
		
		return mode.isInstall();
	}
}
