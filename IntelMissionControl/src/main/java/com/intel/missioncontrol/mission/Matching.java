/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol.mission;

import com.intel.missioncontrol.IApplicationContext;
import com.intel.missioncontrol.INotificationObject;
import com.intel.missioncontrol.hardware.IGenericCameraConfiguration;
import com.intel.missioncontrol.hardware.IGenericCameraDescription;
import com.intel.missioncontrol.hardware.IHardwareConfiguration;
import com.intel.missioncontrol.hardware.IHardwareConfigurationManager;
import com.intel.missioncontrol.hardware.ILensDescription;
import com.intel.missioncontrol.hardware.IPlatformDescription;
import com.intel.missioncontrol.helper.ILanguageHelper;
import com.intel.missioncontrol.map.IMapView;
import com.intel.missioncontrol.measure.Unit;
import com.intel.missioncontrol.mission.bindings.BeanAdapter;
import com.intel.missioncontrol.ui.navigation.INavigationService;
import com.intel.missioncontrol.ui.navigation.WorkflowStep;
import com.intel.missioncontrol.ui.notifications.Toast;
import com.intel.missioncontrol.ui.notifications.ToastType;
import com.intel.missioncontrol.ui.sidepane.analysis.FlightLogEntry;
import com.intel.missioncontrol.ui.sidepane.analysis.ImageChannel;
import com.intel.missioncontrol.utils.IBackgroundTaskManager;
import de.saxsys.mvvmfx.internal.viewloader.DependencyInjector;
import eu.mavinci.core.flightplan.CPhotoLogLine;
import eu.mavinci.core.flightplan.CPicArea;
import eu.mavinci.core.flightplan.GPSFixType;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.AMapLayerMatching;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.ExifInfos;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.LocationType;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.MapLayerCoverageMatching;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.MapLayerMatch;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.MapLayerMatching;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.MapLayerPicArea;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.PhotoCube;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.PhotoFile;
import eu.mavinci.desktop.gui.doublepanel.planemain.tagging.ProjectionType;
import eu.mavinci.desktop.gui.doublepanel.planemain.tree.maplayers.IMapLayer;
import eu.mavinci.desktop.gui.doublepanel.planemain.tree.maplayers.IMapLayerListener;
import eu.mavinci.desktop.gui.doublepanel.planemain.tree.maplayers.MapLayer;
import eu.mavinci.desktop.helper.IRecomputeListener;
import eu.mavinci.desktop.helper.MFileFilter;
import eu.mavinci.desktop.helper.Recomputer;
import eu.mavinci.desktop.main.debug.Debug;
import eu.mavinci.flightplan.Flightplan;
import eu.mavinci.flightplan.Point;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import org.apache.commons.lang3.ArrayUtils;
import org.asyncfx.concurrent.Dispatcher;

public class Matching implements ISaveable {

    private final MatchingViewOptions viewOptions = new MatchingViewOptions();

    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<MatchingStatus> status = new SimpleObjectProperty<>(MatchingStatus.NEW);
    private final ObjectProperty<IBackgroundTaskManager.BackgroundTask> dataTransferBackgroundTask =
        new SimpleObjectProperty<>();
    private final IntegerProperty filteredItemsCount = new SimpleIntegerProperty();
    private final IntegerProperty picturesCount = new SimpleIntegerProperty();
    private final LongProperty filteredPicturesSizeBytes = new SimpleLongProperty();
    private final StringProperty filteredPictureType = new SimpleStringProperty("");
    private final LongProperty rtkFixCount = new SimpleLongProperty();
    private final LongProperty rtkFloatCount = new SimpleLongProperty();
    private final LongProperty diffGpsFixCount = new SimpleLongProperty();
    private final LongProperty gpsFixCount = new SimpleLongProperty();
    private final StringProperty exifDataMsg = new SimpleStringProperty("");
    private final BooleanProperty matchingLayerChanged = new SimpleBooleanProperty();
    private final BooleanProperty rtkAvailable = new SimpleBooleanProperty();
    private final BooleanProperty filtersEnabled = new SimpleBooleanProperty();
    private final BooleanProperty altitudeEnabled = new SimpleBooleanProperty();
    private final DoubleProperty altitudeValue = new SimpleDoubleProperty();
    private final DoubleProperty altitudeSpread = new SimpleDoubleProperty();
    private final BooleanProperty rollEnabled = new SimpleBooleanProperty();
    private final DoubleProperty rollValue = new SimpleDoubleProperty();
    private final DoubleProperty rollSpread = new SimpleDoubleProperty();
    private final BooleanProperty pitchEnabled = new SimpleBooleanProperty();
    private final DoubleProperty pitchValue = new SimpleDoubleProperty();
    private final DoubleProperty pitchSpread = new SimpleDoubleProperty();
    private final BooleanProperty yawEnabled = new SimpleBooleanProperty();
    private final DoubleProperty yawValue = new SimpleDoubleProperty();
    private final DoubleProperty yawSpread = new SimpleDoubleProperty();
    private final DoubleProperty rtkAvgTime = new SimpleDoubleProperty();
    private final BooleanProperty onlyAoiEnabled = new SimpleBooleanProperty();
    private final ObjectProperty<IHardwareConfiguration> hardwareConfiguration = new SimpleObjectProperty<>();
    private final ObjectProperty<LocationType> rtkBaseLocationType = new SimpleObjectProperty<>(LocationType.ASSUMED);
    private final BooleanProperty rtkLocationConfirmed = new SimpleBooleanProperty();
    private final DoubleProperty antennaHeight = new SimpleDoubleProperty();
    private final DoubleProperty geoidOffset = new SimpleDoubleProperty();
    private final ObjectProperty<Position> positionReal = new SimpleObjectProperty<>();
    private final ObjectProperty<Position> positionAssumed = new SimpleObjectProperty<>();
    private final DoubleProperty trueOrthoRatio = new SimpleDoubleProperty();
    private final DoubleProperty pseudoOrthoRatio = new SimpleDoubleProperty();
    private final DoubleProperty trueOrthoArea = new SimpleDoubleProperty();
    private final DoubleProperty pseudoOrthoArea = new SimpleDoubleProperty();
    private final ListProperty<AreaFilter> areaFilters = new SimpleListProperty<>(FXCollections.observableArrayList());

