/**
 * Copyright (C) 2024 Israel Mateos-Aparicio-Ruiz
 */
package qupath.ext.gncnn;

import org.controlsfx.control.PropertySheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.MenuItem;
import qupath.ext.gncnn.ui.GNCnnCommand;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.GitHubProject;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;

/**
 * QuPath extension to detect and classify glomeruli using deep learning.
 * 
 * @author Israel Mateos Aparicio
 */
public class GNCnnExtension implements QuPathExtension, GitHubProject {

	private static final Logger logger = LoggerFactory.getLogger(GNCnnExtension.class);

	private static final String EXTENSION_NAME = "GNCnn";
	private static final String EXTENSION_DESCRIPTION = "A QuPath extension for glomerulosclerosis and glomerulonephritis characterization based on deep learning.";
	private static final Version EXTENSION_QUPATH_VERSION = Version.parse("v0.5.0");
	private static final GitHubRepo EXTENSION_REPOSITORY = GitHubRepo.create(
			EXTENSION_NAME, "israelMateos", "qupath-extension-gncnn");

	private boolean isInstalled = false;
	private BooleanProperty enableExtensionProperty = PathPrefs.createPersistentPreference(
			"enableExtension", true);

	@Override
	public void installExtension(QuPathGUI qupath) {
		if (isInstalled) {
			logger.debug("{} is already installed", getName());
			return;
		}
		isInstalled = true;
		addMenuItems(qupath);
		addPreferences();
	}

	/**
	 * Adds the needed preferences to the QuPath preferences dialog.
	 */
	private void addPreferences() {
		// Create the items for the preferences dialog
		PropertySheet.Item enableExtensionItem = new PropertyItemBuilder<>(enableExtensionProperty, Boolean.class)
				.propertyType(PropertyItemBuilder.PropertyType.GENERAL)
				.name("Enable " + EXTENSION_NAME)
				.category(EXTENSION_NAME)
				.description("Enable or disable the " + EXTENSION_NAME + " extension.")
				.build();

		// Add the items to the preferences dialog
		QuPathGUI.getInstance().getPreferencePane().getPropertySheet().getItems().add(
				enableExtensionItem);
	}

	/**
	 * Adds a menu item to the Extensions menu.
	 * 
	 * @param qupath
	 */
	private void addMenuItems(QuPathGUI qupath) {
		var menu = qupath.getMenu("Extensions>" + EXTENSION_NAME, true);
		MenuItem menuItem = new MenuItem("Open " + EXTENSION_NAME);

		menuItem.setOnAction(e -> {
			new GNCnnCommand(qupath).run();
		});

		menuItem.disableProperty().bind(enableExtensionProperty.not());

		menu.getItems().add(menuItem);
	}

	@Override
	public String getName() {
		return EXTENSION_NAME;
	}

	@Override
	public String getDescription() {
		return EXTENSION_DESCRIPTION;
	}

	@Override
	public Version getQuPathVersion() {
		return EXTENSION_QUPATH_VERSION;
	}

	@Override
	public GitHubRepo getRepository() {
		return EXTENSION_REPOSITORY;
	}
}
