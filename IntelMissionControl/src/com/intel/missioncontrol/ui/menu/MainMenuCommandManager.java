/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol.ui.menu;

import com.google.inject.Inject;
import com.intel.missioncontrol.IApplicationContext;
import com.intel.missioncontrol.api.IFlightPlanService;
import com.intel.missioncontrol.api.support.ISupportManager;
import com.intel.missioncontrol.beans.property.PropertyPath;
import com.intel.missioncontrol.concurrent.Dispatcher;
import com.intel.missioncontrol.diagnostics.PerformanceMonitorViewModel;
import com.intel.missioncontrol.hardware.IHardwareConfigurationManager;
import com.intel.missioncontrol.helper.ILanguageHelper;
import com.intel.missioncontrol.helper.WindowHelper;
import com.intel.missioncontrol.map.worldwind.IWWMapModel;
import com.intel.missioncontrol.map.worldwind.IWWMapView;
import com.intel.missioncontrol.mission.FlightPlan;
import com.intel.missioncontrol.mission.IMissionInfo;
import com.intel.missioncontrol.mission.IMissionManager;
import com.intel.missioncontrol.mission.ISaveable;
import com.intel.missioncontrol.mission.Matching;
import com.intel.missioncontrol.mission.MatchingStatus;
import com.intel.missioncontrol.mission.Mission;
import com.intel.missioncontrol.mission.MissionConstants;
import com.intel.missioncontrol.settings.GeneralSettings;
import com.intel.missioncontrol.settings.ISettingsManager;
import com.intel.missioncontrol.settings.OperationLevel;
import com.intel.missioncontrol.settings.PathSettings;
import com.intel.missioncontrol.ui.common.components.RenameDialog;
import com.intel.missioncontrol.ui.dialogs.IDialogService;
import com.intel.missioncontrol.ui.dialogs.IVeryUglyDialogHelper;
import com.intel.missioncontrol.ui.dialogs.SendSupportDialogViewModel;
import com.intel.missioncontrol.ui.dialogs.about.AboutDialogViewModel;
import com.intel.missioncontrol.ui.dialogs.savechanges.SaveChangesDialogViewModel;
import com.intel.missioncontrol.ui.navigation.INavigationService;
import com.intel.missioncontrol.ui.navigation.SidePanePage;
import com.intel.missioncontrol.ui.navigation.WorkflowStep;
import com.intel.missioncontrol.ui.notifications.Toast;
import com.intel.missioncontrol.ui.notifications.ToastType;
import de.saxsys.mvvmfx.ViewModel;
import eu.mavinci.core.licence.ILicenceManager;
import eu.mavinci.desktop.helper.FileFilter;
import eu.mavinci.desktop.helper.FileHelper;
import eu.mavinci.desktop.helper.MFileFilter;
import eu.mavinci.desktop.main.debug.Debug;
import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ObservableList;
import org.apache.commons.io.FileUtils;

/** Contains implementations for main menu commands that don't better fit somewhere else. */
public class MainMenuCommandManager {

    private static final String MANUAL_PATH_ARG = "manual_path";
    private static final String DEFAULT_MANUAL_FOLDER = "../manuals";
    private static final String MANUAL_FILE = "omc-quick-start-guide.pdf";
    private static final String QUICK_START_GUIDE_FILE = "omc-quick-start-guide.pdf";

    private final ViewModel ownerViewModel;
    private final IApplicationContext applicationContext;
    private final ILanguageHelper languageHelper;
    private final IMissionManager missionManager;
    private final IFlightPlanService flightPlanService;
    private final IVeryUglyDialogHelper dialogHelper;
    private final IDialogService dialogService;
    private final INavigationService navigationService;
    private final ISupportManager supportManager;
    private final IWWMapModel mapModel;
    private final IWWMapView mapView;
    private final ILicenceManager licenceManager;
    private final IHardwareConfigurationManager hardwareConfigurationManager;
    private final ISettingsManager settingsManager;

