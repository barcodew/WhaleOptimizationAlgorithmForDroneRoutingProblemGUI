package GUI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * DroneRoutingGUI — Modern Dashboard untuk Visualisasi
 * Whale Optimization Algorithm pada Drone Routing Problem
 *
 * Cara pakai:
 *   1. Pastikan DRP.java dan helper classes sudah ada
 *   2. Panggil DRP.runWOA() untuk dapat RunResult
 *   3. Set data di bagian DATA CONFIGURATION
 *   4. Jalankan GUI ini
 *
 * Catatan: File ini standalone — tidak bergantung pada DRP.java saat runtime.
 *          Data di-hardcode dari hasil eksperimen untuk keperluan presentasi.
 *          Untuk integrasi langsung, lihat bagian "INTEGRASI" di bawah.
 */
public class DroneRoutingGUIManualEntry extends Application {

    // ============================================================
    // DATA CONFIGURATION — Ganti dengan hasil eksperimen Anda
    // ============================================================

    // Node: {id, x, y, type} — 0=depot, 1=customer, 2=CS
    static double[][] nodes = {
        {0,  4, 40, 0},   // V0 Depot
        {1, 71,  3, 1},   // V1
        {2, 19, 88, 1},   // V2
        {3, 86, 61, 1},   // V3
        {4, 30, 73, 1},   // V4
        {5, 98, 66, 1},   // V5
        {6, 48, 54, 1},   // V6
        {7, 44, 57, 1},   // V7
        {8, 19, 59, 1},   // V8
        {9, 36, 92, 1},   // V9
        {10,38,100, 1},   // V10
        {11,87, 99, 2},   // CS1
        {12, 9, 96, 2},   // CS2
    };

    // Rute terbaik dari WOA (termasuk depot awal & akhir + CS jika ada)
    static int[] bestRoute = {0, 2, 9, 10, 4, 11,8, 7, 6, 3,12, 5, 1, 0};

    // Hasil eksperimen per skenario: {P, G, TotalJarak, TotalEnergi, JumlahCS, Fitness, Waktu}
    static double[][] hasilEksperimen = {
        {20,  200, 331.86, 4977.96, 0, 0.000201, 0.011},
        {20,  500, 354.38, 5315.71, 1, 0.000188, 0.010},
        {20, 1000, 331.86, 4977.96, 0, 0.000201, 0.016},
        {50,  200, 331.86, 4977.96, 0, 0.000201, 0.008},
        {50,  500, 310.02, 4650.25, 0, 0.000215, 0.022},
        {50, 1000, 344.69, 5170.40, 1, 0.000193, 0.038},
        {100, 200, 310.02, 4650.25, 0, 0.000215, 0.016},
        {100, 500, 310.02, 4650.25, 0, 0.000215, 0.041},
        {100,1000, 349.14, 5237.06, 1, 0.000191, 0.071},
    };

    // Konvergensi: fitness per iterasi (contoh)
    static double[] convergenceData = null; // akan di-generate dummy jika null

    // Parameter
    static double ALPHA = 20.0;
    static double E_MAX = 4000.0;
    static int NUM_CUSTOMERS = 10;

    // ============================================================
    // UI STATE
    // ============================================================
    Canvas mapCanvas;
    Canvas convergenceCanvas;
    GraphicsContext gc, gcConv;
    double canvasW = 680, canvasH = 680;
    double convW = 680, convH = 200;
    double padding = 50;
    double maxCoord = 110;

    // Animasi
    int currentSegment = 0;
    double droneScreenX, droneScreenY;
    double targetScreenX, targetScreenY;
    double animProgress = 0;
    boolean isAnimating = false;
    double animSpeed = 0.012;
    double currentBattery;
    double propellerAngle = 0;
    AnimationTimer animTimer;
    ArrayList<double[]> trailPoints = new ArrayList<>();

    // UI Controls
    Label lblStatus, lblBattery, lblSegment, lblCurrentPos;
    Label lblFitness, lblDistance, lblEnergy, lblRecharge;
    ProgressBar batteryBar;
    Button btnPlay, btnReset;
    Slider speedSlider;
    TextArea logArea;
    TabPane mainTabs;
    ComboBox<String> scenarioCombo;

