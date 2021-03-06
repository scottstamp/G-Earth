package g_earth.ui;

import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import g_earth.protocol.HConnection;
import g_earth.ui.connection.Connection;
import g_earth.ui.extensions.Extensions;
import g_earth.ui.info.Info;
import g_earth.ui.injection.Injection;
import g_earth.ui.logger.Logger;
import g_earth.ui.scheduler.Scheduler;
import g_earth.ui.settings.Settings;
import g_earth.ui.tools.Tools;

public class GEarthController {

    public Tab tab_Settings;
    public TabPane tabBar;
    private Stage stage = null;
    private volatile HConnection hConnection;

    public Connection connectionController;
    public Injection injectionController;
    public Logger loggerController;
    public Tools toolsController;
    public Scheduler schedulerController;
    public Settings settingsController;
    public Info infoController;
    public Extensions extensionsController;

    public Pane mover;

    public GEarthController() {
        hConnection = new HConnection();
    }

    public void initialize() {
        connectionController.setParentController(this);
        injectionController.setParentController(this);
        loggerController.setParentController(this);
        toolsController.setParentController(this);
        schedulerController.setParentController(this);
        settingsController.setParentController(this);
        infoController.setParentController(this);
        extensionsController.setParentController(this);

        tabBar.getTabs().remove(tab_Settings);


        //custom header bar
//        final Point[] startpos = {null};
//        final Double[] xx = {0.0};
//        final Double[] yy = {0.0};
//        final Boolean[] isMoving = {false};
//
//        mover.addEventHandler(MouseEvent.MOUSE_PRESSED,
//                event -> {
//                    startpos[0] = MouseInfo.getPointerInfo().getLocation();
//                    xx[0] = stage.getX();
//                    yy[0] = stage.getY();
//                    isMoving[0] = true;
//                });
//
//        mover.addEventHandler(MouseEvent.MOUSE_RELEASED,
//                event -> {
//                    isMoving[0] = false;
//                });
//
//
//        mover.setOnMouseDragged(event -> {
//            if (isMoving[0]) {
//                Point now = MouseInfo.getPointerInfo().getLocation();
//                double diffX = now.getX() - startpos[0].getX();
//                double diffY = now.getY() - startpos[0].getY();
//                stage.setX(xx[0] + diffX);
//                stage.setY(yy[0] + diffY);
//            }
//        });
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }
    public Stage getStage() {
        return stage;
    }

    HConnection getHConnection() {
        return hConnection;
    }
    void writeToLog(javafx.scene.paint.Color color, String text) {
        loggerController.miniLogText(color, text);
    }


    public void abort() {
        hConnection.abort();
    }

}