    private boolean hasDefaultName = false;
    private final LongProperty toImportTriggersCount = new SimpleLongProperty();
    private final LongProperty toImportImagesCount = new SimpleLongProperty();
    private final BooleanProperty toImportFlightLog = new SimpleBooleanProperty();
    private final ListProperty<File> toImportImages = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ObjectProperty<File> toImportTargetFolder = new SimpleObjectProperty<>();
    private final StringProperty toImportImageSourceFolder = new SimpleStringProperty();

    public LongProperty toImportImagesCountProperty() {
        return toImportImagesCount;
    }

    public LongProperty toImportTriggersCountProperty() {
        return toImportTriggersCount;
    }

    public BooleanProperty toImportFlightLogProperty() {
        return toImportFlightLog;
    }

    public ListProperty<File> toImportImagesProperty() {
        return toImportImages;
    }

    public ObjectProperty<File> toImportTargetFolderProperty() {
        return toImportTargetFolder;
    }

    public StringProperty toImportImageSourceFolderProperty() {
        return toImportImageSourceFolder;
    }

    public ObjectProperty<IBackgroundTaskManager.BackgroundTask> dataTransferBackgroundTaskProperty() {
        return dataTransferBackgroundTask;
    }

    private final AMapLayerMatching legacyMatching;
    private BeanAdapter<AMapLayerMatching> beanAdapter;
    private UUID instanceId = UUID.randomUUID();

    private final INavigationService navigationService =
        DependencyInjector.getInstance().getInstanceOf(INavigationService.class);
    private final INotificationObject.ChangeListener hardwareDescriptionListener =
        event -> {
            mapImageChannels(
                hardwareConfiguration
                    .get()
                    .getPrimaryPayload(IGenericCameraConfiguration.class)
                    .getDescription()
                    .getBandNamesSplit());
            checkExif();
        };

    private final IRecomputeListener coverageRecomputeListener =
        new IRecomputeListener() {
            @Override
            public void recomputeReady(Recomputer recomputer, boolean anotherRecomputeIsWaiting, long runNo) {
                Dispatcher.platform().run(() -> updateOrtho(legacyMatching));
            }
        };

    private final IMapLayerListener mapLayerListener =
        new IMapLayerListener() {
            private void updateValuesFromSource() {
                if (!Platform.isFxApplicationThread()) {
                    Dispatcher.platform().runLater(this::updateValuesFromSource);
                } else {
                    Matching.this.updateValuesFromSource();
                }
            }

            @Override
            public void mapLayerValuesChanged(IMapLayer layer) {
                updateValuesFromSource();
            }

            @Override
            public void mapLayerVisibilityChanged(IMapLayer layer, boolean newVisibility) {}

            @Override
            public void childMapLayerInserted(int i, IMapLayer layer) {
                updateValuesFromSource();
            }

            @Override
            public void childMapLayerRemoved(int i, IMapLayer layer) {
                updateValuesFromSource();
            }

            @Override
            public void mapLayerStructureChanged(IMapLayer layer) {}
        };

    private final IHardwareConfigurationManager hardwareConfigurationManager;

    public Matching(String name, IHardwareConfigurationManager hardwareConfigurationManager) {
        this.hardwareConfigurationManager = hardwareConfigurationManager;
        this.name.set(name);
        hasDefaultName = true;
        legacyMatching = new MapLayerMatching(hardwareConfigurationManager.getImmutableDefault().deepCopy());
        statusProperty().set(MatchingStatus.NEW);
        initialize();
    }