    // ============================================================
    // MAIN
    // ============================================================
    @Override
    public void start(Stage stage) {

        // Generate dummy convergence jika belum diset
        if (convergenceData == null) {
            convergenceData = generateDummyConvergence(200);
        }

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0a0e17;");

        // === TOP BAR ===
        root.setTop(buildTopBar());

        // === CENTER: Tabs (Map + Tabel + Konvergensi) ===
        mainTabs = new TabPane();
        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        mainTabs.setStyle("""
            -fx-background-color: #0a0e17;
            -fx-tab-min-height: 35;
        """);

        Tab tabMap = new Tab("   MAP RUTE   ", buildMapTab());
        Tab tabTable = new Tab("   HASIL EKSPERIMEN   ", buildTableTab());
        Tab tabConv = new Tab("   KONVERGENSI   ", buildConvergenceTab());
        mainTabs.getTabs().addAll(tabMap, tabTable, tabConv);

        root.setCenter(mainTabs);

        // === RIGHT PANEL ===
        root.setRight(buildRightPanel());

        // === BOTTOM LOG ===
        root.setBottom(buildBottomLog());

        // === SETUP ANIMATION ===
        setupAnimation();

        // Initial draw
        drawMap(-1, toScreenX(nodes[0][1]), toScreenY(nodes[0][2]));

        Scene scene = new Scene(root, 1200, 900);
        scene.getStylesheets().add("data:text/css," + getCustomCSS());
        stage.setTitle("DRP — Whale Optimization Algorithm Dashboard");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();

        // Intro animation
        playIntroAnimation(root);

        log("Sistem siap. Dataset: " + NUM_CUSTOMERS + " customer, "
            + (nodes.length - NUM_CUSTOMERS - 1) + " charging station.");
        log("Rute terbaik dimuat. Tekan PLAY untuk animasi.");
    }