    @Inject
    public MainMenuCommandManager(
            ViewModel ownerViewModel,
            MenuModel menuModel,
            INavigationService navigationService,
            IApplicationContext applicationContext,
            IDialogService dialogService,
            ILanguageHelper languageHelper,
            IMissionManager missionManager,
            IFlightPlanService flightPlanService,
            IVeryUglyDialogHelper dialogHelper,
            ISupportManager supportManager,
            ISettingsManager settingsManager,
            ILicenceManager licenceManager,
            IWWMapModel mapModel,
            IWWMapView mapView,
            IHardwareConfigurationManager hardwareConfigurationManager) {
        this.ownerViewModel = ownerViewModel;
        this.applicationContext = applicationContext;
        this.languageHelper = languageHelper;
        this.missionManager = missionManager;
        this.navigationService = navigationService;
        this.flightPlanService = flightPlanService;
        this.dialogHelper = dialogHelper;
        this.dialogService = dialogService;
        this.supportManager = supportManager;
        this.mapModel = mapModel;
        this.mapView = mapView;
        this.licenceManager = licenceManager;
        this.hardwareConfigurationManager = hardwareConfigurationManager;
        this.settingsManager = settingsManager;

        BooleanBinding importedBinding =
            PropertyPath.from(applicationContext.currentMissionProperty())
                .select(Mission::currentMatchingProperty)
                .selectReadOnlyObject(Matching::statusProperty)
                .isEqualTo(MatchingStatus.IMPORTED);

        menuModel.find(MainMenuModel.Project.EXIT).setCommandHandler(WindowHelper::closePrimaryStage);

        menuModel.find(MainMenuModel.Project.OPEN).setCommandHandler(this::openMission);
        menuModel
            .find(MainMenuModel.Project.CLOSE)
            .setCommandHandler(
                applicationContext::unloadCurrentMission, applicationContext.currentMissionProperty().isNotNull());

        menuModel
            .find(MainMenuModel.Project.RENAME)
            .setCommandHandler(
                () -> {
                    Dispatcher.postToUI(
                        () -> {
                            applicationContext.renameCurrentMission();
                        });
                },
                applicationContext.currentMissionProperty().isNotNull());

        menuModel
            .find(MainMenuModel.Project.SHOW)
            .setCommandHandler(
                () -> {
                    try {
                        Desktop.getDesktop().browse(applicationContext.getCurrentMission().getDirectory().toUri());
                    } catch (IOException e) {
                        Debug.getLog()
                            .log(
                                Level.WARNING,
                                "cant browse folder:" + applicationContext.getCurrentMission().getDirectory(),
                                e);
                        applicationContext.addToast(
                            Toast.of(ToastType.ALERT)
                                .setText(
                                    languageHelper.getString(
                                        "com.intel.missioncontrol.ui.menu.cant_browse_folder_exception"))
                                .setCloseable(true)
                                .create());
                    }
                },
                applicationContext.currentMissionProperty().isNotNull());

        menuModel
            .find(MainMenuModel.Dataset.OPEN)
            .setCommandHandler(
                () -> {
                    String title =
                        languageHelper.getString(
                            "com.intel.missioncontrol.ui.analysis.AnalysisView.FileChooser.dialogTitle");
                    Mission mission = applicationContext.currentMissionProperty().get();
                    Path matchingFolder = MissionConstants.getMatchingsFolder(mission.getDirectory()).toPath();
                    Path selectedFile =
                        dialogService.requestFileOpenDialog(ownerViewModel, title, matchingFolder, FileFilter.PTG);
                    if (selectedFile != null) {
                        try {
                            Matching matching = new Matching(selectedFile.toFile(), hardwareConfigurationManager);
                            mission.getMatchings().add(matching);
                            mission.setCurrentMatching(matching);
                            navigationService.navigateTo(WorkflowStep.DATA_PREVIEW);
                            navigationService.navigateTo(SidePanePage.VIEW_DATASET);
                            mapView.goToSectorAsync(matching.getSector(), matching.getMaxElev());
                        } catch (Exception e) {
                            Debug.getLog().log(Level.WARNING, "cant load dataset:" + matchingFolder, e);
                            applicationContext.addToast(
                                Toast.of(ToastType.ALERT)
                                    .setText(
                                        languageHelper.getString(
                                            "com.intel.missioncontrol.ui.analysis.AnalysisView.FileChooser.isNotMatchingFileAlert"))
                                    .setCloseable(true)
                                    .create());
                        }
                    }
                },
                applicationContext
                    .currentMissionProperty()
                    .isNotNull()
                    .and(applicationContext.currentMissionIsNoDemo()));

        menuModel
            .find(MainMenuModel.Dataset.CLOSE)
            .setCommandHandler(
                () -> {
                    ObservableList<Matching> matchings = applicationContext.getCurrentMission().getMatchings();
                    if (!matchings.isEmpty()) {
                        Mission mission = applicationContext.getCurrentMission();
                        Matching currentMatching = mission.getCurrentMatching();
                        if (!askToSaveChangesAndProceed(mission, currentMatching)) {
                            return;
                        }

                        matchings.remove(currentMatching);

                        if (!matchings.isEmpty()) {
                            mission.setCurrentMatching(matchings.get(0));
                        } else {
                            mission.setCurrentMatching(null);
                        }
                    }
                },
                importedBinding);

        menuModel
            .find(MainMenuModel.Dataset.SAVE)
            .setCommandHandler(
                () -> {
                    applicationContext.getCurrentMission().getCurrentMatching().saveResourceFile();
                },
                importedBinding.and(
                    PropertyPath.from(applicationContext.currentMissionProperty())
                        .select(Mission::currentMatchingProperty)
                        .selectReadOnlyBoolean(Matching::matchingLayerChangedProperty)));

        menuModel
            .find(MainMenuModel.FlightPlan.CLOSE)
            .setCommandHandler(
                () -> {
                    Mission mission = applicationContext.getCurrentMission();
                    FlightPlan flightPlan = mission.getCurrentFlightPlan();
                    if (!askToSaveChangesAndProceed(mission, flightPlan)) {
                        return;
                    }

                    mission.closeFlightPlan(flightPlan);
                    if (flightPlan == null) {
                        mission.setCurrentFlightPlan(null);
                    } else {
                        mission.setCurrentFlightPlan(mission.getFirstFlightPlan());
                    }
                },
                PropertyPath.from(applicationContext.currentMissionProperty())
                    .selectReadOnlyObject(Mission::currentFlightPlanProperty)
                    .isNotNull()
                    .and(applicationContext.currentMissionIsNoDemo()));

        menuModel
            .find(MainMenuModel.FlightPlan.SHOW)
            .setCommandHandler(
                () -> {
                    try {
                        Desktop.getDesktop()
                            .browse(
                                applicationContext
                                    .getCurrentMission()
                                    .getCurrentFlightPlan()
                                    .getLegacyFlightplan()
                                    .getResourceFile()
                                    .getParentFile()
                                    .toURI());
                    } catch (IOException e) {
                        Debug.getLog()
                            .log(
                                Level.WARNING,
                                "cant browse folder:"
                                    + applicationContext
                                        .getCurrentMission()
                                        .getCurrentFlightPlan()
                                        .getLegacyFlightplan()
                                        .getResourceFile()
                                        .getParentFile(),
                                e);
                        applicationContext.addToast(
                            Toast.of(ToastType.ALERT)
                                .setText(
                                    languageHelper.getString(
                                        "com.intel.missioncontrol.ui.menu.cant_browse_folder_exception"))
                                .setCloseable(true)
                                .create());
                    }
                },
                PropertyPath.from(applicationContext.currentMissionProperty())
                    .selectReadOnlyObject(Mission::currentFlightPlanProperty)
                    .isNotNull());

        menuModel
            .find(MainMenuModel.Dataset.SHOW)
            .setCommandHandler(
                () -> {
                    ObservableList<Matching> matchings = applicationContext.getCurrentMission().getMatchings();
                    if (matchings.isEmpty()) {
                        return;
                    }

                    Matching currentMatching = applicationContext.getCurrentMission().getCurrentMatching();

                    try {
                        Desktop.getDesktop().browse(currentMatching.getResourceFile().getParentFile().toURI());
                    } catch (IOException e) {
                        Debug.getLog()
                            .log(
                                Level.WARNING,
                                "cant browse folder:"
                                    + applicationContext
                                        .getCurrentMission()
                                        .getCurrentMatching()
                                        .getResourceFile()
                                        .getParentFile(),
                                e);
                        applicationContext.addToast(
                            Toast.of(ToastType.ALERT)
                                .setText(
                                    languageHelper.getString(
                                        "com.intel.missioncontrol.ui.menu.cant_browse_folder_exception"))
                                .setCloseable(true)
                                .create());
                    }
                },
                importedBinding);

        menuModel
            .find(MainMenuModel.FlightPlan.NEW)
            .setCommandHandler(
                () -> {
                    Mission mission = applicationContext.getCurrentMission();
                    mission.setCurrentFlightPlan(null);
                },
                applicationContext
                    .currentMissionProperty()
                    .isNotNull()
                    .and(applicationContext.currentMissionIsNoDemo()));

        menuModel
            .find(MainMenuModel.FlightPlan.RENAME)
            .setCommandHandler(
                this::renameFlightPlan,
                PropertyPath.from(applicationContext.currentMissionProperty())
                    .selectReadOnlyObject(Mission::currentFlightPlanProperty)
                    .isNotNull()
                    .and(applicationContext.currentMissionIsNoDemo()));

        menuModel
            .find(MainMenuModel.Help.QUICK_START_GUIDE)
            .setCommandHandler(() -> openManual(QUICK_START_GUIDE_FILE));

        menuModel.find(MainMenuModel.Help.DEMO_MISSION).setCommandHandler(() -> openDemoMission());

        menuModel
            .find(MainMenuModel.Help.ABOUT)
            .setCommandHandler(() -> dialogService.requestDialog(ownerViewModel, AboutDialogViewModel.class));

        menuModel
            .find(MainMenuModel.Debug.MENU_CAPTION)
            .visibleProperty()
            .bind(
                settingsManager
                    .getSection(GeneralSettings.class)
                    .operationLevelProperty()
                    .isEqualTo(OperationLevel.DEBUG));

        menuModel.find(MainMenuModel.Debug.PERF_MONITOR).setCommandHandler(this::openPerformanceMonitor);
        menuModel
            .find(MainMenuModel.Debug.WIREFRAME)
            .checkedProperty()
            .addListener(
                (observable, oldValue, newValue) -> {
                    mapModel.setShowWireframeInterior(newValue);
                });
    }