    public Matching(File toLoad, IHardwareConfigurationManager hardwareConfigurationManager) throws Exception {
        statusProperty().set(MatchingStatus.IMPORTED);
        // legacy compatibility
        if (toLoad.getName().endsWith(MissionConstants.MATCHING_EXT) && !toLoad.isDirectory()) {
            toLoad = toLoad.getParentFile();
        }

        this.hardwareConfigurationManager = hardwareConfigurationManager;
        this.name.set(toLoad.getName());
        hasDefaultName = false;
        legacyMatching = new MapLayerMatching(toLoad, hardwareConfigurationManager.getImmutableDefault().deepCopy());
        initialize();
    }

    public void startTransferring(File toSaveTo, IHardwareConfiguration bestHw) throws IOException {
        MapLayerMatching layer = (MapLayerMatching)getLegacyMatching();
        layer.getPicsLayer().removeAllLayers(true);

        hasDefaultName = true;
        legacyMatching.setFile(toSaveTo);

        Dispatcher dispatcher = Dispatcher.platform();
        dispatcher.run(
            () -> {
                hardwareConfiguration.get().setConfigurationFrom(bestHw);
                this.name.set(toSaveTo.getName());
                statusProperty().set(MatchingStatus.TRANSFERRING);
            });
    }

    private void checkExif() {
        String dataMessage = null;

        if (legacyMatching instanceof MapLayerMatching) {
            MapLayerMatching matching = (MapLayerMatching)legacyMatching;
            dataMessage = matching.checkExif();
        }

        exifDataMsg.set(dataMessage);
    }

