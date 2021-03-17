/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol.ui.sidepane.planning.landing;

import com.google.inject.Inject;
import com.intel.missioncontrol.IApplicationContext;
import com.intel.missioncontrol.beans.property.PropertyPath;
import com.intel.missioncontrol.beans.property.PropertyPathStore;
import com.intel.missioncontrol.beans.property.QuantityProperty;
import com.intel.missioncontrol.beans.property.SimpleQuantityProperty;
import com.intel.missioncontrol.beans.property.VariantQuantityProperty;
import com.intel.missioncontrol.concurrent.Dispatcher;
import com.intel.missioncontrol.helper.ILanguageHelper;
import com.intel.missioncontrol.map.IMapController;
import com.intel.missioncontrol.map.ISelectionManager;
import com.intel.missioncontrol.map.InputMode;
import com.intel.missioncontrol.measure.Dimension;
import com.intel.missioncontrol.measure.Quantity;
import com.intel.missioncontrol.measure.Unit;
import com.intel.missioncontrol.measure.UnitInfo;
import com.intel.missioncontrol.measure.VariantQuantity;
import com.intel.missioncontrol.mission.FlightPlan;
import com.intel.missioncontrol.mission.Mission;
import com.intel.missioncontrol.mission.SrsPosition;
import com.intel.missioncontrol.settings.GeneralSettings;
import com.intel.missioncontrol.settings.ISettingsManager;
import com.intel.missioncontrol.settings.SrsSettings;
import com.intel.missioncontrol.ui.ViewModelBase;
import com.intel.missioncontrol.ui.commands.DelegateCommand;
import com.intel.missioncontrol.ui.commands.ICommand;
import eu.mavinci.core.flightplan.AltitudeAdjustModes;
import eu.mavinci.desktop.helper.gdal.MSpatialReference;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;

/** View model used to integrate "Start & Landing" flight plan settings section with FlightPlan domain object. */
public class StartingLandingViewModel extends ViewModelBase {

    private final BooleanProperty autoEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty showTakeoffElevation = new SimpleBooleanProperty(true);
    private final SimpleBooleanProperty takeoffButtonPressed = new SimpleBooleanProperty();
    private final QuantityProperty<Dimension.Length> takeoffElevation;
    private final BooleanProperty recalculateOnEveryChange;
    private final ReadOnlyObjectProperty<AltitudeAdjustModes> currentAltMode;

    private final PropertyPathStore propertyPathStore = new PropertyPathStore();
    private final IApplicationContext applicationContext;
    private final ICommand toggleChooseTakeOffPositionCommand;
    private final ISelectionManager selectionManager;
    private final ChangeListener<MSpatialReference> spatialReferenceChangeListener;
    private SrsPosition takeoffPositionWrapper;
    private ChangeListener<InputMode> mouseModesChangeListener =
        ((observable, oldValue, newValue) -> {
            if (newValue.equals(InputMode.SET_TAKEOFF_POINT)) {
                takeoffButtonPressed.setValue(true);
            } else {
                takeoffButtonPressed.setValue(false);
            }
        });

    private final IMapController mapController;

    @Inject
    public StartingLandingViewModel(
            IApplicationContext applicationContext,
            ISettingsManager settingsManager,
            ILanguageHelper languageHelper,
            IMapController mapController,
            ISelectionManager selectionManager) {
        this.applicationContext = applicationContext;
        this.mapController = mapController;
        this.selectionManager = selectionManager;
        GeneralSettings generalSettings = settingsManager.getSection(GeneralSettings.class);

        recalculateOnEveryChange =
            PropertyPath.from(applicationContext.currentMissionProperty())
                .select(Mission::currentFlightPlanProperty)
                .selectBoolean(FlightPlan::recalculateOnEveryChangeProperty);

        takeoffPositionWrapper = new SrsPosition(generalSettings);
        SrsSettings srsSettings = settingsManager.getSection(SrsSettings.class);
        takeoffPositionWrapper.setSrs(srsSettings.getApplicationSrs());

        spatialReferenceChangeListener = (observable, oldValue, newValue) -> takeoffPositionWrapper.setSrs(newValue);
        srsSettings.applicationSrsProperty().addListener(new WeakChangeListener<>(spatialReferenceChangeListener));

        toggleChooseTakeOffPositionCommand = new DelegateCommand(this::toggleChooseTakeOffPosition);

        takeoffElevation =
            new SimpleQuantityProperty<>(generalSettings, UnitInfo.LOCALIZED_LENGTH, Quantity.of(0.0, Unit.METER));

        currentAltMode =
            PropertyPath.from(applicationContext.currentMissionProperty())
                .select(Mission::currentFlightPlanProperty)
                .selectReadOnlyObject(FlightPlan::currentAltModeProperty);
    }

