/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol.ui.sidepane.planning;

import static com.intel.missioncontrol.ui.scope.planning.PlanningScope.EVENT_ON_FLIGHT_PLAN_SAVE;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.inject.Inject;
import com.intel.missioncontrol.IApplicationContext;
import com.intel.missioncontrol.api.workflow.AoiWorkflowEvent;
import com.intel.missioncontrol.api.workflow.AoiWorkflowHints;
import com.intel.missioncontrol.beans.property.PropertyPath;
import com.intel.missioncontrol.beans.property.PropertyPathStore;
import com.intel.missioncontrol.concurrent.Dispatcher;
import com.intel.missioncontrol.concurrent.FluentFuture;
import com.intel.missioncontrol.geometry.AreaOfInterest;
import com.intel.missioncontrol.hardware.IHardwareConfiguration;
import com.intel.missioncontrol.hardware.IPlatformDescription;
import com.intel.missioncontrol.map.IMapController;
import com.intel.missioncontrol.map.IMapModel;
import com.intel.missioncontrol.map.IMapView;
import com.intel.missioncontrol.map.ISelectionManager;
import com.intel.missioncontrol.map.InputMode;
import com.intel.missioncontrol.mission.FlightPlan;
import com.intel.missioncontrol.mission.Mission;
import com.intel.missioncontrol.settings.GeneralSettings;
import com.intel.missioncontrol.settings.ISettingsManager;
import com.intel.missioncontrol.settings.OperationLevel;
import com.intel.missioncontrol.ui.MainScope;
import com.intel.missioncontrol.ui.ViewModelBase;
import com.intel.missioncontrol.ui.commands.CompositeCommand;
import com.intel.missioncontrol.ui.commands.DelegateCommand;
import com.intel.missioncontrol.ui.commands.ICommand;
import com.intel.missioncontrol.ui.commands.IParameterizedCommand;
import com.intel.missioncontrol.ui.commands.ParameterizedDelegateCommand;
import com.intel.missioncontrol.ui.dialogs.IDialogService;
import com.intel.missioncontrol.ui.menu.MainMenuModel;
import com.intel.missioncontrol.ui.menu.MenuModel;
import com.intel.missioncontrol.ui.navigation.INavigationService;
import com.intel.missioncontrol.ui.navigation.SidePanePage;
import com.intel.missioncontrol.ui.navigation.WorkflowStep;
import com.intel.missioncontrol.ui.scope.planning.PlanningScope;
import com.intel.missioncontrol.ui.sidepane.planning.aoi.AoiAdvancedParametersViewModel;
import de.saxsys.mvvmfx.InjectScope;
import eu.mavinci.core.flightplan.IFlightplanRelatedObject;
import eu.mavinci.core.flightplan.PlanType;
import eu.mavinci.flightplan.Flightplan;
import eu.mavinci.flightplan.PicArea;
import java.util.Optional;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

public class EditFlightplanViewModel extends ViewModelBase {

    private final ReadOnlyStringProperty missionName;
    private final BooleanProperty toolsAvailableDebug = new SimpleBooleanProperty();
    private final BooleanProperty allAoiInViewMode = new SimpleBooleanProperty(true);
    private final BooleanProperty canShowAdvancedDialog = new SimpleBooleanProperty(true);
    private final BooleanProperty editState = new SimpleBooleanProperty();
    private final ObjectProperty<AreaOfInterest> selectedAoi = new SimpleObjectProperty<>();
    private final PropertyPathStore propertyPathStore = new PropertyPathStore();
    private final IApplicationContext applicationContext;
    private final INavigationService navigationService;
    private final GeneralSettings generalSettings;
    private final ICommand showOnMapCommand;
    private final ICommand renameMissionCommand;
    private final IParameterizedCommand<AreaOfInterest> showAdvancedDialogCommand;
    private final IMapModel mapModel;
    private final IMapView mapView;
    private final IMapController mapController;
    private final ISelectionManager selectionManager;
    private final BooleanProperty showFlightPlanTemplateFooter = new SimpleBooleanProperty();