    private void initialize() {
        if (beanAdapter != null) {
            throw new RuntimeException("please only initialize this class once");
        }

        beanAdapter = new BeanAdapter<>(legacyMatching);

        if (legacyMatching instanceof MapLayerMatching) {
            MapLayerMatching matching = (MapLayerMatching)legacyMatching;

            IHardwareConfiguration slaveHwConfig = matching.getHardwareConfiguration();
            slaveHwConfig.addListener(hardwareDescriptionListener);

            String[] bandNames =
                slaveHwConfig.getPrimaryPayload(IGenericCameraConfiguration.class).getDescription().getBandNamesSplit();

            mapImageChannels(bandNames);
            hardwareConfiguration.set(slaveHwConfig);
        }

        // Bindings to AMapLayerMatching
        //
        beanAdapter
            .bind(filtersEnabled)
            .to(matching -> !matching.isUsingAll(), (matching, value) -> matching.setUseAll(!value));
        beanAdapter.bind(filteredItemsCount).to(AMapLayerMatching::getCountFiltered);
        beanAdapter.bind(picturesCount).to(AMapLayerMatching::getPicturesCount);
        beanAdapter.bind(filteredPicturesSizeBytes).to(AMapLayerMatching::getTotalSizeFilteredBytes);
        beanAdapter.bind(filteredPictureType).to(AMapLayerMatching::getFilteredFileType);
        beanAdapter.bind(rtkFixCount).to(matching -> matching.getGpsFixTypeCount(GPSFixType.rtkFixedBL));
        beanAdapter.bind(rtkFloatCount).to(matching -> matching.getGpsFixTypeCount(GPSFixType.rtkFloatingBL));
        beanAdapter.bind(diffGpsFixCount).to(matching -> matching.getGpsFixTypeCount(GPSFixType.dgps));
        beanAdapter.bind(gpsFixCount).to(matching -> matching.getGpsFixTypeCount(GPSFixType.gpsFix));
        beanAdapter
            .bind(altitudeEnabled)
            .to(AMapLayerMatching::getAltitudeAGLEnabled, AMapLayerMatching::setAltitudeAGLEnabled);
        beanAdapter.bind(altitudeValue).to(AMapLayerMatching::getAltitudeValue, AMapLayerMatching::setAltitudeValue);
        beanAdapter.bind(altitudeSpread).to(AMapLayerMatching::getAltitudeSpread, AMapLayerMatching::setAltitudeSpread);
        beanAdapter.bind(rollEnabled).to(AMapLayerMatching::getRollEnabled, AMapLayerMatching::setRollEnabled);
        beanAdapter.bind(rollValue).to(AMapLayerMatching::getRollValue, AMapLayerMatching::setRollValue);
        beanAdapter.bind(rollSpread).to(AMapLayerMatching::getRollSpread, AMapLayerMatching::setRollSpread);
        beanAdapter.bind(pitchEnabled).to(AMapLayerMatching::getPitchEnabled, AMapLayerMatching::setPitchEnabled);
        beanAdapter.bind(pitchValue).to(m -> m.getPitchValue() - 90, (m, v) -> m.setPitchValue(v + 90));
        beanAdapter.bind(pitchSpread).to(AMapLayerMatching::getPitchSpread, AMapLayerMatching::setPitchSpread);
        beanAdapter.bind(yawEnabled).to(AMapLayerMatching::getYawEnabled, AMapLayerMatching::setYawEnabled);
        beanAdapter.bind(yawValue).to(AMapLayerMatching::getYawValue, AMapLayerMatching::setYawValue);
        beanAdapter.bind(yawSpread).to(AMapLayerMatching::getYawSpread, AMapLayerMatching::setYawSpread);
        beanAdapter.bind(onlyAoiEnabled).to(AMapLayerMatching::isOnlyInPicArea, AMapLayerMatching::setOnlyInPicArea);
        beanAdapter.bind(matchingLayerChanged).to(AMapLayerMatching::isChanged);

        // Bindings to MapLayerMatching
        //
        beanAdapter
            .subtype(MapLayerMatching.class)
            .bind(rtkLocationConfirmed)
            .to(MapLayerMatching::isConfirmAsCorrect, MapLayerMatching::setConfirmAsCorrect, true);
        beanAdapter
            .subtype(MapLayerMatching.class)
            .bind(rtkBaseLocationType)
            .to(MapLayerMatching::getLocationType, MapLayerMatching::setLocationType, true);
        beanAdapter
            .subtype(MapLayerMatching.class)
            .bind(antennaHeight)
            .to(MapLayerMatching::getRealAntennaAlt, MapLayerMatching::setRealAntennaAlt, true);
        beanAdapter
            .subtype(MapLayerMatching.class)
            .bind(geoidOffset)
            .to(MapLayerMatching::getGeoidOffset, MapLayerMatching::setGeoidOffset, true);
        beanAdapter
            .subtype(MapLayerMatching.class)
            .bind(positionReal)
            .to(
                MapLayerMatching::getRealPositionWitoutAntennaShiftIgnoringLocationType,
                (m, v) -> m.setRealPos(v.getLatitude().degrees, v.getLongitude().degrees, v.getAltitude()),
                true);
        beanAdapter.subtype(MapLayerMatching.class).bind(positionAssumed).to(MapLayerMatching::getRTKPosition);
        beanAdapter.subtype(MapLayerMatching.class).bind(rtkAvgTime).to(MapLayerMatching::getRtkAvgTime);
        beanAdapter.subtype(MapLayerMatching.class).bind(rtkAvailable).to(MapLayerMatching::isRTKposAvaiable);

        // Bindings for AMapLayerMatching view options
        //
        beanAdapter
            .bind(viewOptions.showPreviewProperty())
            .to(m -> !m.getPicsLayer().isShowOnlyOutlines(), (m, v) -> m.getPicsLayer().setShowOnlyOutlines(!v));

        beanAdapter
            .bind(viewOptions.showImageLocationsProperty())
            .to(m -> m.getPicsLayer().isShowImageLocations(), (m, v) -> m.getPicsLayer().setShowImageLocations(v));

        beanAdapter
            .bind(viewOptions.showCoverageProperty())
            .to(m -> m.getCoverage().isVisible(), (m, v) -> m.getCoverage().setVisible(v));

        beanAdapter
            .bind(viewOptions.imageProjectionProperty())
            .to(AMapLayerMatching::getProjectionType, AMapLayerMatching::setProjectionType);

        beanAdapter
            .bind(viewOptions.projectionDistanceProperty())
            .to(AMapLayerMatching::getProjectionDistance, AMapLayerMatching::setProjectionDistance);

        beanAdapter
            .bind(viewOptions.elevationOffsetProperty())
            .to(AMapLayerMatching::getElevationOffset, AMapLayerMatching::setElevationOffset);

        // Bindings for MapLayerMatching view options
        //
        beanAdapter
            .subtype(MapLayerMatching.class)
            .bind(viewOptions.showAoisProperty())
            .to(m -> m.getPicAreasLayer().isVisible(), (m, v) -> m.getPicAreasLayer().setVisible(v));

        beanAdapter
            .subtype(MapLayerMatching.class)
            .bind(viewOptions.showRtkProperty())
            .to(
                m -> {
                    MapLayer layer = m.getMayLayerRTKPosition();
                    return layer != null && layer.isVisible();
                },
                (m, v) -> {
                    MapLayer layer = m.getMayLayerRTKPosition();
                    if (layer != null) {
                        layer.setVisible(v);
                    }
                });

        beanAdapter
            .subtype(MapLayerMatching.class)
            .bind(viewOptions.showTrackProperty())
            .to(
                m -> {
                    MapLayer layer = m.getTrackLayer();
                    return layer != null && layer.isVisible();
                },
                (m, v) -> {
                    MapLayer layer = m.getTrackLayer();
                    if (layer != null) {
                        layer.setVisible(v);
                    }
                });
        beanAdapter
            .bind(name)
            .to(
                m -> {
                    // dont load name from legacy until its ready loaded, otherwise the "new Dataset" name will get
                    // overwritten too early
                    return status.get() != MatchingStatus.IMPORTED ? name.get() : m.getName();
                },
                (m, v) -> { // not implemented
                });

        legacyMatching.setVisible(true);

        status.addListener(
            (observable, oldValue, newValue) -> {
                if (newValue == MatchingStatus.IMPORTED) {
                    checkExif();
                }
            });
        if (status.get() == MatchingStatus.IMPORTED) {
            checkExif();
        }

        bindMapViewOptions();

        // load control values and then add listeners
        legacyMatching.addMapListener(mapLayerListener);
        legacyMatching.getCoverage().addRecomputeListener(coverageRecomputeListener);

        updateValuesFromSource();
    }

