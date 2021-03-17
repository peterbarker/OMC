/**
 * Copyright (c) 2020 Intel Corporation
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.intel.missioncontrol.splashscreen;

import com.intel.missioncontrol.helper.ManifestInfo;
import com.intel.missioncontrol.diagnostics.Debugger;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinBase;
import com.sun.jna.platform.win32.WinDef;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class SplashScreen extends Application {

    private MailslotServer mailslotServer;

    @Override
    public void start(Stage primaryStage) throws IOException {
        var parameters = getParameters().getNamed();
        String pid = parameters.get("pid");
        if (pid == null || pid.isEmpty()) {
            throw new IllegalArgumentException("Required parameter not found: --pid");
        }

        String mailslot = parameters.get("mailslot");
        if (mailslot == null || mailslot.isEmpty()) {
            throw new IllegalArgumentException("Required parameter not found: --mailslot");
        }

        ProcessHandle parentProcess = ProcessHandle.of(Long.parseLong(pid)).orElse(null);
        if (parentProcess == null) {
            System.exit(0);
        }

        parentProcess.onExit().thenRun(() -> Platform.runLater(primaryStage::close));

        FXMLLoader loader = new FXMLLoader(SplashScreenView.class.getResource("SplashScreenView.fxml"));
        Region view = loader.load();
        SplashScreenView controller = loader.getController();
        controller.setVersion(ManifestInfo.getBranchName() + "_" + ManifestInfo.getCommitHash());

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        primaryStage.initStyle(StageStyle.UTILITY);
        primaryStage.setOpacity(0);
        primaryStage.setWidth(1200. / 2);
        primaryStage.setHeight(750. / 2);
        primaryStage.setX((bounds.getWidth() - primaryStage.getWidth()) / 2);
        primaryStage.setY((bounds.getHeight() - primaryStage.getHeight()) / 2);

        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.initOwner(primaryStage);
        stage.setScene(new Scene(view));
        stage.setWidth(1200. / 2);
        stage.setHeight(750. / 2);
        stage.setX((bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY((bounds.getHeight() - stage.getHeight()) / 2);

        primaryStage.setOnCloseRequest(Event::consume);
        stage.setOnCloseRequest(Event::consume);

        primaryStage.show();
        stage.show();

        mailslotServer =
            new MailslotServer(
                mailslot,
                message -> {
                    double progress = Double.parseDouble(message);
                    if (progress == 1.0) {
                        Platform.runLater(primaryStage::close);
                    } else {
                        Platform.runLater(() -> controller.setProgress(progress));
                    }
                },
                exception -> Platform.runLater(() -> controller.setVersion("err: " + exception.getMessage())));
    }

    @Override
    public void stop() throws Exception {
        mailslotServer.close();
        super.stop();
    }

    public static void show(String mailslotName) throws IOException {
        // For now, we don't show a splash screen when IMC is launched from an IDE.
        if (Debugger.isAttached()) {
            return;
        }

        Optional<String> executable = ProcessHandle.current().info().command();
        if (!executable.isPresent()) {
            throw new FileNotFoundException();
        }

        Path path = Paths.get(executable.get());
        String executableFile = path.getFileName().toString();

        final int CREATE_NO_WINDOW = 0x08000000;
        final int CREATE_UNICODE_ENVIRONMENT = 0x00000400;
        WinBase.STARTUPINFO startupInfo = new WinBase.STARTUPINFO();
        WinBase.PROCESS_INFORMATION procInfo = new WinBase.PROCESS_INFORMATION();

        if (!Kernel32.INSTANCE.CreateProcess(
                executable.get(),
                executableFile
                    + " --mainclass=com.intel.missioncontrol.splashscreen.Main --pid="
                    + ProcessHandle.current().pid()
                    + " --mailslot="
                    + mailslotName,
                null,
                null,
                false,
                new WinDef.DWORD(CREATE_UNICODE_ENVIRONMENT | CREATE_NO_WINDOW),
                null,
                null,
                startupInfo,
                procInfo)) {
            throw new RuntimeException("CreateProcess");
        }
    }
}
