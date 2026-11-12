package com.matrixclient.ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.matrixclient.client.AuthService;
import com.matrixclient.client.MatrixHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Entry Point for the JavaFX Matrix Client.
 */
public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    
    private MatrixHttpClient httpClient;
    private AuthService authService;
    
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        logger.info("Starting MainApp...");

        // Show Login Screen initially
        showLoginScreen();
    }

    private void showLoginScreen() {
        GridPane grid = new GridPane();
        grid.setPadding(new Insets(25, 25, 25, 25));
        grid.setHgap(10);
        grid.setVgap(10);

        Label lblServer = new Label("Homeserver URL:");
        TextField txtServer = new TextField("https://matrix.org");
        
        Label lblUser = new Label("User ID:");
        TextField txtUser = new TextField("");
        
        Label lblPass = new Label("Password:");
        PasswordField txtPass = new PasswordField();
        
        Button btnLogin = new Button("Login");
        btnLogin.setOnAction(e -> handleLogin(txtServer.getText(), txtUser.getText(), txtPass.getText()));

        grid.add(lblServer, 0, 0);
        grid.add(txtServer, 1, 0);
        grid.add(lblUser, 0, 1);
        grid.add(txtUser, 1, 1);
        grid.add(lblPass, 0, 2);
        grid.add(txtPass, 1, 2);
        grid.add(btnLogin, 1, 3);

        Scene scene = new Scene(grid, 400, 300);
        primaryStage.setTitle("Matrix Login");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void handleLogin(String server, String user, String pass) {
        httpClient = new MatrixHttpClient(server);
        authService = new AuthService(httpClient);

        logger.info("Attempting login to {} for user {}", server, user);

        authService.login(server, user, pass).thenAccept(token -> {
            Platform.runLater(() -> {
                logger.info("Login success, switching to main UI");
                showMainUI();
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Login Failed");
                alert.setHeaderText(null);
                alert.setContentText(ex.getMessage());
                alert.showAndWait();
            });
            return null;
        });
    }

    private void showMainUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(10));

        Label lblStatus = new Label("Connected to Matrix");
        Button btnLogout = new Button("Logout");
        btnLogout.setOnAction(e -> handleLogout());

        Label lblRooms = new Label("Rooms (Coming Soon)");
        ListView<String> roomList = new ListView<>();
        roomList.getItems().add("Room 1 (Demo)");
        roomList.getItems().add("Room 2 (Demo)");

        root.getChildren().addAll(lblStatus, lblRooms, btnLogout);

        Scene scene = new Scene(root, 600, 400);
        primaryStage.setTitle("Matrix Desktop Client");
        primaryStage.setScene(scene);
    }

    private void handleLogout() {
        authService.logout().thenRun(() -> {
            Platform.runLater(() -> showLoginScreen());
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