    private void bindMapViewOptions() {
        legacyMatching.getPicsLayer().setVisible(true);
    }

    private void mapImageChannels(String[] bandNames) {
        viewOptions.availableChannelsProperty().clear();
        viewOptions
            .availableChannelsProperty()
            .addAll(Arrays.stream(bandNames).map(ImageChannel::fromStringName).collect(Collectors.toList()));
        viewOptions
            .selectedChannelProperty()
            .setValue(ImageChannel.fromStringName(bandNames[legacyMatching.getCurrentBandNo()]));
    }

    public String getName() {
        return name.get();
    }

    @Override
    public boolean hasUnsavedChanges() {
        return matchingLayerChanged.get();
    }

    @Override
    public boolean canBeSaved() {
        return status.get().equals(MatchingStatus.IMPORTED);
    }

    public ReadOnlyStringProperty nameProperty() {
        return name;
    }

    public MatchingStatus getStatus() {
        return status.get();
    }

    public ObjectProperty<MatchingStatus> statusProperty() {
        return status;
    }

    public AMapLayerMatching getLegacyMatching() {
        return legacyMatching;
    }

    public ReadOnlyIntegerProperty filteredItemsCountProperty() {
        return filteredItemsCount;
    }

    public ReadOnlyIntegerProperty picturesCountProperty() {
        return picturesCount;
    }

    public ReadOnlyLongProperty filteredPicturesSizeBytesProperty() {
        return filteredPicturesSizeBytes;
    }

    public StringProperty filteredPictureTypeProperty() {
        return filteredPictureType;
    }

    public LongProperty rtkFixCountProperty() {
        return rtkFixCount;
    }

    public LongProperty rtkFloatCountProperty() {
        return rtkFloatCount;
    }

    public LongProperty diffGpsFixCountProperty() {
        return diffGpsFixCount;
    }

    public LongProperty gpsFixCountProperty() {
        return gpsFixCount;
    }

    public boolean hasDefaultName() {
        return hasDefaultName;
    }

    public Sector getSector() {
        if (legacyMatching == null) {
            return null;
        }

        return legacyMatching.getSector();
    }

    public OptionalDouble getMaxElev() {
        if (legacyMatching == null) {
            return OptionalDouble.empty();
        }

        return legacyMatching.getMaxElev();
    }

    public OptionalDouble getMinElev() {
        if (legacyMatching == null) {
            return OptionalDouble.empty();
        }

        return legacyMatching.getMinElev();
    }

    public MatchingViewOptions getViewOptions() {
        return viewOptions;
    }

    public UUID getInstanceId() {
        return instanceId;
    }

    public BooleanProperty rtkLocationConfirmedProperty() {
        return rtkLocationConfirmed;
    }

    public LocationType getRtkBaseLocationType() {
        return rtkBaseLocationType.get();
    }

    public ObjectProperty<LocationType> rtkBaseLocationTypeProperty() {
        return rtkBaseLocationType;
    }

    public DoubleProperty antennaHeightProperty() {
        return antennaHeight;
    }

    public DoubleProperty geoidOffsetProperty() {
        return geoidOffset;
    }

    public ObjectProperty<Position> positionRealProperty() {
        return positionReal;
    }

    public ObjectProperty<Position> positionAssumedProperty() {
        return positionAssumed;
    }

    public ReadOnlyDoubleProperty rtkAvgTimeProperty() {
        return rtkAvgTime;
    }

    public ReadOnlyBooleanProperty rtkAvailableProperty() {
        return rtkAvailable;
    }

    private void updateOrtho(AMapLayerMatching matching) {
        MapLayerCoverageMatching coverage = matching.getCoverage();

        if (coverage == null) {
            trueOrthoRatio.set(
                -1); // -1 means that there is no area defined which we can take as scale... so no warnings would get
            // shown as well!
            pseudoOrthoRatio.set(-1);

            trueOrthoArea.set(0.0);
            pseudoOrthoArea.set(0.0);

            return;
        }

        trueOrthoRatio.set(coverage.getCoverageRatioOrtho());
        pseudoOrthoRatio.set(coverage.getCoverageRatioPseudoOrtho());

        trueOrthoArea.set(coverage.getQmOK());
        pseudoOrthoArea.set(coverage.getQmMedium());
    }