    // returns true if the operation should proceed (otherwise it it was canceled)
    private boolean askToSaveChangesAndProceed(Mission mission, ISaveable saveable) {
        if (saveable.hasUnsavedChanges()) {
            SaveChangesDialogViewModel viewModel =
                dialogService.requestDialogAndWait(
                    WindowHelper.getPrimaryViewModel(),
                    SaveChangesDialogViewModel.class,
                    () ->
                        new SaveChangesDialogViewModel.Payload(
                            Arrays.asList(saveable), mission, SaveChangesDialogViewModel.DialogTypes.close));
            boolean shouldProceed = viewModel.shouldProceedProperty().get();
            if (!shouldProceed) {
                return false;
            }

            boolean shouldSaveChanges = viewModel.shouldSaveChangesProperty().get();
            boolean wasSelected = viewModel.needsToSaveItem(saveable);
            if (shouldSaveChanges && wasSelected) {
                if (saveable instanceof FlightPlan) {
                    flightPlanService.saveFlightPlan(mission, ((FlightPlan)saveable));
                } else {
                    saveable.save();
                }

                missionManager.saveMission(mission);
                if (!viewModel.nameProperty().get().equals(mission.getName())) {
                    missionManager.renameMission(mission, viewModel.nameProperty().get());
                }
            }
        }

        return true;
    }

