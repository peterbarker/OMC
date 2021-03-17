/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol;

import com.intel.missioncontrol.concurrent.FluentFuture;
import com.intel.missioncontrol.mission.Mission;
import com.intel.missioncontrol.ui.notifications.Toast;
import java.lang.ref.WeakReference;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.ReadOnlyListProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Encapsulates application-wide state. */
public interface IApplicationContext {

    interface ICloseRequestListener {
        boolean canClose();
    }

    interface IClosingListener {
        void close();
    }

    final class WeakCloseRequestListener implements ICloseRequestListener {
        static class Accessor {
            static void setApplicationContext(
                    WeakCloseRequestListener listener, IApplicationContext applicationContext) {
                listener.applicationContext = applicationContext;
            }
        }

        private final WeakReference<ICloseRequestListener> listener;
        private IApplicationContext applicationContext;

        public WeakCloseRequestListener(ICloseRequestListener listener) {
            this.listener = new WeakReference<>(listener);
        }

        @Override
        public boolean canClose() {
            ICloseRequestListener listener = this.listener.get();
            if (listener != null) {
                return listener.canClose();
            } else if (applicationContext != null) {
                applicationContext.removeCloseRequestListener(this);
            }

            return true;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (obj == this) {
                return true;
            }

            return obj == listener.get();
        }
    }

    final class WeakClosingListener implements IClosingListener {
        static class Accessor {
            static void setApplicationContext(WeakClosingListener listener, IApplicationContext applicationContext) {
                listener.applicationContext = applicationContext;
            }
        }

        private final WeakReference<IClosingListener> listener;
        private IApplicationContext applicationContext;

        public WeakClosingListener(IClosingListener listener) {
            this.listener = new WeakReference<>(listener);
        }

        @Override
        public void close() {
            IClosingListener listener = this.listener.get();
            if (listener != null) {
                listener.close();
            } else if (applicationContext != null) {
                applicationContext.removeClosingListener(this);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null) {
                return false;
            }

            if (obj == this) {
                return true;
            }

            return obj == listener.get();
        }
    }

    void addToast(Toast toast);

    void addCloseRequestListener(ICloseRequestListener listener);

    void removeCloseRequestListener(ICloseRequestListener listener);

    void addClosingListener(IClosingListener listener);

    void removeClosingListener(IClosingListener listener);

    ReadOnlyObjectProperty<Mission> currentMissionProperty();

    @Nullable
    Mission getCurrentMission();

    boolean unloadCurrentMission();

    /**
     * if there is a open mission, ask the user if he likes to save changes and execute them
     *
     * @return true if there is no loaded mission at all or if user NOT clicked cancel
     */
    boolean askUserForMissionSave();

    boolean renameCurrentMission();

    BooleanExpression currentMissionIsNoDemo();

    FluentFuture<Void> ensureMissionAsync();

    FluentFuture<Void> loadMissionAsync(Mission mission);

    FluentFuture<Void> loadNewMissionAsync();

    FluentFuture<Void> loadClonedMissionAsync(Mission mission);

    ReadOnlyListProperty<Toast> toastsProperty();

}