    public void saveResourceFile() {
        name.setValue(getMatchingFolder().getName());
        status.set(MatchingStatus.IMPORTED);
        if (legacyMatching instanceof MapLayerMatching) {
            ((MapLayerMatching)legacyMatching).saveResourceFile();
        }

        DependencyInjector.getInstance()
            .getInstanceOf(IApplicationContext.class)
            .addToast(
                Toast.of(ToastType.INFO)
                    .setText(
                        DependencyInjector.getInstance()
                            .getInstanceOf(ILanguageHelper.class)
                            .getString("eu.mavinci.desktop.gui.doublepanel.planemain.tagging.MatchingDataWriter.saved"))
                    .create());
    }

    public ReadOnlyObjectProperty<IHardwareConfiguration> hardwareConfigurationProperty() {
        return hardwareConfiguration;
    }

    public IHardwareConfiguration getHardwareConfiguration() {
        return hardwareConfiguration.get();
    }

    public StringProperty exifDataMsgProperty() {
        return exifDataMsg;
    }

    public ReadOnlyBooleanProperty matchingLayerChangedProperty() {
        return matchingLayerChanged;
    }

    private int indexOf(String[] bandNames, String stringName) {
        int index = ArrayUtils.indexOf(bandNames, stringName);
        if (index == ArrayUtils.INDEX_NOT_FOUND) {
            throw new IllegalStateException("Unable to map image channel with name: " + stringName);
        }

        return index;
    }

    public BooleanProperty filtersEnabledProperty() {
        return filtersEnabled;
    }

    public BooleanProperty altitudeEnabledProperty() {
        return altitudeEnabled;
    }

    public BooleanProperty onlyAoiEnabledProperty() {
        return onlyAoiEnabled;
    }

    public DoubleProperty altitudeValueProperty() {
        return altitudeValue;
    }

    public DoubleProperty altitudeSpreadProperty() {
        return altitudeSpread;
    }

    public BooleanProperty rollEnabledProperty() {
        return rollEnabled;
    }

    public DoubleProperty rollValueProperty() {
        return rollValue;
    }

    public DoubleProperty rollSpreadProperty() {
        return rollSpread;
    }

    public BooleanProperty pitchEnabledProperty() {
        return pitchEnabled;
    }

    public DoubleProperty pitchValueProperty() {
        return pitchValue;
    }

    public DoubleProperty pitchSpreadProperty() {
        return pitchSpread;
    }

    public BooleanProperty yawEnabledProperty() {
        return yawEnabled;
    }

    public DoubleProperty yawValueProperty() {
        return yawValue;
    }

    public DoubleProperty yawSpreadProperty() {
        return yawSpread;
    }

    public DoubleProperty trueOrthoRatioProperty() {
        return trueOrthoRatio;
    }

    public DoubleProperty pseudoOrthoRatioProperty() {
        return pseudoOrthoRatio;
    }

    public DoubleProperty trueOrthoAreaProperty() {
        return trueOrthoArea;
    }

    public DoubleProperty pseudoOrthoAreaProperty() {
        return pseudoOrthoArea;
    }

    public File getResourceFile() {
        return legacyMatching.getResourceFile();
    }

    @Override
    public void save() {
        saveResourceFile();
    }

    public ListProperty<AreaFilter> areaFiltersProperty() {
        return areaFilters;
    }

    public static List<MapLayerMatch> sortMatches(Matching matching, Comparator<MapLayerMatch> imageOrder) {
        Collection<Matching> matchings = new ArrayList<>();
        matchings.add(matching);
        return sortMatches(matchings, imageOrder);
    }

    private static List<MapLayerMatch> sortMatches(
            Collection<Matching> matchings, Comparator<MapLayerMatch> imageOrder) {
        return matchings
            .stream()
            .flatMap(matching -> matching.legacyMatching.getPictures().stream())
            .filter(MapLayerMatch.class::isInstance)
            .map(MapLayerMatch.class::cast)
            .sorted(
                (first, second) -> {
                    try {
                        return imageOrder.compare(first, second);
                    } catch (Throwable err) {
                        AMapLayerMatching.comparisonContractViolationFound = true;
                        Debug.getLog().log(Level.SEVERE, "could not sort matches", err);
                    }

                    return 0;
                })
            .collect(Collectors.toList());
    }

    public boolean assureNonRaw() {
        return getPictures()
            .stream()
            .filter(MapLayerMatch.class::isInstance)
            .map(MapLayerMatch.class::cast)
            .map(MapLayerMatch::getResourceFile)
            .noneMatch(MFileFilter.rawFilterNonTiff::accept);
    }

    public void setVisible(boolean b) {
        if (legacyMatching != null) {
            legacyMatching.setVisible(b);
        }
    }