    @Override
    protected void initializeViewModel() {
        super.initializeViewModel();

        mapController
            .mouseModeProperty()
            .addListener(new WeakChangeListener<>(mouseModesChangeListener), Dispatcher::dispatchToUI);

        Mission m = applicationContext.currentMissionProperty().get();
        if (m != null && m.getCurrentFlightPlan() != null) {
            takeoffPositionWrapper
                .positionProperty()
                .bindBidirectional(
                    propertyPathStore
                        .from(applicationContext.currentMissionProperty())
                        .select(Mission::currentFlightPlanProperty)
                        .selectObject(FlightPlan::takeoffPositionProperty));

            autoEnabled.bindBidirectional(
                propertyPathStore
                    .from(applicationContext.currentMissionProperty())
                    .select(Mission::currentFlightPlanProperty)
                    .selectBoolean(FlightPlan::takeoffAutoProperty));
        }

        takeoffPositionWrapper
            .positionProperty()
            .addListener(
                (obj, oldV, newV) -> {
                    if (newV == null) {
                        return;
                    }
                });

        takeoffElevation.bindBidirectional(
            propertyPathStore
                .from(applicationContext.currentMissionProperty())
                .select(Mission::currentFlightPlanProperty)
                .selectObject(FlightPlan::takeoffElevationProperty));
    }

    public BooleanProperty recalculateOnEveryChangeProperty() {
        return recalculateOnEveryChange;
    }

    public BooleanProperty showTakeoffElevationProperty() {
        return showTakeoffElevation;
    }

    public BooleanProperty autoEnabledProperty() {
        return autoEnabled;
    }

    public VariantQuantityProperty takeOffLatitudeProperty() {
        return takeoffPositionWrapper.latitudeQuantity();
    }

    public SimpleBooleanProperty takeoffButtonPressedProperty() {
        return takeoffButtonPressed;
    }

    public QuantityProperty<Dimension.Length> takeoffElevationProperty() {
        return takeoffElevation;
    }

    public VariantQuantity getTakeOffLatitude() {
        return takeOffLatitudeProperty().get();
    }

    public void setTakeOffLatitude(VariantQuantity value) {
        takeOffLatitudeProperty().set(value);
    }

    public VariantQuantityProperty takeOffLongitudeProperty() {
        return takeoffPositionWrapper.longitudeQuantity();
    }

    public VariantQuantity getTakeOffLongitude() {
        return takeOffLongitudeProperty().get();
    }

    public void setTakeOffLongitude(VariantQuantity value) {
        takeOffLongitudeProperty().set(value);
    }

    public ICommand getToggleChooseTakeOffPositionCommand() {
        return toggleChooseTakeOffPositionCommand;
    }

    private void toggleChooseTakeOffPosition() {
        if (mapController.getMouseMode() != InputMode.SET_TAKEOFF_POINT) {
            selectionManager.setSelection(
                applicationContext.getCurrentMission().getCurrentFlightPlan().getLegacyFlightplan().getTakeoff());
            mapController.setMouseMode(InputMode.SET_TAKEOFF_POINT);
        } else {
            mapController.tryCancelMouseModes(InputMode.SET_TAKEOFF_POINT);
        }
    }

    public ReadOnlyObjectProperty<AltitudeAdjustModes> currentAltModeProperty() {
        return currentAltMode;
    }
}