    private void openManual(String fileName) {
        try {
            final String manualFolder = System.getProperty(MANUAL_PATH_ARG, DEFAULT_MANUAL_FOLDER);
            File file = Paths.get(manualFolder, Locale.getDefault().getLanguage(), fileName).toFile();
            if (!file.exists()) {
                file = Paths.get(manualFolder, Locale.ENGLISH.getLanguage(), fileName).toFile();
            }

            if (!file.exists()) {
                throw new FileNotFoundException(file.getAbsolutePath());
            }

            FileHelper.openFile(file);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private boolean isFlightplanNameValid(String flightPlanName) {
        Mission currentMission = applicationContext.getCurrentMission();
        int duplicates =
            currentMission
                .flightPlansProperty()
                .filtered(flightPlan -> flightPlan.getName().equals(flightPlanName))
                .size();
        return duplicates == 0 && flightPlanName.trim().length() > 1;
    }

    private void renameFlightPlan() {
        Mission mission = applicationContext.getCurrentMission();
        // mission.closeFlightPlan(mission.getFirstFlightPlan());

        FlightPlan flightPlan = mission.getCurrentFlightPlan();
        String oldName = mission.getCurrentFlightPlan().getName();
        String newName =
            RenameDialog.requestNewMissionName(
                    languageHelper.getString(
                        "com.intel.missioncontrol.ui.SidePaneView.selector.flightplan.rename.title"),
                    languageHelper.getString(
                        "com.intel.missioncontrol.ui.SidePaneView.selector.flightplan.rename.name"),
                    oldName,
                    languageHelper,
                    this::isFlightplanNameValid)
                .orElse(oldName);

        this.flightPlanService.renameFlightPlan(mission, flightPlan, newName);
    }

    private void openMission() {
        if (applicationContext.currentMissionProperty().get() != null) {
            // if unloading is cancelled by user input, we also abort open mission
            if (!applicationContext.unloadCurrentMission()) {
                return;
            }
        }

        Path projectsDirectory = settingsManager.getSection(PathSettings.class).getProjectFolder();
        Path missionFolder = dialogService.requestDirectoryChooser(ownerViewModel, null, projectsDirectory);
        if (missionFolder == null) {
            return;
        }

        if (!missionManager.isMissionFolder(missionFolder.toFile())) {
            Debug.getLog()
                .log(
                    Level.INFO,
                    languageHelper.getString("com.intel.missioncontrol.ui.menu.no_mission_folder")
                        + " "
                        + missionFolder.toFile());
            applicationContext.addToast(
                Toast.of(ToastType.INFO)
                    .setText(languageHelper.getString("com.intel.missioncontrol.ui.menu.no_mission_folder"))
                    .create());
            return;
        }

        openMission(missionManager.openMission(missionFolder), false);
    }

    public static final String DEMO_SESSION_NAME = "DEMO";

    private void openDemoMission() {
        Path projectsDirectory = settingsManager.getSection(PathSettings.class).getProjectFolder();
        Path missionFolder = projectsDirectory.resolve(DEMO_SESSION_NAME);
        boolean alreadyCreated = Files.exists(missionFolder);
        if (alreadyCreated) {
            try {
                FileUtils.deleteDirectory(missionFolder.toFile());
            } catch (IOException e) {
                Debug.getLog().log(Level.SEVERE, "Could not delete Demo Session data on disk", e);
            }
        }

        try {
            if (licenceManager.isFalconEditionProperty().get()) {
                FileHelper.scanFilesJarAndWriteToDisk(
                    MFileFilter.allFilterNonSVN, "com/intel/missioncontrol/demoSessions/falcon/", missionFolder.toFile());
            } else {
                FileHelper.scanFilesJarAndWriteToDisk(
                    MFileFilter.allFilterNonSVN, "com/intel/missioncontrol/demoSessions/dji/", missionFolder.toFile());
            }

            Mission mission = missionManager.openMission(missionFolder);
            openMission(mission, false);
        } catch (Exception e) {
            Debug.getLog().log(Level.SEVERE, "Could not store Demo Session data on disk", e);
        }
    }

    public static boolean isDemo(IMissionInfo mission) {
        return Optional.ofNullable(mission)
            .map(IMissionInfo::getName)
            .filter(DEMO_SESSION_NAME::equalsIgnoreCase)
            .isPresent();
    }

    private void openMission(final Mission mission, boolean clone) {
        (clone ? applicationContext.loadClonedMissionAsync(mission) : applicationContext.loadMissionAsync(mission))
            .onSuccess(
                future -> {
                    SidePanePage newPage =
                        mission.flightPlansProperty().isEmpty()
                            ? (mission.matchingsProperty().isEmpty()
                                ? SidePanePage.START_PLANNING
                                : SidePanePage.VIEW_DATASET)
                            : SidePanePage.EDIT_FLIGHTPLAN;
                    if (mission.flightPlansProperty().isEmpty()
                            && !mission.matchingsProperty().isEmpty()
                            && (mission.matchingsProperty().get(0).getStatus() != MatchingStatus.NEW
                                || mission.getMatchings().size() > 1)) {
                        navigationService.navigateTo(SidePanePage.VIEW_DATASET);
                    } else {
                        navigationService.navigateTo(newPage);
                        missionManager.refreshRecentMissionInfos();
                        missionManager.refreshRecentMissionInfos();
                    }
                },
                Platform::runLater);
    }

    private void handleTicketDownload() {
        String ticketId =
            dialogService.requestInputDialogAndWait(
                ownerViewModel,
                languageHelper.getString(
                    "com.intel.missioncontrol.ui.menu.MainMenuCommandManager.downloadTicketDataTitle"),
                languageHelper.getString(
                    "com.intel.missioncontrol.ui.menu.MainMenuCommandManager.downloadTicketDataMessage"),
                false);

        if (ticketId != null) {
            handleTicketDownload(ticketId);
        }
    }

    private void handleTicketDownload(String ticketId) {
        dialogHelper.createProgressDialogForTicketDownload(ticketId, applicationContext);
    }

    private void handleOldSupportUpload() {
        supportManager.scanReportFolder();
    }

    private void openPerformanceMonitor() {
        dialogService.requestDialog(ownerViewModel, PerformanceMonitorViewModel.class, false);
    }

}
