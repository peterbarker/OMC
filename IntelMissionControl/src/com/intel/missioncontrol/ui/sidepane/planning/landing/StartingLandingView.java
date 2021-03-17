/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol.ui.sidepane.planning.landing;

import com.google.inject.Inject;
import com.intel.missioncontrol.beans.property.IQuantityStyleProvider;
import com.intel.missioncontrol.helper.ILanguageHelper;
import com.intel.missioncontrol.measure.Dimension;
import com.intel.missioncontrol.measure.Quantity;
import com.intel.missioncontrol.measure.QuantityArithmetic;
import com.intel.missioncontrol.measure.Unit;
import com.intel.missioncontrol.measure.VariantQuantity;
import com.intel.missioncontrol.settings.GeneralSettings;
import com.intel.missioncontrol.settings.ISettingsManager;
import com.intel.missioncontrol.ui.ViewBase;
import com.intel.missioncontrol.ui.ViewHelper;
import com.intel.missioncontrol.ui.controls.AdornerSplitView;
import com.intel.missioncontrol.ui.controls.ToggleSwitch;
import com.intel.missioncontrol.ui.controls.VariantQuantitySpinnerValueFactory;
import de.saxsys.mvvmfx.InjectViewModel;
import eu.mavinci.core.flightplan.AltitudeAdjustModes;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleButton;

public class StartingLandingView extends ViewBase<StartingLandingViewModel> {

    public static final String ELEVATION_OVER_R = "takeoffLanding.labelElevationOverR";
    public static final String ELEVATION_AGL = "takeoffLanding.labelElevationAGL";
    private static final String TOGGLE_YES = "com.intel.missioncontrol.ui.toggle.Yes";
    private static final String TOGGLE_NO = "com.intel.missioncontrol.ui.toggle.No";

    @InjectViewModel
    private StartingLandingViewModel viewModel;

    @FXML
    private AdornerSplitView rootNode;

    @FXML
    private Spinner<VariantQuantity> takeOffLatitudeSpinner;

    @FXML
    private Spinner<VariantQuantity> takeOffLongitudeSpinner;

    @FXML
    private ToggleButton chooseTakeOffPositionButton;

    @FXML
    private ToggleSwitch autoSwitch;

    @FXML
    private Label elevationLabel;

    @FXML
    private Spinner<Quantity<Dimension.Length>> elevationSpinner;

    private final IQuantityStyleProvider quantityStyleProvider;

    private final ISettingsManager settingsManager;

    private final ILanguageHelper languageHelper;

    @Inject
    public StartingLandingView(ISettingsManager settingsManager, ILanguageHelper languageHelper) {
        this.quantityStyleProvider = settingsManager.getSection(GeneralSettings.class);
        this.settingsManager = settingsManager;
        this.languageHelper = languageHelper;
    }

    @Override
    public void initializeView() {
        super.initializeView();

        int maxAngleFractionDigits = 6;
        int significantAngleDigits = 8;

        var latAngleSettings =
            new VariantQuantitySpinnerValueFactory.FactorySettings<>(
                Dimension.Angle.class,
                Quantity.of(-90, Unit.DEGREE),
                Quantity.of(90, Unit.DEGREE),
                0.00000899823,
                true,
                significantAngleDigits,
                maxAngleFractionDigits);

        var lonAngleSettings =
            new VariantQuantitySpinnerValueFactory.FactorySettings<>(
                Dimension.Angle.class,
                Quantity.of(-180, Unit.DEGREE),
                Quantity.of(180, Unit.DEGREE),
                0.00000899823,
                true,
                significantAngleDigits,
                maxAngleFractionDigits);

        var lengthSettings =
            new VariantQuantitySpinnerValueFactory.FactorySettings<>(
                Dimension.Length.class,
                Quantity.of(-Double.MAX_VALUE, Unit.METER),
                Quantity.of(Double.MAX_VALUE, Unit.METER),
                1,
                true,
                8,
                5);

        var valueFactory =
            new VariantQuantitySpinnerValueFactory(
                quantityStyleProvider,
                QuantityArithmetic.LATITUDE,
                viewModel.takeOffLatitudeProperty(),
                latAngleSettings,
                lengthSettings);

        takeOffLatitudeSpinner.setValueFactory(valueFactory);
        takeOffLatitudeSpinner.setEditable(true);

        var lonValueFactory =
            new VariantQuantitySpinnerValueFactory(
                quantityStyleProvider,
                QuantityArithmetic.LONGITUDE,
                viewModel.takeOffLongitudeProperty(),
                lonAngleSettings,
                lengthSettings);

        takeOffLongitudeSpinner.setValueFactory(lonValueFactory);
        takeOffLongitudeSpinner.setEditable(true);

        chooseTakeOffPositionButton
            .disableProperty()
            .bind(viewModel.getToggleChooseTakeOffPositionCommand().notExecutableProperty());

        chooseTakeOffPositionButton.selectedProperty().bindBidirectional(viewModel.takeoffButtonPressedProperty());
        chooseTakeOffPositionButton.getStyleClass().remove("toggle-button");

        autoSwitch.selectedProperty().bindBidirectional(viewModel.autoEnabledProperty());

        autoSwitch.disableProperty().bind(viewModel.recalculateOnEveryChangeProperty().not());

        viewModel
            .autoEnabledProperty()
            .addListener((observable, oldValue, newValue) -> setToggleLabel(!viewModel.autoEnabledProperty().get()));
        setToggleLabel(!viewModel.autoEnabledProperty().get());

        takeOffLatitudeSpinner.disableProperty().bind(viewModel.autoEnabledProperty());
        takeOffLongitudeSpinner.disableProperty().bind(viewModel.autoEnabledProperty());

        elevationSpinner.visibleProperty().bind(viewModel.showTakeoffElevationProperty());
        elevationSpinner.managedProperty().bind(viewModel.showTakeoffElevationProperty());

        ViewHelper.initAutoCommitSpinner(
            elevationSpinner,
            viewModel.takeoffElevationProperty(),
            Unit.METER,
            settingsManager.getSection(GeneralSettings.class),
            5,
            0.0,
            1000.0,
            1.0,
            false);
        elevationSpinner.disableProperty().bind(viewModel.autoEnabledProperty());
        elevationLabel
            .textProperty()
            .bind(
                Bindings.createStringBinding(
                    () -> {
                        AltitudeAdjustModes mode = viewModel.currentAltModeProperty().get();
                        if (mode != null) {
                            if (mode.equals(AltitudeAdjustModes.CONSTANT_OVER_R)) {
                                return languageHelper.getString(ELEVATION_OVER_R);
                            } else if (mode.equals(AltitudeAdjustModes.FOLLOW_TERRAIN)) {
                                return languageHelper.getString(ELEVATION_AGL);
                            }
                        }

                        return languageHelper.getString(ELEVATION_AGL);
                    },
                    viewModel.currentAltModeProperty()));
    }

    @Override
    public AdornerSplitView getRootNode() {
        return rootNode;
    }

    @Override
    public StartingLandingViewModel getViewModel() {
        return viewModel;
    }

    @FXML
    public void onToggleChooseTakeOffPositionClicked() {
        viewModel.getToggleChooseTakeOffPositionCommand().execute();
    }

    private void setToggleLabel(boolean val) {
        if (val) {
            autoSwitch.setText(languageHelper.getString(TOGGLE_NO));
        } else {
            autoSwitch.setText(languageHelper.getString(TOGGLE_YES));
        }
    }

}