    @InjectScope
    private MainScope mainScope;

    @InjectScope
    private PlanningScope planningScope;

    private final AoiWorkflowHints hintsBus;

    private ICommand saveFlightplanCommand;
    private ICommand saveFlightplanAndProceedCommand;
    private ICommand exportFlightplanCommand;

    private BooleanBinding isEmergencyActionsVisibleBinding;
    private BooleanBinding isWarningsVisibleBinding;

    @Inject
    public EditFlightplanViewModel(
            ISettingsManager settingsManager,
            IApplicationContext applicationContext,
            INavigationService navigationService,
            IDialogService dialogService,
            AoiWorkflowHints hintsBus,
            ISelectionManager selectionManager,
            IMapModel mapModel,
            IMapView mapView,
            IMapController mapController) {
        this.hintsBus = hintsBus;
        this.mapModel = mapModel;
        this.mapView = mapView;
        this.mapController = mapController;
        this.selectionManager = selectionManager;
        this.applicationContext = applicationContext;
        this.navigationService = navigationService;
        this.generalSettings = settingsManager.getSection(GeneralSettings.class);

        this.missionName =
            PropertyPath.from(applicationContext.currentMissionProperty()).selectReadOnlyString(Mission::nameProperty);

        this.showOnMapCommand =
            new DelegateCommand(
                () -> {
                    FlightPlan fp = getCurrentFlightplan();
                    if (fp != null) {
                        mapView.goToSectorAsync(fp.getSector(), fp.getMaxElev());
                    }
                });

        renameMissionCommand = new DelegateCommand(applicationContext::renameCurrentMission);

        showAdvancedDialogCommand =
            new ParameterizedDelegateCommand<>(
                payload -> {
                    canShowAdvancedDialog.set(false);
                    final FluentFuture<AoiAdvancedParametersViewModel> dialog =
                        dialogService.requestDialog(this, AoiAdvancedParametersViewModel.class, () -> payload, false);
                    dialog.onDone(
                        (aoivm) -> {
                            canShowAdvancedDialog.set(true);
                        },
                        Platform::runLater);
                    dialog.onSuccess(
                        (aoivm) -> {
                            canShowAdvancedDialog.set(true);
                        },
                        Platform::runLater);
                    dialog.onFailure(
                        (aoivm) -> {
                            canShowAdvancedDialog.set(true);
                        });
                    Futures.addCallback(
                        dialog,
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(AoiAdvancedParametersViewModel aoiAdvancedParametersViewModel) {
                                canShowAdvancedDialog.set(true);
                            }

                            @Override
                            public void onFailure(Throwable throwable) {
                                canShowAdvancedDialog.set(true);
                            }

                        });
                },
                canShowAdvancedDialog);

