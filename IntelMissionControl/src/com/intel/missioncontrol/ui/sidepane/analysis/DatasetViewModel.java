/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol.ui.sidepane.analysis;

import com.google.common.io.PatternFilenameFilter;
import com.google.inject.Inject;
import com.intel.missioncontrol.IApplicationContext;
import com.intel.missioncontrol.api.IExportService;
import com.intel.missioncontrol.beans.property.PropertyPath;
import com.intel.missioncontrol.beans.property.PropertyPathStore;
import com.intel.missioncontrol.concurrent.Dispatcher;
import com.intel.missioncontrol.hardware.IHardwareConfigurationManager;
import com.intel.missioncontrol.helper.Ensure;
import com.intel.missioncontrol.helper.Expect;
import com.intel.missioncontrol.helper.ILanguageHelper;
import com.intel.missioncontrol.map.IMapView;
import com.intel.missioncontrol.map.ISelectionManager;
import com.intel.missioncontrol.map.elevation.IElevationModel;
import com.intel.missioncontrol.mission.IMissionManager;
import com.intel.missioncontrol.mission.Matching;
import com.intel.missioncontrol.mission.MatchingStatus;
import com.intel.missioncontrol.mission.Mission;
import com.intel.missioncontrol.mission.MissionConstants;
import com.intel.missioncontrol.settings.GeneralSettings;
import com.intel.missioncontrol.settings.ISettingsManager;
import com.intel.missioncontrol.settings.OperationLevel;
import com.intel.missioncontrol.settings.PathSettings;
import com.intel.missioncontrol.ui.MainScope;
import com.intel.missioncontrol.ui.ViewModelBase;
import com.intel.missioncontrol.ui.commands.DelegateCommand;
import com.intel.missioncontrol.ui.commands.ICommand;
import com.intel.missioncontrol.ui.dialogs.IDialogService;
import com.intel.missioncontrol.ui.dialogs.warnings.UnresolvedWarningsDialogViewModel;
import com.intel.missioncontrol.ui.menu.MainMenuModel;
import com.intel.missioncontrol.ui.menu.MenuModel;
import com.intel.missioncontrol.ui.navigation.INavigationService;
import com.intel.missioncontrol.ui.navigation.SettingsPage;
import com.intel.missioncontrol.ui.navigation.SidePanePage;
import com.intel.missioncontrol.ui.navigation.WorkflowStep;
import com.intel.missioncontrol.ui.notifications.Toast;
import com.intel.missioncontrol.ui.notifications.ToastType;
import com.intel.missioncontrol.ui.validation.IValidationService;
import com.intel.missioncontrol.ui.validation.ValidationMessageCategory;
import com.intel.missioncontrol.utils.IBackgroundTaskManager;
import de.saxsys.mvvmfx.InjectScope;
import de.saxsys.mvvmfx.internal.viewloader.DependencyInjector;
import eu.mavinci.core.flightplan.CPhotoLogLine;
import eu.mavinci.core.licence.ILicenceManager;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.AMapLayerMatching;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.ExifInfos;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.MapLayerMatch;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.MapLayerMatching;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.PhotoCube;
import eu.mavinci.desktop.helper.FileFilter;
import eu.mavinci.desktop.helper.FileHelper;
import eu.mavinci.desktop.helper.gdal.MSpatialReference;
import eu.mavinci.desktop.main.debug.Debug;
import gov.nasa.worldwind.geom.LatLon;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.util.Duration;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetViewModel extends ViewModelBase {

    private static Logger LOGGER = LoggerFactory.getLogger(DatasetViewModel.class);
    private final PropertyPathStore propertyPathStore = new PropertyPathStore();

    private final AnalysisSettings settings;

    @InjectScope
    private MainScope mainScope;

    private ICommand showOnMapCommand;
    private final ICommand renameMissionCommand;
    private final ILanguageHelper languageHelper;
    private final IDialogService dialogService;
    private final IMissionManager missionManager;

    private final IApplicationContext applicationContext;
    private final IValidationService validationService;
    private final INavigationService navigationService;

    private final IBackgroundTaskManager taskManager;
    private final IExportService exportService;
    private final PathSettings pathSettings;
    private final GeneralSettings generalSettings;
    private final ObjectProperty<MSpatialReference> missionSrs = new SimpleObjectProperty<>();

    private final Desktop desktop;

    private final ObjectProperty<ExportTypes> lastExportType = new SimpleObjectProperty<>();
    private final StringProperty exportButtonText = new SimpleStringProperty();
    private ReadOnlyObjectProperty<MatchingStatus> matchingStatus;
    private final BooleanProperty hasImportantWarnings = new SimpleBooleanProperty();

    private Map<ExportTypes, Exporter> exporters = new EnumMap<>(ExportTypes.class);

    private BooleanBinding canExport;

    private final IHardwareConfigurationManager hardwareConfigurationManager;
    private final IMapView mapView;
    private final ReadOnlyObjectProperty<Matching> currentMatching;
    private final ISelectionManager selectionManager;
    private final ILicenceManager licenceManager;

    @Inject
    public DatasetViewModel(
            IMapView mapView,
            ILanguageHelper languageHelper,
            IDialogService dialogService,
            IMissionManager missionManager,
            IApplicationContext applicationContext,
            IValidationService validationService,
            INavigationService navigationService,
            ISettingsManager settingsManager,
            IBackgroundTaskManager taskManager,
            IExportService exportService,
            ISelectionManager selectionManager,
            IHardwareConfigurationManager hardwareConfigurationManager,
            ILicenceManager licenceManager) {
        this.mapView = mapView;
        this.taskManager = taskManager;
        this.exportService = exportService;
        this.selectionManager = selectionManager;
        this.settings = settingsManager.getSection(AnalysisSettings.class);
        this.pathSettings = settingsManager.getSection(PathSettings.class);
        this.generalSettings = settingsManager.getSection(GeneralSettings.class);

        missionSrs.bind(
            propertyPathStore.from(applicationContext.currentMissionProperty()).selectObject(Mission::srsProperty));

        this.hardwareConfigurationManager = hardwareConfigurationManager;
        this.languageHelper = languageHelper;
        this.dialogService = dialogService;
        this.missionManager = missionManager;
        this.applicationContext = applicationContext;
        this.validationService = validationService;
        this.navigationService = navigationService;
        this.licenceManager = licenceManager;
        this.hasImportantWarnings.bind(
            Bindings.createBooleanBinding(
                () ->
                    !validationService
                        .datasetValidationMessagesProperty()
                        .stream()
                        .filter((message) -> !message.getCategory().equals(ValidationMessageCategory.NOTICE))
                        .collect(Collectors.toList())
                        .isEmpty(),
                validationService.datasetValidationMessagesProperty()));

        currentMatching =
            PropertyPath.from(applicationContext.currentMissionProperty())
                .selectReadOnlyObject(Mission::currentMatchingProperty);

        lastExportTypeProperty()
            .addListener(
                (observable, oldValue, newValue) -> {
                    exportButtonText.set(
                        languageHelper.getString(
                            "com.intel.missioncontrol.ui.analysis.AnalysisView.export",
                            languageHelper.getString(newValue.key)));
                });

        lastExportTypeProperty().bindBidirectional(settings.datasetExportTypeProperty());
        desktop = Desktop.getDesktop();

        registerExporter(ExportTypes.CSV, exportService::isNotExportedAsCsv, this::handleCsvExport);
        registerExporter(ExportTypes.WRITE_EXIF, exportService::isWriteExifDataAllowed, this::handleWriteExif);
        registerExporter(
            ExportTypes.AGISOFT_PHOTOSCAN,
            exportService::isNotExportedToAgiSoftPhotoScan,
            this::handleAgiSoftPhotoScanExport);
        registerExporter(
            ExportTypes.AGISOFT_METASHAPE,
            exportService::isNotExportedToAgiSoftPhotoScan,
            this::handleAgiSoftPhotoScanExport);
        registerExporter(
            ExportTypes.CONTEXT_CAPTURE, exportService::isNotExportedToContextCapture, this::handleContextCapture);
        registerExporter(ExportTypes.PIX4D_DESKTOP, exportService::isNotExportedAsPix4d, this::handlePix4DesktopExport);

        renameMissionCommand = new DelegateCommand(applicationContext::renameCurrentMission);
    }

    public void initializeViewModel() {
        super.initializeViewModel();

        this.showOnMapCommand =
            new DelegateCommand(
                () -> {
                    Matching matching = currentMatching.get();
                    if (matching != null) {
                        mapView.goToSectorAsync(matching.getSector(), matching.getMaxElev());
                    }
                },
                currentMatching.isNotNull());

        matchingStatus = PropertyPath.from(currentMatching).selectReadOnlyObject(Matching::statusProperty);

        canExport = matchingStatus.isEqualTo(MatchingStatus.IMPORTED).and(validationService.canExportDatasetProperty());

        currentMatching.addListener((observable, oldValue, newValue) -> this.maybeZoomOnSelectionChange());
        navigationService
            .workflowStepProperty()
            .addListener((observable, oldValue, newValue) -> this.maybeZoomOnSelectionChange());

        MenuModel datasetMenuModel = mainScope.datasetMenuModelProperty().get();
        datasetMenuModel
            .find(DatasetMenuModel.MenuIds.OPEN)
            .setCommandHandler(this::openDataset, applicationContext.currentMissionIsNoDemo());

        MenuModel mainMenuModel = mainScope.mainMenuModelProperty().get();
        var newCommand = datasetMenuModel.find(DatasetMenuModel.MenuIds.NEW_DATASET).getCommand();
        mainMenuModel
            .find(MainMenuModel.Dataset.NEW)
            .setCommandHandler(newCommand::execute, newCommand.executableProperty());
        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_PHOTOSCAN)
            .setCommandHandler(
                () -> getExporter(ExportTypes.AGISOFT_PHOTOSCAN).export(null),
                agiSoftPhotoScanEnabledProperty().and(agiSoftIsMetashapeProperty().not()));
        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_METASHAPE)
            .setCommandHandler(
                () -> getExporter(ExportTypes.AGISOFT_METASHAPE).export(null),
                agiSoftPhotoScanEnabledProperty().and(agiSoftIsMetashapeProperty()));
        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_PHOTOSCAN_WITH_DEBUG)
            .setCommandHandler(
                () ->
                    getExporter(ExportTypes.AGISOFT_PHOTOSCAN)
                        .export(
                            new Event(
                                new EventType<>(
                                    EventType.ROOT, MainMenuModel.Dataset.EXPORT_PHOTOSCAN_WITH_DEBUG.name()))),
                agiSoftPhotoScanEnabledProperty());

        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_PHOTOSCAN)
            .visibleProperty()
            .bind(pathSettings.agiSoftIsPhotoscanProperty());
        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_METASHAPE)
            .visibleProperty()
            .bind(pathSettings.agiSoftIsMetashapeProperty());

        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_PHOTOSCAN_WITH_DEBUG)
            .visibleProperty()
            .bind(generalSettings.operationLevelProperty().isEqualTo(OperationLevel.DEBUG));

        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_CSV)
            .setCommandHandler(() -> getExporter(ExportTypes.CSV).export(null), canExport());
        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_EXIF)
            .setCommandHandler(() -> getExporter(ExportTypes.WRITE_EXIF).export(null), canExport());
        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_PIX4D)
            .setCommandHandler(
                () -> getExporter(ExportTypes.PIX4D_DESKTOP).export(null), pix4DDesktopExportEnabledProperty());
        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_CONTEXT_CAPTURE)
            .setCommandHandler(
                () -> getExporter(ExportTypes.CONTEXT_CAPTURE).export(null), contextCaptureExportEnabledProperty());
        mainMenuModel
            .find(MainMenuModel.Dataset.EXPORT_SETUP)
            .setCommandHandler(this::goToDefineAppPaths, new SimpleBooleanProperty(true));

        mainMenuModel
            .find(MainMenuModel.Dataset.IMPORT_EXIF)
            .setCommandHandler(
                this::importExif,
                applicationContext
                    .currentMissionProperty()
                    .isNotNull()
                    .and(applicationContext.currentMissionIsNoDemo()));

        mainMenuModel
            .find(MainMenuModel.Dataset.IMPORT_EXIF)
            .visibleProperty()
            .bind(
                generalSettings
                    .operationLevelProperty()
                    .isEqualTo(OperationLevel.DEBUG)
                    .or(licenceManager.isDJIEditionProperty()));
    }

    private void maybeZoomOnSelectionChange() {
        if (navigationService.getWorkflowStep() == WorkflowStep.DATA_PREVIEW && showOnMapCommand.isExecutable()) {
            showOnMapCommand.execute();
        }
    }

    public ReadOnlyObjectProperty<MenuModel> datasetMenuModelProperty() {
        return mainScope.datasetMenuModelProperty();
    }

    public ICommand getShowOnMapCommand() {
        return showOnMapCommand;
    }

    public ICommand getRenameMissionCommand() {
        return renameMissionCommand;
    }

    public void saveDataset() {
        currentMatchingProperty().get().saveResourceFile();
    }

    public void goToDefineAppPaths() {
        navigationService.navigateTo(SettingsPage.FILES_FOLDERS);
    }

    public void openDataset() {
        String title =
            languageHelper.getString("com.intel.missioncontrol.ui.analysis.AnalysisView.FileChooser.dialogTitle");
        Path selectedFile = dialogService.requestFileOpenDialog(this, title, getMatchingsFolder(), FileFilter.PTG);
        if (selectedFile != null) {
            try {
                Matching matching = new Matching(selectedFile.toFile(), hardwareConfigurationManager);
                Mission mission = applicationContext.getCurrentMission();
                mission.getMatchings().add(matching);
                mission.currentMatchingProperty().set(matching);
                navigationService.navigateTo(WorkflowStep.DATA_PREVIEW);
                navigationService.navigateTo(SidePanePage.VIEW_DATASET);
                mapView.goToSectorAsync(matching.getSector(), matching.getMaxElev());
            } catch (Exception e) {
                Debug.getLog().log(Level.WARNING, "cant load dataset:" + selectedFile, e);
                applicationContext.addToast(
                    Toast.of(ToastType.ALERT)
                        .setText(
                            languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisView.FileChooser.isNotMatchingFileAlert"))
                        .setCloseable(true)
                        .create());
            }
        }
    }

    private Path getMatchingsFolder() {
        return MissionConstants.getMatchingsFolder(applicationContext.getCurrentMission().getDirectory()).toPath();
    }

    public BooleanBinding canExport() {
        return canExport;
    }

    public BooleanBinding agiSoftPhotoScanEnabledProperty() {
        return canExport().and(pathSettings.agiSoftPhotoScanPathProperty().isNotNull());
    }

    public BooleanBinding agiSoftIsMetashapeProperty() {
        return canExport()
            .and(
                pathSettings
                    .agiSoftPhotoScanPathProperty()
                    .isNotNull()
                    .and((ObservableBooleanValue)pathSettings.agiSoftIsMetashapeProperty()));
    }

    public BooleanBinding contextCaptureExportEnabledProperty() {
        return canExport();
    }

    public BooleanBinding pix4DDesktopExportEnabledProperty() {
        return canExport();
    }

    public ObjectProperty<ExportTypes> lastExportTypeProperty() {
        return lastExportType;
    }

    public void handleWriteExif(Event event, Matching matching) {
        lastExportTypeProperty().set(ExportTypes.WRITE_EXIF);
        doWriteExifToImages(matching);
    }

    private void doWriteExifToImages(Matching matching) {
        if (matching != null) {
            final String progressMessagePattern =
                languageHelper.getString("com.intel.missioncontrol.ui.analysis.AnalysisView.writeExif.progress");
            IBackgroundTaskManager.BackgroundTask writeExifTask =
                new IBackgroundTaskManager.BackgroundTask(
                    languageHelper.getString("com.intel.missioncontrol.ui.analysis.AnalysisView.writeExif.task.name")) {
                    @Override
                    public @Nullable Toast getFailedToast(ILanguageHelper languageHelper) {
                        return Toast.of(ToastType.ALERT)
                            .setText(
                                languageHelper.getString(
                                    "com.intel.missioncontrol.ui.analysis.AnalysisView.writeExif.failed"))
                            .create();
                    }

                    @Override
                    public @Nullable Toast getSucceededToast(ILanguageHelper languageHelper) {
                        return toastTemplate(
                            languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisView.writeExif.finished"),
                            languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.open.browser"),
                            () -> showInExplorer(getMatchingImagesFolder(matching)));
                    }

                    @Override
                    protected Void call() throws Exception {
                        exportService.writeExifData(
                            (progressFirer) -> {
                                long handledImagesCount = progressFirer.getCurrentStage();
                                long imagesToHandleCount = progressFirer.getStagesCount();
                                updateMessage(
                                    String.format(progressMessagePattern, handledImagesCount, imagesToHandleCount));
                                updateProgress(handledImagesCount, imagesToHandleCount);
                            },
                            this::isCancelled,
                            matching);
                        return null;
                    }
                };

            taskManager.submitTask(writeExifTask);
        }
    }

    private Toast toastTemplate(String text, String actionText, Runnable action) {
        final int hintTtl = settings.getBackgroundTaskHintTtl();
        if (actionText != null && action != null) {
            return Toast.of(ToastType.INFO)
                .setText(text)
                .setTimeout(Duration.seconds(hintTtl))
                .setAction(actionText, action, Platform::runLater)
                .create();
        }

        return Toast.of(ToastType.INFO).setText(text).setTimeout(Duration.seconds(hintTtl)).create();
    }

    private void showInExplorer(File parentFile) {
        try {
            desktop.open(parentFile);
        } catch (IOException e) {
            LOGGER.error("Unable show in explorer " + parentFile.getAbsolutePath(), e);
        }
    }

    private File getMatchingImagesFolder(Matching matching) {
        File matchingFolder = matching.getResourceFile().getParentFile();
        File[] imagesDirs = matchingFolder.listFiles(new PatternFilenameFilter("images"));
        if (imagesDirs != null && imagesDirs.length > 0) {
            File result = imagesDirs[0];
            if (result.canRead()) {
                return result;
            }
        }

        return matchingFolder;
    }

    public ReadOnlyObjectProperty<Matching> currentMatchingProperty() {
        return currentMatching;
    }

    public StringProperty exportButtonTextProperty() {
        return exportButtonText;
    }

    public Exporter getExporter(ExportTypes exportType) throws IllegalStateException {
        Expect.notNull(exporters, "exporters");
        Exporter exp = exporters.get(exportType);
        Expect.notNull(exp, "exp");
        return exp;
    }

    interface ExportHandler {
        void handle(Event e, Matching matching);
    }

    private void registerExporter(
            ExportTypes exportTypes, Predicate<Matching> checkExportAllowed, ExportHandler exportHandler) {
        exporters.put(exportTypes, new Exporter(this, exportTypes, checkExportAllowed, exportHandler));
    }

    public Function<Matching, String> getTargetPathCommand(ExportTypes type) {
        return exportService.getTagretFilePath(type);
    }

    public static class Exporter {
        private DatasetViewModel viewModel;
        private final ExportTypes exportType;
        private Predicate<Matching> checkFileNotExists;
        private final ExportHandler exportHandler;

        public Exporter(
                DatasetViewModel viewModel,
                ExportTypes exportType,
                Predicate<Matching> checkFileNotExists,
                ExportHandler exportHandler) {
            this.viewModel = viewModel;
            this.exportType = exportType;
            this.checkFileNotExists = checkFileNotExists;
            this.exportHandler = exportHandler;
        }

        public Predicate<Matching> alsoAvailableIf(Predicate<Matching> availabilityPredicate) {
            this.checkFileNotExists = checkFileNotExists.or(availabilityPredicate);
            return checkFileNotExists;
        }

        public void export(Event event) {
            if (viewModel.hasImportantWarnings.get()) {
                UnresolvedWarningsDialogViewModel vm =
                    viewModel.dialogService.requestDialogAndWait(viewModel, UnresolvedWarningsDialogViewModel.class);
                if (!vm.getDialogResult()) {
                    return;
                }
            }

            viewModel.lastExportTypeProperty().set(exportType);
            if (checkFileNotExists.test(viewModel.currentMatchingProperty().get())) {
                exportHandler.handle(event, viewModel.currentMatchingProperty().get());
            }
        }
    }

    public void handleCsvExport(Event event, Matching matching) {
        IBackgroundTaskManager.BackgroundTask exportToCsvTask =
            new IBackgroundTaskManager.BackgroundTask(
                languageHelper.getString("com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.csv.title")) {
                @Override
                public @Nullable Toast getSucceededToast(ILanguageHelper languageHelper) {
                    return toastTemplate(
                        languageHelper.getString(
                            "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.generic.complete"),
                        languageHelper.getString(
                            "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.open.browser"),
                        () -> showInExplorer(matching.getResourceFile().getParentFile()));
                }

                @Override
                protected Void call() throws Exception {
                    exportService.exportAsCsv(currentMatching.get(), missionSrs.get());
                    return null;
                }
            };

        taskManager.submitTask(exportToCsvTask);
    }

    public void handleAgiSoftPhotoScanExport(Event event, Matching matching) {
        IBackgroundTaskManager.BackgroundTask exportToAgiSoftTask =
            new IBackgroundTaskManager.BackgroundTask(
                languageHelper.getString(
                    pathSettings.agiSoftIsMetashape()
                        ? "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.agiSoftMetashape.title"
                        : "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.agiSoftPhotoScan.title")) {
                @Override
                public @Nullable Toast getSucceededToast(ILanguageHelper languageHelper) {
                    return toastTemplate(
                        languageHelper.getString(
                            "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.generic.complete"),
                        languageHelper.getString(
                            "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.open.application"),
                        () -> {
                            try {
                                exportService.openInAgiSoft(matching);
                            } catch (IOException e) {
                                LOGGER.warn("opening agisoft failed", e);
                                applicationContext.addToast(
                                    Toast.createDefaultFailed(
                                        languageHelper,
                                        languageHelper.getString(
                                            "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.generic.complete")));
                            }
                        });
                }

                @Override
                protected Void call() throws Exception {
                    if (event != null
                            && event.getEventType()
                                .getName()
                                .equals(MainMenuModel.Dataset.EXPORT_PHOTOSCAN_WITH_DEBUG.name())) {
                        exportService.writeDebugAgisoftScript();
                    }

                    exportService.exportToAgiSoftPhotoScan(currentMatching.get(), missionSrs.get());

                    return null;
                }
            };

        taskManager.submitTask(exportToAgiSoftTask);
    }

    public void handleContextCapture(Event event, Matching matching) {
        IBackgroundTaskManager.BackgroundTask exportToContextCapture =
            new IBackgroundTaskManager.BackgroundTask(
                languageHelper.getString(
                    "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.contextCapture.title")) {
                @Override
                public @Nullable Toast getSucceededToast(ILanguageHelper languageHelper) {
                    return toastTemplate(
                        languageHelper.getString(
                            "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.generic.complete"),
                        pathSettings.contextCapturePathProperty().get() != null
                            ? languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.open.application")
                            : languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.open.explorer"),
                        () -> {
                            exportService.openInContextCapture(matching);
                        });
                }

                @Override
                protected Void call() throws Exception {
                    exportService.exportToContextCapture(currentMatching.get(), missionSrs.get());
                    return null;
                }
            };

        taskManager.submitTask(exportToContextCapture);
    }

    public void handlePix4DesktopExport(Event event, Matching matching) {
        IBackgroundTaskManager.BackgroundTask exportToPix4dTask =
            new IBackgroundTaskManager.BackgroundTask(
                languageHelper.getString("com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.pix4d.title")) {
                @Override
                public @Nullable Toast getSucceededToast(ILanguageHelper languageHelper) {
                    return toastTemplate(
                        languageHelper.getString(
                            "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.generic.complete"),
                        pathSettings.pix4DPathProperty().get() != null
                            ? languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.open.application")
                            : languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.export.open.explorer"),
                        () -> {
                            exportService.openInPix4d(matching);
                        });
                }

                @Override
                protected Void call() throws Exception {
                    exportService.exportAsPix4d(currentMatching.get(), missionSrs.get());
                    return null;
                }
            };

        taskManager.submitTask(exportToPix4dTask);
    }

    public void importExif() {
        final Path[] files =
            dialogService.requestMultiFileOpenDialog(
                this,
                languageHelper.getString("com.intel.missioncontrol.ui.analysis.AnalysisView.browseImages.title"),
                null,
                FileFilter.JPEG);
        if (files == null || files.length == 0 || (files.length == 1 && files[0] == null)) {
            return;
        }

        IBackgroundTaskManager.BackgroundTask importExifTask =
            new IBackgroundTaskManager.BackgroundTask(
                languageHelper.getString("com.intel.missioncontrol.ui.analysis.AnalysisViewModel.import.exif.title")) {
                private Mission mission;

                @Override
                public @Nullable Toast getSucceededToast(ILanguageHelper languageHelper) {
                    Toast.ToastBuilder toastBuilder =
                        Toast.of(ToastType.INFO)
                            .setText(
                                languageHelper.getString(
                                    "com.intel.missioncontrol.ui.analysis.AnalysisCreateView.transferCompleteMessage"));

                    // if mission was closed in the meantime, dont show the action
                    if (applicationContext.getCurrentMission() == mission) {
                        toastBuilder =
                            toastBuilder.setAction(
                                languageHelper.getString(
                                    "com.intel.missioncontrol.ui.analysis.AnalysisCreateView.transferActionLinkMessage"),
                                false,
                                true,
                                () -> {
                                    Ensure.notNull(matching, "matching");
                                    selectionManager.setSelection(matching.getLegacyMatching());
                                    if (!mission.getMatchings().contains(matching)) {
                                        mission.getMatchings().add(matching);
                                    }

                                    mission.setCurrentMatching(matching);

                                    navigationService.navigateTo(WorkflowStep.DATA_PREVIEW);
                                    navigationService.navigateTo(SidePanePage.VIEW_DATASET);
                                    mapView.goToSectorAsync(matching.getSector(), matching.getMaxElev());
                                },
                                Platform::runLater);
                    }

                    return toastBuilder.create();
                }

                Matching matching;

                @Override
                protected Void call() throws Exception {
                    String matchingName = "import_" + System.currentTimeMillis();
                    mission = applicationContext.getCurrentMission();
                    File matchingsFolder = MissionConstants.getMatchingsFolder(mission.getDirectory());
                    File baseFolder = new File(matchingsFolder, matchingName);
                    final File save = new File(baseFolder, AMapLayerMatching.DEFAULT_FILENAME);

                    Matching matching = new Matching(save, hardwareConfigurationManager);
                    MapLayerMatching legacyMatching = (MapLayerMatching)matching.getLegacyMatching();
                    int i = 0;
                    legacyMatching.getPicsLayer().setMute(true);
                    String lastModel = null;
                    String nextModel = null;
                    boolean modelMismatch = false;
                    double groundAltSum = 0;
                    int imgCount = 0;

                    ArrayList<CPhotoLogLine> plgs = new ArrayList<>();
                    ArrayList<PhotoCube> photos = new ArrayList<>();
                    IElevationModel elevationModel =
                        DependencyInjector.getInstance().getInstanceOf(IElevationModel.class);
                    for (Path file : files) {
                        updateMessage(
                            languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.import.exif.progress",
                                files.length,
                                i / (double)(2 * files.length) * 100));
                        updateProgress(i, 2 * files.length);
                        i++;
                        try {
                            File fileTarget = new File(legacyMatching.getImagesFolder(), file.getFileName().toString());
                            FileHelper.copyFile(file.toFile(), fileTarget);
                            PhotoCube photoCube = new PhotoCube(fileTarget);
                            ExifInfos exifInfos = photoCube.photoFiles[0].getExif();
                            nextModel = exifInfos.model;
                            if (lastModel == null) {
                                lastModel = nextModel;
                            } else if (!lastModel.equals(nextModel)) {
                                modelMismatch = true;
                            }

                            CPhotoLogLine plg = new CPhotoLogLine(exifInfos);
                            groundAltSum +=
                                elevationModel.getElevationAsGoodAsPossible(LatLon.fromDegrees(plg.lat, plg.lon));
                            imgCount++;
                            plgs.add(plg);
                            photos.add(photoCube);
                        } catch (Exception e) {
                            LOGGER.warn("cant import image: " + file, e);
                        }
                    }

                    if (imgCount > 0) {
                        groundAltSum /= imgCount;
                    } else {
                        throw new Exception("Can't import images, images maybe without positions information");
                    }

                    i = 0;
                    int groundAltAvgCm = (int)Math.round(groundAltSum * 100);
                    for (Path file : files) {
                        updateMessage(
                            languageHelper.getString(
                                "com.intel.missioncontrol.ui.analysis.AnalysisViewModel.import.exif.progress",
                                files.length,
                                (i + files.length) / (double)(2 * files.length) * 100));
                        updateProgress(i + files.length, 2 * files.length);
                        try {
                            CPhotoLogLine plg = plgs.get(i);
                            PhotoCube photoCube = photos.get(i);

                            if (plg.gps_altitude_cm == plg.alt) {
                                plg.gps_altitude_cm += groundAltAvgCm; // DJI gps wrong
                            } else {
                                plg.alt -= groundAltAvgCm;
                            }

                            i++;

                            MapLayerMatch match = new MapLayerMatch(photoCube, plg, legacyMatching);
                            photoCube.setMatch(match);
                            legacyMatching.getPicsLayer().addMapLayer(match);
                            match.generatePreview();
                        } catch (Exception e) {
                            LOGGER.warn("cant import image: " + file, e);
                        }
                    }

                    if (modelMismatch) {
                        throw new Exception(
                            "Can't import data from different cameras at once: " + lastModel + " != " + nextModel);
                    }

                    legacyMatching.getCoverage().updateCameraCorners();
                    legacyMatching.getPicsLayer().setMute(false);
                    legacyMatching.saveResourceFile();
                    this.matching =
                        matching; // if user clicks fast, postToUI not yet ready, hw can be wrong or matching empty
                    Dispatcher.postToUI(
                        () -> {
                            matching.detectBestHwConfiguration();
                            legacyMatching.guessGoodFilters();
                            matching.saveResourceFile();
                            Debug.getLog()
                                .log(
                                    Level.INFO,
                                    "Saved HW for this dataset: "
                                        + matching.getHardwareConfiguration().getPlatformDescription().getId()
                                        + " "
                                        + matching.getHardwareConfiguration().getPrimaryPayload());
                            this.matching = matching;
                            // open dataset, should be checked if mission still open and datasets active
                            if (!mission.getMatchings().contains(matching)) {
                                mission.getMatchings().add(matching);
                            }

                            mission.setCurrentMatching(matching);
                            missionManager.makeDefaultScreenshot(mission);
                            if (applicationContext.getCurrentMission() == mission) {
                                navigationService.navigateTo(WorkflowStep.DATA_PREVIEW);
                                navigationService.navigateTo(SidePanePage.VIEW_DATASET);
                                mapView.goToSectorAsync(matching.getSector(), matching.getMaxElev());
                            }
                        });
                    return null;
                }
            };

        taskManager.submitTask(importExifTask);
    }
}