    public double estimateGsd() {
        return legacyMatching.estimateGsd();
    }

    public double getEstimatedStartingElevationInMoverWgs84(boolean addEGMOffsetForExport) {
        return legacyMatching.getEstimatedStartingElevationInMoverWGS84(addEGMOffsetForExport);
    }

    public List<MapLayerPicArea> getVisiblePicAreas() {
        return legacyMatching.getVisiblePicAreas();
    }

    public List<IMapLayer> getPictures() {
        return legacyMatching.getPictures();
    }

    public int getNumberOfImagesPerPosition() {
        return legacyMatching.getNumberOfImagesPerPosition();
    }

    public String[] getBandNames() {
        return legacyMatching.getBandNames();
    }

    public void addDefaultAreaFilter() {
        AMapLayerMatching aMapLayerMatching = getLegacyMatching();
        if (!(aMapLayerMatching instanceof MapLayerMatching)) {
            return;
        }

        MapLayerMatching mapLayerMatching = (MapLayerMatching)aMapLayerMatching;
        MapLayerPicArea newPicArea = new MapLayerPicArea(mapLayerMatching);
        newPicArea.setGSD(CPicArea.DEF_GSD);
        List<MapLayerMatch> matches =
            mapLayerMatching.getCountFiltered() > 1
                ? mapLayerMatching.getPicsLayer().getMatchesFiltered()
                : mapLayerMatching.getPicsLayer().getMatches();
        List<LatLon> positions = matches.stream().map(MapLayerMatch::getLatLon).collect(Collectors.toList());
        Sector sector = Sector.boundingSector(positions);
        if (sector == null || sector.equals(Sector.EMPTY_SECTOR)) {
            return;
        }

        try {
            for (LatLon tmp : sector.getCorners()) {
                Point point = new Point(tmp.latitude.degrees, tmp.longitude.degrees);
                newPicArea.addToFlightplanContainer(point);
            }
        } catch (Exception e) {
            throw new RuntimeException("cant add corners to new area filter", e);
        }

        mapLayerMatching.getPicAreasLayer().addMapLayer(newPicArea);
    }

    public boolean tryAddPicAreasFromFlightplan(FlightPlan fp) {
        AMapLayerMatching aMapLayerMatching = getLegacyMatching();
        if (!(aMapLayerMatching instanceof MapLayerMatching)) {
            return false;
        }

        MapLayerMatching mapLayerMatching = (MapLayerMatching)aMapLayerMatching;
        return mapLayerMatching.getPicAreasLayer().tryAddPicAreasFromFlightplan(fp.getLegacyFlightplan());
    }

    public void previewLogfiles(List<FlightLogEntry> flightLogsSelected, IMapView mapView) {
        if (getStatus() != MatchingStatus.NEW || navigationService.getWorkflowStep() != WorkflowStep.DATA_PREVIEW) {
            return;
        }

        MapLayerMatching layer = (MapLayerMatching)getLegacyMatching();
        getViewOptions().showAoisProperty().set(false);
        getViewOptions().showCoverageProperty().set(false);
        getViewOptions().showImageLocationsProperty().set(true);
        getViewOptions().showRtkProperty().set(false);
        getViewOptions().showPreviewProperty().set(true);
        getViewOptions().showTrackProperty().set(true);
        getViewOptions().showAnnotationProperty().set(false);
        getViewOptions().imageProjectionProperty().set(ProjectionType.SURVEYS_2D);
        filtersEnabled.set(false);

        layer.getPicsLayer().removeAllLayers(true);
        layer.getPicsLayer().setMute(true);
        for (FlightLogEntry log : flightLogsSelected) {
            for (CPhotoLogLine line : log.getImageTriggers()) {
                try {
                    MapLayerMatch match = new MapLayerMatch(PhotoCube.EMPTY_CUBE, line, layer);
                    layer.getPicsLayer().addMapLayer(match);
                } catch (Throwable e1) {
                    Debug.getLog().log(Level.WARNING, "can't load single matching", e1);
                }
            }
        }

        layer.getPicsLayer().setMute(false);
        layer.getCoverage().updateCameraCorners();
        mapView.goToSectorAsync(getSector(), getMaxElev());
    }

    private void updateValuesFromSource() {
        areaFilters.setAll(
            legacyMatching
                .getPicAreas()
                .stream()
                .map(a -> new AreaFilter(Matching.this, a))
                .collect(Collectors.toList()));

        beanAdapter.updateValuesFromSource();
    }