    // ============================================================
    // TOP BAR
    // ============================================================
    HBox buildTopBar() {
        HBox bar = new HBox();
        bar.setPadding(new Insets(14, 24, 14, 24));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setSpacing(12);
        bar.setStyle("""
            -fx-background-color: linear-gradient(to right, #0d1b2a, #1b2d4a, #0d1b2a);
            -fx-border-color: #1e3a5f;
            -fx-border-width: 0 0 1 0;
        """);

        // Logo icon
        StackPane logo = new StackPane();
        Circle logoCircle = new Circle(16);
        logoCircle.setFill(Color.web("#00b4d8"));
        logoCircle.setEffect(new Glow(0.4));
        Label logoText = new Label("D");
        logoText.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        logoText.setTextFill(Color.WHITE);
        logo.getChildren().addAll(logoCircle, logoText);

        Label title = new Label("DRONE ROUTING PROBLEM");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setTextFill(Color.web("#e0f0ff"));

        Label pipe = new Label("  |  ");
        pipe.setTextFill(Color.web("#2a4a6a"));

        Label subtitle = new Label("Whale Optimization Algorithm — Achmad Ali Akbar");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 12));
        subtitle.setTextFill(Color.web("#5a8ab5"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Live clock feel
        Label lblParams = new Label(String.format(
            "α = %.0f Wh/km  |  E_max = %.0f Wh  |  Customers = %d",
            ALPHA, E_MAX, NUM_CUSTOMERS));
        lblParams.setFont(Font.font("Consolas", 11));
        lblParams.setTextFill(Color.web("#4a7a9a"));

        bar.getChildren().addAll(logo, title, pipe, subtitle, spacer, lblParams);
        return bar;
    }

    // ============================================================
    // MAP TAB
    // ============================================================
    StackPane buildMapTab() {
        mapCanvas = new Canvas(canvasW, canvasH);
        gc = mapCanvas.getGraphicsContext2D();

        StackPane container = new StackPane(mapCanvas);
        container.setPadding(new Insets(8));
        container.setStyle("-fx-background-color: #080c14;");
        return container;
    }

    // ============================================================
    // TABLE TAB
    // ============================================================
    VBox buildTableTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color: #0a0e17;");

        Label header = new Label("HASIL EKSPERIMEN — Variasi Populasi dan Generasi");
        header.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        header.setTextFill(Color.web("#00b4d8"));

        TableView<double[]> table = new TableView<>();
        table.setStyle("""
            -fx-background-color: #0d1520;
            -fx-table-cell-border-color: #1a2a3a;
            -fx-control-inner-background: #0d1520;
            -fx-control-inner-background-alt: #111d2a;
        """);
        table.setPrefHeight(500);

        String[] colNames = {"No", "Populasi", "Generasi", "Jarak (km)",
                             "Energi (Wh)", "CS Visit", "Fitness", "Waktu (s)"};
        double[] colWidths = {50, 80, 80, 100, 100, 70, 100, 80};

        for (int c = 0; c < colNames.length; c++) {
            final int col = c;
            TableColumn<double[], String> tc = new TableColumn<>(colNames[c]);
            tc.setPrefWidth(colWidths[c]);
            tc.setStyle("-fx-alignment: CENTER; -fx-text-fill: #b0c4de;");
            tc.setCellValueFactory(param -> {
                double[] row = param.getValue();
                if (col == 0) return new javafx.beans.property.SimpleStringProperty(
                    String.valueOf((int)(java.util.Arrays.asList(hasilEksperimen).indexOf(row) + 1)));

                String val = switch (col) {
                    case 1 -> String.format("%.0f", row[0]);
                    case 2 -> String.format("%.0f", row[1]);
                    case 3 -> String.format("%.2f", row[2]);
                    case 4 -> String.format("%.2f", row[3]);
                    case 5 -> String.format("%.0f", row[4]);
                    case 6 -> String.format("%.6f", row[5]);
                    case 7 -> String.format("%.3f", row[6]);
                    default -> "";
                };
                return new javafx.beans.property.SimpleStringProperty(val);
            });
            table.getColumns().add(tc);
        }

        for (int i = 0; i < hasilEksperimen.length; i++) {
            table.getItems().add(hasilEksperimen[i]);
        }

        // Best highlight
        Label bestLabel = new Label();
        double bestFit = 0;
        int bestIdx = 0;
        for (int i = 0; i < hasilEksperimen.length; i++) {
            if (hasilEksperimen[i][5] > bestFit) {
                bestFit = hasilEksperimen[i][5];
                bestIdx = i;
            }
        }
        bestLabel.setText(String.format(
            "Solusi Terbaik: P=%.0f, G=%.0f → Jarak: %.2f km, Fitness: %.6f",
            hasilEksperimen[bestIdx][0], hasilEksperimen[bestIdx][1],
            hasilEksperimen[bestIdx][2], hasilEksperimen[bestIdx][5]));
        bestLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
        bestLabel.setTextFill(Color.web("#00e676"));
        bestLabel.setPadding(new Insets(8, 0, 0, 0));

        box.getChildren().addAll(header, table, bestLabel);
        return box;
    }

    // ============================================================
    // CONVERGENCE TAB
    // ============================================================
    StackPane buildConvergenceTab() {
        convergenceCanvas = new Canvas(canvasW, 500);
        gcConv = convergenceCanvas.getGraphicsContext2D();
        drawConvergenceChart();

        StackPane container = new StackPane(convergenceCanvas);
        container.setPadding(new Insets(16));
        container.setStyle("-fx-background-color: #0a0e17;");
        return container;
    }

    void drawConvergenceChart() {
        double w = convergenceCanvas.getWidth();
        double h = convergenceCanvas.getHeight();
        double pad = 60;

        gcConv.setFill(Color.web("#080c14"));
        gcConv.fillRect(0, 0, w, h);

        // Title
        gcConv.setFill(Color.web("#00b4d8"));
        gcConv.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        gcConv.setTextAlign(TextAlignment.CENTER);
        gcConv.fillText("GRAFIK KONVERGENSI — Fitness Terbaik per Iterasi", w / 2, 25);

        // Find min/max
        double minF = Double.MAX_VALUE, maxF = 0;
        for (double f : convergenceData) {
            if (f < minF) minF = f;
            if (f > maxF) maxF = f;
        }
        if (minF == maxF) { minF -= 0.0001; maxF += 0.0001; }

        double plotW = w - 2 * pad;
        double plotH = h - 2 * pad - 20;

        // Grid
        gcConv.setStroke(Color.web("#152030"));
        gcConv.setLineWidth(0.5);
        for (int i = 0; i <= 5; i++) {
            double y = pad + 20 + plotH * i / 5;
            gcConv.strokeLine(pad, y, pad + plotW, y);

            double val = maxF - (maxF - minF) * i / 5;
            gcConv.setFill(Color.web("#4a6a8a"));
            gcConv.setFont(Font.font("Consolas", 9));
            gcConv.setTextAlign(TextAlignment.RIGHT);
            gcConv.fillText(String.format("%.5f", val), pad - 8, y + 3);
        }

        // X axis labels
        gcConv.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i <= 5; i++) {
            double x = pad + plotW * i / 5;
            int iter = (int)(convergenceData.length * i / 5.0);
            gcConv.fillText(String.valueOf(iter), x, h - pad + 18);
        }

        // Axis labels
        gcConv.setFill(Color.web("#5a8aaa"));
        gcConv.setFont(Font.font("Segoe UI", 11));
        gcConv.setTextAlign(TextAlignment.CENTER);
        gcConv.fillText("Iterasi", w / 2, h - 10);

        // Draw line with gradient
        gcConv.setStroke(Color.web("#00b4d8"));
        gcConv.setLineWidth(2);
        gcConv.beginPath();

        for (int i = 0; i < convergenceData.length; i++) {
            double x = pad + (double) i / (convergenceData.length - 1) * plotW;
            double y = pad + 20 + plotH * (1 - (convergenceData[i] - minF) / (maxF - minF));

            if (i == 0) gcConv.moveTo(x, y);
            else gcConv.lineTo(x, y);
        }
        gcConv.stroke();

        // Fill area under curve
        gcConv.setGlobalAlpha(0.1);
        gcConv.setFill(Color.web("#00b4d8"));
        gcConv.beginPath();
        gcConv.moveTo(pad, pad + 20 + plotH);
        for (int i = 0; i < convergenceData.length; i++) {
            double x = pad + (double) i / (convergenceData.length - 1) * plotW;
            double y = pad + 20 + plotH * (1 - (convergenceData[i] - minF) / (maxF - minF));
            gcConv.lineTo(x, y);
        }
        gcConv.lineTo(pad + plotW, pad + 20 + plotH);
        gcConv.closePath();
        gcConv.fill();
        gcConv.setGlobalAlpha(1.0);

        // End point marker
        double lastX = pad + plotW;
        double lastY = pad + 20 + plotH * (1 - (convergenceData[convergenceData.length-1] - minF) / (maxF - minF));
        gcConv.setFill(Color.web("#00e676"));
        gcConv.fillOval(lastX - 5, lastY - 5, 10, 10);
        gcConv.setFont(Font.font("Consolas", FontWeight.BOLD, 11));
        gcConv.setTextAlign(TextAlignment.LEFT);
        gcConv.fillText(String.format("%.6f", convergenceData[convergenceData.length-1]),
            lastX + 8, lastY + 4);
    }

    // ============================================================
    // RIGHT PANEL
    // ============================================================
    ScrollPane buildRightPanel() {
        VBox panel = new VBox(6);
        panel.setPadding(new Insets(14));
        panel.setPrefWidth(310);
        panel.setStyle("""
            -fx-background-color: #0d1520;
            -fx-border-color: #1a2a3f;
            -fx-border-width: 0 0 0 1;
        """);

        // === HASIL OPTIMASI ===
        panel.getChildren().add(sectionTitle("HASIL OPTIMASI"));

        double bestDist = hasilEksperimen[4][2]; // ambil skenario terbaik
        double bestEn = hasilEksperimen[4][3];
        double bestFit = hasilEksperimen[4][5];

        lblFitness = statLabel("Fitness", String.format("%.6f", bestFit));
        lblDistance = statLabel("Total Jarak", String.format("%.2f km", bestDist));
        lblEnergy = statLabel("Total Energi", String.format("%.2f Wh", bestEn));
        lblRecharge = statLabel("Recharge", String.format("%.0f kali", hasilEksperimen[4][4]));

        panel.getChildren().addAll(lblFitness, lblDistance, lblEnergy, lblRecharge);
        panel.getChildren().add(separator());

        // === ANIMASI DRONE ===
        panel.getChildren().add(sectionTitle("ANIMASI DRONE"));

        lblCurrentPos = infoLabel("Posisi: V0 (Depot)");
        lblSegment = infoLabel("Segmen: 0 / " + (bestRoute.length - 1));

        lblBattery = infoLabel(String.format("Baterai: %.0f / %.0f Wh", E_MAX, E_MAX));
        batteryBar = new ProgressBar(1.0);
        batteryBar.setPrefWidth(280);
        batteryBar.setPrefHeight(14);
        batteryBar.setStyle("-fx-accent: #00e676;");

        panel.getChildren().addAll(lblCurrentPos, lblSegment, lblBattery, batteryBar);
        panel.getChildren().add(separator());

        // === KONTROL ===
        panel.getChildren().add(sectionTitle("KONTROL"));

        btnPlay = new Button("▶  PLAY");
        styleBtn(btnPlay, "#00b4d8");
        btnPlay.setOnAction(e -> {
            if (isAnimating) pauseAnimation();
            else startAnimation();
        });

        btnReset = new Button("↺  RESET");
        styleBtn(btnReset, "#e63946");
        btnReset.setOnAction(e -> resetAnimation());

        HBox btnRow = new HBox(8, btnPlay, btnReset);
        btnRow.setAlignment(Pos.CENTER);

        Label lblSpeed = infoLabel("Kecepatan Animasi:");
        speedSlider = new Slider(0.003, 0.04, 0.012);
        speedSlider.setPrefWidth(280);
        speedSlider.valueProperty().addListener((o, ov, nv) -> animSpeed = nv.doubleValue());

        panel.getChildren().addAll(btnRow, lblSpeed, speedSlider);
        panel.getChildren().add(separator());

        // === RUTE ===
        panel.getChildren().add(sectionTitle("RUTE TERBAIK"));

        StringBuilder routeStr = new StringBuilder();
        for (int i = 0; i < bestRoute.length; i++) {
            if (i > 0) routeStr.append("  →  ");
            routeStr.append(nodeLabel(bestRoute[i]));
        }
        Label lblRoute = new Label(routeStr.toString());
        lblRoute.setFont(Font.font("Consolas", 10));
        lblRoute.setTextFill(Color.web("#7ab0d0"));
        lblRoute.setWrapText(true);
        lblRoute.setPrefWidth(280);

        panel.getChildren().add(lblRoute);
        panel.getChildren().add(separator());

        // === LEGENDA ===
        panel.getChildren().add(sectionTitle("LEGENDA"));
        panel.getChildren().add(legendRow("#e67e22", "■", "Depot"));
        panel.getChildren().add(legendRow("#00b4d8", "●", "Customer"));
        panel.getChildren().add(legendRow("#00e676", "◆", "Charging Station"));
        panel.getChildren().add(legendRow("#00e5ff", "━", "Rute Aktif"));
        panel.getChildren().add(legendRow("#ff5722", "✦", "Drone"));
        panel.getChildren().add(separator());

        // Status
        lblStatus = infoLabel("Status: Siap");
        lblStatus.setTextFill(Color.web("#00e676"));
        panel.getChildren().add(lblStatus);

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setStyle("-fx-background: #0d1520; -fx-border-color: transparent;");
        return scroll;
    }

    // ============================================================
    // BOTTOM LOG
    // ============================================================
    VBox buildBottomLog() {
        VBox box = new VBox();
        box.setPadding(new Insets(4, 12, 8, 12));

        logArea = new TextArea();
        logArea.setPrefHeight(90);
        logArea.setEditable(false);
        logArea.setStyle("""
            -fx-control-inner-background: #060a12;
            -fx-text-fill: #00e676;
            -fx-font-family: 'Consolas';
            -fx-font-size: 10.5;
            -fx-border-color: #1a2a3f;
            -fx-border-width: 1 0 0 0;
        """);

        box.getChildren().add(logArea);
        return box;
    }

    // ============================================================
    // MAP DRAWING
    // ============================================================
    double toScreenX(double x) {
        return padding + (x / maxCoord) * (canvasW - 2 * padding);
    }

    double toScreenY(double y) {
        return canvasH - padding - (y / maxCoord) * (canvasH - 2 * padding);
    }

    void drawMap(int completedSegment, double droneCX, double droneCY) {
        // Background
        gc.setFill(Color.web("#080c14"));
        gc.fillRect(0, 0, canvasW, canvasH);

        // Grid
        gc.setStroke(Color.web("#111c2a"));
        gc.setLineWidth(0.5);
        gc.setFill(Color.web("#253550"));
        gc.setFont(Font.font("Consolas", 8));

        for (int i = 0; i <= 100; i += 10) {
            double sx = toScreenX(i);
            double sy = toScreenY(i);
            gc.strokeLine(sx, padding, sx, canvasH - padding);
            gc.strokeLine(padding, sy, canvasW - padding, sy);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf(i), sx, canvasH - padding + 14);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(String.valueOf(i), padding - 6, sy + 3);
        }

        // Completed route segments (glowing cyan)
        for (int i = 0; i < completedSegment && i < bestRoute.length - 1; i++) {
            double x1 = toScreenX(nodes[bestRoute[i]][1]);
            double y1 = toScreenY(nodes[bestRoute[i]][2]);
            double x2 = toScreenX(nodes[bestRoute[i + 1]][1]);
            double y2 = toScreenY(nodes[bestRoute[i + 1]][2]);

            // Glow effect
            gc.setStroke(Color.web("#00e5ff", 0.15));
            gc.setLineWidth(8);
            gc.strokeLine(x1, y1, x2, y2);

            // Main line
            gc.setStroke(Color.web("#00e5ff", 0.85));
            gc.setLineWidth(2.5);
            gc.strokeLine(x1, y1, x2, y2);

            drawArrowMid(x1, y1, x2, y2);
        }

        // Current segment (partial)
        if (completedSegment >= 0 && completedSegment < bestRoute.length - 1) {
            double x1 = toScreenX(nodes[bestRoute[completedSegment]][1]);
            double y1 = toScreenY(nodes[bestRoute[completedSegment]][2]);

            gc.setStroke(Color.web("#00e5ff", 0.6));
            gc.setLineWidth(2);
            gc.strokeLine(x1, y1, droneCX, droneCY);
        }

        // Future route (dimmed dotted)
        gc.setLineDashes(6, 6);
        gc.setStroke(Color.web("#1a3050", 0.5));
        gc.setLineWidth(1);
        int startSeg = Math.max(completedSegment + 1, 0);
        for (int i = startSeg; i < bestRoute.length - 1; i++) {
            double x1 = toScreenX(nodes[bestRoute[i]][1]);
            double y1 = toScreenY(nodes[bestRoute[i]][2]);
            double x2 = toScreenX(nodes[bestRoute[i + 1]][1]);
            double y2 = toScreenY(nodes[bestRoute[i + 1]][2]);
            gc.strokeLine(x1, y1, x2, y2);
        }
        gc.setLineDashes(null);

        // Draw nodes
        for (double[] node : nodes) {
            double sx = toScreenX(node[1]);
            double sy = toScreenY(node[2]);
            int type = (int) node[3];
            int id = (int) node[0];

            if (type == 0) drawDepot(sx, sy);
            else if (type == 2) drawCS(sx, sy, id);
            else drawCustomer(sx, sy, id);
        }

        // Step counter on map
        if (completedSegment >= 0) {
            gc.setFill(Color.web("#0a1020", 0.85));
            gc.fillRoundRect(canvasW - 180, 10, 170, 32, 8, 8);
            gc.setFill(Color.web("#00e5ff"));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.format("Step %d / %d", completedSegment, bestRoute.length - 1),
                canvasW - 95, 31);
        }
    }

    void drawDepot(double x, double y) {
        double s = 22;

        // Glow
        gc.setFill(Color.web("#e67e22", 0.12));
        gc.fillOval(x - 18, y - 18, 36, 36);

        // Square
        gc.setFill(Color.web("#e67e22"));
        gc.fillRoundRect(x - s/2, y - s/2, s, s, 4, 4);
        gc.setStroke(Color.web("#f5a623"));
        gc.setLineWidth(2);
        gc.strokeRoundRect(x - s/2, y - s/2, s, s, 4, 4);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("D", x, y + 5);
    }

    void drawCustomer(double x, double y, int id) {
        double r = 8;

        // Check if visited
        boolean visited = false;
        for (int i = 0; i <= currentSegment && i < bestRoute.length; i++) {
            if (bestRoute[i] == id) { visited = true; break; }
        }

        String color = visited ? "#00b4d8" : "#2a5a7a";

        gc.setFill(Color.web(color));
        gc.fillOval(x - r, y - r, r * 2, r * 2);
        gc.setStroke(Color.web(visited ? "#0090b0" : "#1a3a5a"));
        gc.setLineWidth(1.5);
        gc.strokeOval(x - r, y - r, r * 2, r * 2);

        gc.setFill(Color.web(visited ? "#a0dff0" : "#4a6a80"));
        gc.setFont(Font.font("Consolas", 8));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("" + id, x + r + 3, y + 3);
    }

    void drawCS(double x, double y, int id) {
        double s = 12;

        // Glow
        gc.setFill(Color.web("#00e676", 0.1));
        gc.fillOval(x - 15, y - 15, 30, 30);

        // Diamond
        gc.save();
        gc.translate(x, y);
        gc.rotate(45);
        gc.setFill(Color.web("#00e676"));
        gc.fillRoundRect(-s/2, -s/2, s, s, 2, 2);
        gc.setStroke(Color.web("#00c853"));
        gc.setLineWidth(1.5);
        gc.strokeRoundRect(-s/2, -s/2, s, s, 2, 2);
        gc.restore();

        // Lightning icon
        gc.setFill(Color.web("#ffd600"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("⚡", x, y - 13);

        gc.setFill(Color.web("#00e676"));
        gc.setFont(Font.font("Consolas", 8));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("CS" + (id - NUM_CUSTOMERS), x + 12, y + 3);
    }

    void drawArrowMid(double x1, double y1, double x2, double y2) {
        double mx = (x1 + x2) / 2;
        double my = (y1 + y2) / 2;
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double len = 7;

        gc.setFill(Color.web("#00e5ff", 0.7));
        double ax1 = mx - len * Math.cos(angle - Math.PI / 6);
        double ay1 = my - len * Math.sin(angle - Math.PI / 6);
        double ax2 = mx - len * Math.cos(angle + Math.PI / 6);
        double ay2 = my - len * Math.sin(angle + Math.PI / 6);

        gc.fillPolygon(new double[]{mx, ax1, ax2}, new double[]{my, ay1, ay2}, 3);
    }

    void drawDrone(double x, double y) {
        double armLen = 16;

        gc.save();
        gc.translate(x, y);

        // Glow
        gc.setFill(Color.web("#ff5722", 0.12));
        gc.fillOval(-24, -24, 48, 48);

        // Arms
        gc.setStroke(Color.web("#b0b0b0"));
        gc.setLineWidth(2);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90);
            double ex = Math.cos(a) * armLen;
            double ey = Math.sin(a) * armLen;

            gc.strokeLine(0, 0, ex, ey);

            // Propellers
            gc.save();
            gc.translate(ex, ey);
            gc.rotate(propellerAngle + i * 90);
            gc.setStroke(Color.web("#ff5722", 0.7));
            gc.setLineWidth(2.5);
            gc.strokeLine(-8, 0, 8, 0);
            gc.restore();

            // Motor dots
            gc.setFill(Color.web("#424242"));
            gc.fillOval(ex - 3, ey - 3, 6, 6);
        }

        // Body
        gc.setFill(Color.web("#ff5722"));
        gc.fillOval(-6, -6, 12, 12);
        gc.setStroke(Color.web("#e64a19"));
        gc.setLineWidth(1.5);
        gc.strokeOval(-6, -6, 12, 12);

        // Center dot
        gc.setFill(Color.WHITE);
        gc.fillOval(-2.5, -2.5, 5, 5);

        gc.restore();
    }

    // ============================================================
    // ANIMATION
    // ============================================================
    void setupAnimation() {
        currentBattery = E_MAX;

        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isAnimating) return;

                animProgress += animSpeed;
                propellerAngle += 15;

                if (animProgress >= 1.0) {
                    animProgress = 0;
                    onSegmentComplete();
                    currentSegment++;

                    if (currentSegment >= bestRoute.length - 1) {
                        isAnimating = false;
                        lblStatus.setText("Status: Animasi selesai!");
                        lblStatus.setTextFill(Color.web("#00e676"));
                        btnPlay.setText("▶  PLAY");
                        log("Animasi selesai. Drone kembali ke depot.");
                        drawMap(bestRoute.length - 1,
                            toScreenX(nodes[0][1]), toScreenY(nodes[0][2]));
                        return;
                    }

                    setupNextSegment();
                }

                double cx = droneScreenX + (targetScreenX - droneScreenX) * animProgress;
                double cy = droneScreenY + (targetScreenY - droneScreenY) * animProgress;

                // Battery interpolation
                int from = bestRoute[currentSegment];
                int to = bestRoute[currentSegment + 1];
                double segDist = dist(nodes[from], nodes[to]);
                double segEnergy = segDist * ALPHA;
                double displayBat = currentBattery - segEnergy * animProgress;
                updateBatteryUI(Math.max(0, displayBat));

                drawMap(currentSegment, cx, cy);
                drawDrone(cx, cy);
            }
        };
    }

    void startAnimation() {
        if (currentSegment >= bestRoute.length - 1) resetAnimation();

        isAnimating = true;
        btnPlay.setText("⏸  PAUSE");
        lblStatus.setText("Status: Animasi berjalan...");
        lblStatus.setTextFill(Color.web("#ffd600"));

        if (currentSegment == 0) {
            currentBattery = E_MAX;
            trailPoints.clear();
            log("Drone lepas landas dari depot!");
        }

        setupNextSegment();
        animTimer.start();
    }

    void pauseAnimation() {
        isAnimating = false;
        btnPlay.setText("▶  PLAY");
        lblStatus.setText("Status: Dijeda");
        lblStatus.setTextFill(Color.web("#ffd600"));
    }

    void resetAnimation() {
        isAnimating = false;
        animTimer.stop();
        currentSegment = 0;
        animProgress = 0;
        currentBattery = E_MAX;
        trailPoints.clear();

        lblCurrentPos.setText("Posisi: V0 (Depot)");
        lblSegment.setText("Segmen: 0 / " + (bestRoute.length - 1));
        updateBatteryUI(E_MAX);
        btnPlay.setText("▶  PLAY");
        lblStatus.setText("Status: Siap");
        lblStatus.setTextFill(Color.web("#00e676"));
        log("Reset animasi.");

        drawMap(-1, toScreenX(nodes[0][1]), toScreenY(nodes[0][2]));
    }

    void setupNextSegment() {
        int from = bestRoute[currentSegment];
        int to = bestRoute[currentSegment + 1];

        droneScreenX = toScreenX(nodes[from][1]);
        droneScreenY = toScreenY(nodes[from][2]);
        targetScreenX = toScreenX(nodes[to][1]);
        targetScreenY = toScreenY(nodes[to][2]);

        lblCurrentPos.setText("Posisi: " + nodeLabel(from) + " → " + nodeLabel(to));
        lblSegment.setText("Segmen: " + (currentSegment + 1) + " / " + (bestRoute.length - 1));
    }

    void onSegmentComplete() {
        int arrivedNode = bestRoute[currentSegment + 1];
        double segDist = dist(nodes[bestRoute[currentSegment]], nodes[arrivedNode]);
        double segEnergy = segDist * ALPHA;
        currentBattery -= segEnergy;

        if (isCS(arrivedNode)) {
            log(String.format("⚡ Charging di %s | Baterai: %.1f → %.0f Wh",
                nodeLabel(arrivedNode), currentBattery, E_MAX));
            currentBattery = E_MAX;
        } else if (arrivedNode == 0) {
            log(String.format("Tiba di Depot | Sisa: %.1f Wh", currentBattery));
        } else {
            log(String.format("→ %s | Jarak: %.2f km | Sisa: %.1f Wh",
                nodeLabel(arrivedNode), segDist, currentBattery));
        }

        updateBatteryUI(currentBattery);
    }

    void updateBatteryUI(double bat) {
        double pct = bat / E_MAX;
        batteryBar.setProgress(pct);
        lblBattery.setText(String.format("Baterai: %.0f / %.0f Wh", bat, E_MAX));

        if (pct > 0.5) batteryBar.setStyle("-fx-accent: #00e676;");
        else if (pct > 0.25) batteryBar.setStyle("-fx-accent: #ffd600;");
        else batteryBar.setStyle("-fx-accent: #e63946;");
    }

    // ============================================================
    // UI HELPERS
    // ============================================================
    Label sectionTitle(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        lbl.setTextFill(Color.web("#00b4d8"));
        lbl.setPadding(new Insets(8, 0, 4, 0));
        return lbl;
    }

    Label statLabel(String title, String value) {
        Label lbl = new Label(title + ":  " + value);
        lbl.setFont(Font.font("Consolas", 11));
        lbl.setTextFill(Color.web("#c0d8e8"));
        lbl.setPadding(new Insets(2, 0, 2, 8));
        return lbl;
    }

    Label infoLabel(String text) {
        Label lbl = new Label(text);
        lbl.setFont(Font.font("Consolas", 10.5));
        lbl.setTextFill(Color.web("#90aac0"));
        return lbl;
    }

    Separator separator() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color: #1a2a3f;");
        return sep;
    }

    void styleBtn(Button btn, String color) {
        btn.setPrefWidth(132);
        btn.setPrefHeight(36);
        btn.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        btn.setTextFill(Color.WHITE);
        btn.setStyle(String.format("""
            -fx-background-color: %s;
            -fx-background-radius: 6;
            -fx-cursor: hand;
        """, color));

        btn.setOnMouseEntered(e -> btn.setOpacity(0.85));
        btn.setOnMouseExited(e -> btn.setOpacity(1.0));
    }

    HBox legendRow(String color, String symbol, String text) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(1, 0, 1, 8));

        Label sym = new Label(symbol);
        sym.setFont(Font.font("Segoe UI", 13));
        sym.setTextFill(Color.web(color));

        Label lbl = new Label(text);
        lbl.setFont(Font.font("Segoe UI", 11));
        lbl.setTextFill(Color.web("#6a8aa0"));

        row.getChildren().addAll(sym, lbl);
        return row;
    }

    String nodeLabel(int id) {
        if (id == 0) return "V0(Depot)";
        if (id > NUM_CUSTOMERS) return "CS" + (id - NUM_CUSTOMERS);
        return "V" + id;
    }

    boolean isCS(int id) {
        return id > NUM_CUSTOMERS;
    }

    double dist(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }

    void log(String msg) {
        Platform.runLater(() -> {
            logArea.appendText(">> " + msg + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    void playIntroAnimation(BorderPane root) {
        root.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(800), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    double[] generateDummyConvergence(int iterations) {
        double[] data = new double[iterations];
        double current = 0.000100;
        double target = 0.000215;

        for (int i = 0; i < iterations; i++) {
            double progress = (double) i / iterations;
            // Logarithmic convergence curve
            current = 0.000100 + (target - 0.000100) * (1 - Math.exp(-4 * progress));
            // Add small noise
            current += (Math.random() - 0.5) * 0.000003;
            data[i] = Math.max(data[i > 0 ? i - 1 : 0], current); // monotonic non-decreasing
        }
        return data;
    }

    String getCustomCSS() {
        return String.join("",
            ".tab-pane .tab-header-background { -fx-background-color: %230d1520; }",
            ".tab { -fx-background-color: %231a2a40; -fx-border-color: %231a2a40; }",
            ".tab:selected { -fx-background-color: %2300b4d8; }",
            ".tab .tab-label { -fx-text-fill: %2390aac0; -fx-font-family: 'Segoe UI'; -fx-font-weight: bold; -fx-font-size: 11; }",
            ".tab:selected .tab-label { -fx-text-fill: white; }",
            ".scroll-bar { -fx-background-color: %230d1520; }",
            ".scroll-bar .thumb { -fx-background-color: %231a3050; -fx-background-radius: 4; }",
            ".scroll-bar .increment-button, .scroll-bar .decrement-button { -fx-background-color: transparent; -fx-padding: 0; }",
            ".scroll-bar .increment-arrow, .scroll-bar .decrement-arrow { -fx-shape: ''; -fx-padding: 0; }",
            ".progress-bar .track { -fx-background-color: %231a2a3f; -fx-background-radius: 4; }",
            ".progress-bar .bar { -fx-background-radius: 4; -fx-padding: 2; }",
            ".slider .track { -fx-background-color: %231a2a3f; }",
            ".slider .thumb { -fx-background-color: %2300b4d8; }",
            ".table-view .column-header { -fx-background-color: %231a2a40; }",
            ".table-view .column-header .label { -fx-text-fill: %2300b4d8; -fx-font-weight: bold; }",
            ".table-row-cell { -fx-background-color: %230d1520; -fx-text-fill: %23b0c4de; }",
            ".table-row-cell:odd { -fx-background-color: %23111d2a; }",
            ".table-row-cell:selected { -fx-background-color: %231a3a5f; }"
        );
    }

    public static void main(String[] args) {
        launch(args);
    }
}