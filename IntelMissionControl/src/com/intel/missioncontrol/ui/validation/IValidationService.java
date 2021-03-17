/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol.ui.validation;

import com.intel.missioncontrol.mission.FlightPlan;
import com.intel.missioncontrol.mission.Matching;
import eu.mavinci.flightplan.PicArea;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyListProperty;

public interface IValidationService {

    void notifyAirplaneConnected();

    void notifyAirplaneDisconnected();

    ObjectProperty<FlightPlan> observedFlightPlanProperty();

    ObjectProperty<Matching> observedMatchingProperty();

    ReadOnlyListProperty<ResolvableValidationMessage> flightValidationMessagesProperty();

    ReadOnlyBooleanProperty canExecuteFlightProperty();

    ReadOnlyListProperty<ResolvableValidationMessage> planningValidationMessagesProperty();

    ReadOnlyBooleanProperty canExportFlightProperty();

    ReadOnlyListProperty<ResolvableValidationMessage> datasetValidationMessagesProperty();

    ReadOnlyBooleanProperty canExportDatasetProperty();

    ReadOnlyListProperty<ResolvableValidationStatus> flightAndPlanAllValidationStatusesProperty();

    ValidatorBase<FlightPlan> getValidator(Class<? extends ValidatorBase<FlightPlan>> type, FlightPlan validatedObject);

    ValidatorBase<PicArea> getValidator(Class<? extends ValidatorBase<PicArea>> type, PicArea validatedObject);

    ValidatorBase<Matching> getValidator(Class<? extends ValidatorBase<Matching>> type, Matching validatedObject);

    void addValidatorsChangedListener(InvalidationListener listener);

}