        showFlightPlanTemplateFooter.bind(
            propertyPathStore
                .from(applicationContext.currentMissionProperty())
                .select(Mission::currentFlightPlanProperty)
                .selectReadOnlyBoolean(FlightPlan::isTemplateProperty));
    }

    public ReadOnlyBooleanProperty showFlightPlanTemplateFooterProperty() {
        return showFlightPlanTemplateFooter;
    }

    public BooleanProperty canShowAdvancedDialogProperty() {
        return canShowAdvancedDialog;
    }

    public ReadOnlyStringProperty missionNameProperty() {
        return missionName;
    }

    public ReadOnlyObjectProperty<Mission> currentMissionProperty() {
        return applicationContext.currentMissionProperty();
    }

    public BooleanBinding chooseAoiModeBinding() {
        return navigationService.sidePanePageProperty().isEqualTo(SidePanePage.CHOOSE_AOI);
    }

    public IParameterizedCommand<AreaOfInterest> getShowAdvancedDialogCommand() {
        return showAdvancedDialogCommand;
    }

    @Override
    protected void initializeViewModel() {
        super.initializeViewModel();

        planningScope.subscribe(EVENT_ON_FLIGHT_PLAN_SAVE, (message, observer) -> publish(EVENT_ON_FLIGHT_PLAN_SAVE));

        mapController
            .mouseModeProperty()
            .addListener(
                (observable, oldValue, newValue) -> {
                    AreaOfInterest aoi = selectedAoi.get();
                    if (aoi != null) {
                        if (newValue == InputMode.ADD_POINTS) {
                            Dispatcher.dispatchToUI(() -> hintsBus.reportEvent(new AoiWorkflowEvent(aoi)));
                        }

                        if (oldValue == InputMode.ADD_POINTS) {
                            Platform
                                .runLater( // if we execute this immediately, the corner count check isnt updated jet
                                    () -> {
                                        if (!aoi.hasEnoughCornersBinding().get()) {
                                            // delete API, not sufficiently added corners to it
                                            createRemoveAoiCommand(aoi).execute();
                                        } else {
                                            aoi.isInitialAddingProperty().set(false);
                                        }
                                    });
                        }
                    }
                },
                Dispatcher::dispatchToUI);

        selectionManager
            .currentSelectionProperty()
            .addListener((observable, oldValue, newValue) -> switchMode(newValue), Dispatcher::dispatchToUI);

        selectedAoiProperty()
            .addListener(
                (observable, oldValue, newValue) -> {
                    Platform.runLater( // if we execute this immediately, the corner count check isnt updated jet
                        () -> {
                            if (oldValue != null && !oldValue.hasEnoughCornersBinding().get()) {
                                // delete API, not sufficiently added corners to it
                                createRemoveAoiCommand(oldValue).execute();
                            }
                        });
                });

        toolsAvailableDebug.bind(generalSettings.operationLevelProperty().isEqualTo(OperationLevel.DEBUG));

        MenuModel mainMenuModel = mainScope.mainMenuModelProperty().get();
        exportFlightplanCommand = mainMenuModel.find(MainMenuModel.FlightPlan.EXPORT).getCommand();
        saveFlightplanCommand = mainMenuModel.find(MainMenuModel.FlightPlan.SAVE).getCommand();

        saveFlightplanAndProceedCommand =
            new CompositeCommand(
                saveFlightplanCommand,
                new DelegateCommand(
                    () -> navigationService.navigateTo(WorkflowStep.FLIGHT),
                    toolsAvailableDebug.and(applicationContext.currentMissionIsNoDemo())));

        propertyPathStore
            .from(applicationContext.currentMissionProperty())
            .selectReadOnlyObject(Mission::currentFlightPlanProperty)
            .addListener((observable, oldValue, newValue) -> selectFlightPlan(newValue));

        navigationService
            .workflowStepProperty()
            .addListener((observable, oldValue, newValue) -> this.maybeZoomOnSelectionChange());

        BooleanBinding isFixedWingsBinding =
            Bindings.createBooleanBinding(
                () ->
                    Optional.ofNullable(
                            planningScope.selectedHardwareConfigurationProperty().get().getPlatformDescription())
                        .map(IPlatformDescription::isInFixedWingEditionMode)
                        .orElse(true),
                planningScope.selectedHardwareConfigurationProperty());
        isEmergencyActionsVisibleBinding =
            isFixedWingsBinding.or(generalSettings.operationLevelProperty().isEqualTo(OperationLevel.DEBUG));

        isWarningsVisibleBinding =
            PropertyPath.from(currentFlightplanProperty())
                .selectReadOnlyBoolean(FlightPlan::allAoisSizeValidProperty)
                .and(
                    PropertyPath.from(currentFlightplanProperty())
                        .selectReadOnlyBoolean(FlightPlan::isSimulatedTimeValidProperty))
                .not();
    }

    public BooleanProperty editStateProperty() {
        return editState;
    }

    public ICommand getShowOnMapCommand() {
        return showOnMapCommand;
    }

    public ICommand getRenameMissionCommand() {
        return renameMissionCommand;
    }

    public MainScope getScope() {
        return mainScope;
    }

    public PlanningScope getPlanningScope() {
        return planningScope;
    }

    public MenuModel getFlightPlanMenuModel() {
        return mainScope.flightPlanMenuModelProperty().get();
    }

    public ObjectProperty<IHardwareConfiguration> hardwareConfigurationProperty() {
        return planningScope.selectedHardwareConfigurationProperty();
    }

    public void chooseAreaOfInterest(PlanType aoiId) {
        planningScope.generateDefaultName(aoiId);
        navigationService.navigateTo(SidePanePage.EDIT_FLIGHTPLAN);
        mapModel.addAreaOfInterest(applicationContext.getCurrentMission(), aoiId);
    }

    public void changeAoiPosition(int sourceId, int destId) {
        planningScope.changeAoiPosition(sourceId, destId);
    }

    public ICommand createRemoveAoiCommand(AreaOfInterest areaOfInterest) {
        return new DelegateCommand(
            () -> {
                areaOfInterest.getPicArea().getParent().removeFromFlightplanContainer(areaOfInterest.getPicArea());
                applicationContext
                    .getCurrentMission()
                    .currentFlightPlanProperty()
                    .get()
                    .areasOfInterestProperty()
                    .remove(areaOfInterest);
                if (selectedAoiProperty().get() == areaOfInterest) {
                    selectedAoiProperty().set(null);
                    editState.set(false);
                }

                if (applicationContext
                                .getCurrentMission()
                                .currentFlightPlanProperty()
                                .get()
                                .getAreasOfInterest()
                                .getSize()
                            == 0
                        && !applicationContext.getCurrentMission().currentFlightPlanProperty().get().isTemplate()) {
                    navigationService.navigateTo(SidePanePage.CHOOSE_AOI);
                }
            });
    }

    public ICommand createDoneAoiCommand(AreaOfInterest areaOfInterest) {
        return new DelegateCommand(
            () -> {
                areaOfInterest.isInitialAddingProperty().setValue(false);
            },
            areaOfInterest.hasEnoughCornersBinding());
    }

    public int indexOfAoi(AreaOfInterest aoi) {
        return planningScope.indexOfAoi(aoi);
    }

    public ReadOnlyBooleanProperty allAoisInViewModeProperty() {
        return allAoiInViewMode;
    }

    public FlightPlan getCurrentFlightplan() {
        return planningScope.getCurrentFlightplan();
    }

    public ReadOnlyObjectProperty<FlightPlan> currentFlightplanProperty() {
        return planningScope.currentFlightplanProperty();
    }

    public BooleanBinding isEmergencyActionsVisibleBinding() {
        return isEmergencyActionsVisibleBinding;
    }

    public BooleanBinding isWarningsVisibleBinding() {
        return isWarningsVisibleBinding;
    }

    public ICommand getExportFlightplanCommand() {
        return exportFlightplanCommand;
    }

    public ICommand getSaveFlightplanCommand() {
        return saveFlightplanCommand;
    }

    public ICommand getSaveFlightplanAndProceedCommand() {
        return saveFlightplanAndProceedCommand;
    }

    private void maybeZoomOnSelectionChange() {
        if (navigationService.getWorkflowStep() == WorkflowStep.PLANNING && showOnMapCommand.isExecutable()) {
            showOnMapCommand.execute();
        }
    }

    private void selectFlightPlan(FlightPlan flightPlan) {
        selectedAoi.set(null);
        if (flightPlan == null) {
            navigationService.navigateTo(SidePanePage.START_PLANNING);
            return;
        }

        focusFlightPlanOnMap(flightPlan);
        if (flightPlan.areasOfInterestProperty().isEmpty() && !flightPlan.isTemplate()) {
            navigationService.navigateTo(SidePanePage.CHOOSE_AOI);
        } else {
            Optional.ofNullable(navigationService.getWorkflowStep())
                .filter(wfs -> wfs != WorkflowStep.FLIGHT)
                .ifPresent(wfs -> navigationService.navigateTo(SidePanePage.EDIT_FLIGHTPLAN));
        }
    }

    private void focusFlightPlanOnMap(FlightPlan flightPlan) {
        if (flightPlan.getCenter() != null) {
            mapView.goToSectorAsync(flightPlan.getSector(), flightPlan.getMaxElev());
        }
    }

    public IPlatformDescription getPlatformDescription() {
        return planningScope.getCurrentFlightplan() != null
            ? planningScope
                .getCurrentFlightplan()
                .getLegacyFlightplan()
                .getHardwareConfiguration()
                .getPlatformDescription()
            : null;
    }

    public ObjectProperty<AreaOfInterest> selectedAoiProperty() {
        return selectedAoi;
    }

    public ReadOnlyBooleanProperty toolsAvailableDebugProperty() {
        return toolsAvailableDebug;
    }

    ////////////////// All the AOI editing mode code

    private void switchMode(Object selection) {
        if (selection == null) {
            exitEditingModeFromSelectionManager();
        } else {
            if (selection instanceof PicArea) {
                enterEditingModeFromSelectionManager();
            } else {
                AreaOfInterest aoi = findAoiBySelection(selection);
                // if a takeoff for example was selected
                if (aoi == null) {
                    exitEditingModeFromSelectionManager();
                }
            }
        }
    }

    private void enterEditingModeFromSelectionManager() {
        AreaOfInterest areaOfInterest = findAoiBySelection(selectionManager.getSelection());
        // if already in edit mode and not switching between editing different aois - return
        if (editState.get() && selectedAoi.get().equals(areaOfInterest)) {
            return;
        }

        editState.set(true);
        selectedAoi.set(areaOfInterest);
    }

    private void exitEditingModeFromSelectionManager() {
        // already not in the edit mode
        if (!editState.get()) {
            return;
        }

        editState.set(false);
        selectedAoi.set(null);
        selectionManager.setSelection(null);
    }

    public void enterEditingMode(AreaOfInterest areaOfInterest) {
        // if already in edit mode and not switching between editing different aois - return
        if (editState.get() && selectedAoi.get().equals(areaOfInterest)) {
            return;
        }

        editState.set(true);
        if (areaOfInterest != null) {
            selectedAoi.set(areaOfInterest);
            // selection manager does not know yet what is selected
            selectionManager.setSelection(areaOfInterest.getPicArea());
        }
    }

    public void exitEditingMode() {
        // already not in the edit mode
        if (!editState.get()) {
            return;
        }

        editState.set(false);
        selectedAoi.set(null);
        selectionManager.setSelection(null);

        // only in case if we are in the adding mode and clicking Done button
        mapController.tryCancelMouseModes(InputMode.ADD_POINTS);
    }

    private AreaOfInterest findAoiBySelection(Object selectedObject) {
        Mission mission = applicationContext.getCurrentMission();
        if (mission == null) {
            return null;
        }

        if (selectedObject != null) {
            if (selectedObject instanceof IFlightplanRelatedObject) {
                IFlightplanRelatedObject fpRel = (IFlightplanRelatedObject)selectedObject;
                if (fpRel.getFlightplan() == null) {
                    // in case this are Match Filter polygones or its phantom points
                    return null;
                }

                FlightPlan flightPlan = mission.getFlightPlanForLegacy((Flightplan)fpRel.getFlightplan());
                applicationContext.getCurrentMission().setCurrentFlightPlan(flightPlan);

                while (fpRel != null) {
                    if (fpRel instanceof PicArea) {
                        PicArea picArea = (PicArea)fpRel;
                        FlightPlan fp = currentFlightplanProperty().get();
                        AreaOfInterest aoi = null;
                        if (fp != null) {
                            aoi = currentFlightplanProperty().get().getAreaOfInterest(picArea);
                        }

                        return aoi;
                    }

                    fpRel = fpRel.getParent();
                }
            }
        }

        return null;
    }
}