    public boolean detectBestHwConfiguration() {
        try {
            if (legacyMatching.getPictures().size() == 0) {
                throw new Exception("Dataset has no images");
            }

            MapLayerMatch match = (MapLayerMatch)getPictures().get(getPictures().size() - 1);

            File[] copiedAsctecLogs =
                legacyMatching
                    .getMatchingFolder()
                    .listFiles(pathname -> pathname.getName().toLowerCase().startsWith("asctec"));

            IHardwareConfiguration bestHw =
                guessHardwareConfiguration(
                    hardwareConfigurationManager,
                    match.getCurPhotoFile().getFile(),
                    copiedAsctecLogs != null && copiedAsctecLogs.length > 0,
                    null);
            if (bestHw == null) {
                throw new Exception("no fitting Hardware found");
            }

            IGenericCameraConfiguration payload = bestHw.getPrimaryPayload(IGenericCameraConfiguration.class);

            Debug.getLog()
                .log(
                    Level.INFO,
                    "detected HW for this dataset: "
                        + bestHw.getPlatformDescription().getId()
                        + " "
                        + payload
                        + "  "
                        + payload.getLens());
            hardwareConfiguration.get().setConfigurationFrom(bestHw);
        } catch (Exception e) {
            Debug.getLog().log(Level.SEVERE, "could detect HW", e);
            return false;
        }

        return true;
    }

    public static IHardwareConfiguration guessHardwareConfiguration(
            IHardwareConfigurationManager hardwareConfigurationManager,
            File photo,
            boolean isFalcon,
            List<Flightplan> flightPlans)
            throws Exception {
        PhotoFile f = new PhotoFile(photo);
        ExifInfos exif = f.getExif();

        IHardwareConfiguration hardwareConfigBest = hardwareConfigurationManager.getImmutableDefault().deepCopy();
        int matchQualityBest = -1;
        if (flightPlans != null && !flightPlans.isEmpty()) {
            Flightplan fp = flightPlans.get(0);
            hardwareConfigBest = fp.getHardwareConfiguration().deepCopy();
            matchQualityBest = 3;
        }

        IGenericCameraConfiguration camConf = hardwareConfigBest.getPrimaryPayload(IGenericCameraConfiguration.class);
        IPlatformDescription platBest = hardwareConfigBest.getPlatformDescription();

        for (IPlatformDescription plat : hardwareConfigurationManager.getPlatforms()) {
            for (IGenericCameraDescription cam : hardwareConfigurationManager.getCameras()) {
                if (!hardwareConfigurationManager.isCompatible(plat, cam)) {
                    continue;
                }

                for (ILensDescription lens : hardwareConfigurationManager.getLenses()) {
                    if (!hardwareConfigurationManager.isCompatible(cam, lens)) {
                        continue;
                    }

                    int matchQuality = 0;

                    if (plat.isInCopterMode() == isFalcon) {
                        matchQuality++;
                    }

                    if (cam.getExifModels().contains(exif.model)) {
                        matchQuality += 3;
                    }

                    if (cam.getCcdResX() == exif.imageWidth && cam.getCcdResY() == exif.imageHeight) {
                        matchQuality++;
                    }

                    if (lens.isLensManual()) {
                        // only manual compatibleLenseIds have broken aperture
                        // all further checks are not applicable, since the data is unknown due to manual lens
                        if (exif.aperture <= 0) {
                            matchQuality++;
                        }
                    } else {
                        if (exif.aperture > 0) {
                            // negative means exif does not contain any info about this
                            matchQuality++;
                        }
                    }

                    if (exif.focalLengthMM <= 0
                            || Math.abs(
                                    lens.getFocalLength().convertTo(Unit.MILLIMETER).getValue().doubleValue()
                                            / exif.focalLengthMM
                                        - 1)
                                <= 0.05) {
                        matchQuality++;
                    }

                    if (cam.isExposureTimeFixed()) {
                        final double exposureTime1OverS =
                            cam.getOneOverExposureTime().convertTo(Unit.SECOND).getValue().doubleValue();
                        if (exif.exposureSec > 0 && Math.abs(exposureTime1OverS * exif.exposureSec - 1) <= 0.01) {
                            matchQuality++;
                        }
                    } else {
                        matchQuality++; // to get it balanced
                    }

                    if (matchQuality > matchQualityBest
                            || (matchQualityBest == matchQuality
                                && plat.getAirplaneType().isBetterThan(platBest.getAirplaneType()))) {
                        IGenericCameraConfiguration cameraConfig =
                            hardwareConfigurationManager.getCameraConfiguration(cam.getId(), lens.getId());
                        IHardwareConfiguration hardwareConfig =
                            hardwareConfigurationManager.getHardwareConfiguration(plat.getId()).deepCopy();
                        hardwareConfig.setPrimaryPayload(cameraConfig);
                        platBest = plat;
                        matchQualityBest = matchQuality;
                        hardwareConfigBest = hardwareConfig;
                    }
                }
            }
        }

        return hardwareConfigBest;
    }

    public File getMatchingFolder() {
        return legacyMatching.getMatchingFolder();
    }

    public String getFolderName() {
        return legacyMatching.getName();
    }

    public void rename(String newName) throws IOException {
        legacyMatching.rename(newName);
    }

}
