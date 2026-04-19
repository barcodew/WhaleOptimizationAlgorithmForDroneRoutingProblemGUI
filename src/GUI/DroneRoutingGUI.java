package GUI;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.Glow;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.animation.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.util.Duration;
import java.io.*;
import java.util.*;

public class DroneRoutingGUI extends Application {
    Label routeLabel;
    ArrayList<File> datasetFiles = new ArrayList<>();
    ComboBox<String> datasetCombo;
    ArrayList<double[]> nodes = new ArrayList<>();
    int numCustomers = 0, numCS = 0;
    ArrayList<String[]> ringkasanData = new ArrayList<>();
    String[] ringkasanHeader;
    ArrayList<double[]> konvergensiData = new ArrayList<>();
    String[] konvergensiHeader;
    ArrayList<String[]> ruteTerbaikData = new ArrayList<>();
    int[] bestRoute;
    double ALPHA = 20.0, E_MAX = 4000.0;
    Canvas mapCanvas, convCanvas;
    GraphicsContext gc, gcConv;
    double canvasW = 680, canvasH = 680, padding = 50, maxCoord = 110;
    int currentSegment = 0;
    double droneX, droneY;
    double targetX, targetY;
    double animProgress = 0, animSpeed = 0.012, currentBattery, propellerAngle = 0;
    boolean isAnimating = false, dataLoaded = false;
    AnimationTimer animTimer;
    Label lblStatus, lblBattery, lblSegment, lblCurrentPos;
    Label lblFitness, lblDistance, lblEnergy, lblRecharge, lblDatasetInfo;
    ProgressBar batteryBar;
    Button btnPlay, btnReset;
    Slider speedSlider;
    TextArea logArea;
    TabPane mainTabs;
    ComboBox<String> routeCombo;
    double segmentStartBattery;
    double zoom = 1.0;
    double offsetX = 0;
    double offsetY = 0;
    double lastMouseX, lastMouseY;
    double segmentEnergy;
    
    @Override
    public void start(Stage stage) {

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #0a0e17;");
        root.setTop(buildTopBar(stage));
        mainTabs = new TabPane();
        mainTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        mainTabs.setStyle("-fx-background-color: #0a0e17;");
        mapCanvas = new Canvas(canvasW, canvasH);
        gc = mapCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#080c14"));
        gc.fillRect(0, 0, canvasW, canvasH);
        gc.setFill(Color.web("#2a4a6a"));
        gc.setFont(Font.font("Segoe UI", 14));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Muat dataset untuk melihat peta", canvasW / 2, canvasH / 2);
        StackPane mapC = new StackPane(mapCanvas);
        mapC.setPadding(new Insets(8));
        mapC.setStyle("-fx-background-color: #080c14;");
        Tab t1 = new Tab("   MAP RUTE   ", mapC);
        Tab t2 = new Tab("   HASIL EKSPERIMEN   ", emptyTab("Muat data dulu"));
        Tab t3 = new Tab("   KONVERGENSI   ", emptyTab("Muat data dulu"));
        mainTabs.getTabs().addAll(t1, t2, t3);
        root.setCenter(mainTabs);
        root.setRight(buildRightPanel(stage));
        VBox btm = new VBox();
        btm.setPadding(new Insets(4, 12, 8, 12));
        logArea = new TextArea();
        logArea.setPrefHeight(90);
        logArea.setEditable(false);
        logArea.setStyle(
                "-fx-control-inner-background:#060a12;-fx-text-fill:#00e676;-fx-font-family:'Consolas';-fx-font-size:10.5;-fx-border-color:#1a2a3f;-fx-border-width:1 0 0 0;");
        btm.getChildren().add(logArea);
        root.setBottom(btm);
        setupAnimation();
        Scene scene = new Scene(root, 1220, 920);
        scene.getStylesheets().add("data:text/css," + getCSS());
        stage.setTitle("DRP Dashboard — Whale Optimization Algorithm");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
        FadeTransition fade = new FadeTransition(Duration.millis(600), root);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
        log("Sistem siap. Klik 'Muat Dataset' lalu 'Muat Hasil Eksperimen'.");
        mapCanvas.setOnScroll(e -> {
            double zoomFactor = (e.getDeltaY() > 0) ? 1.1 : 0.9;

            double mouseX = e.getX();
            double mouseY = e.getY();

            // sebelum zoom
            double beforeZoomX = (mouseX - offsetX) / zoom;
            double beforeZoomY = (mouseY - offsetY) / zoom;

            zoom *= zoomFactor;
            zoom = Math.max(0.5, Math.min(zoom, 5.0));

            // setelah zoom → adjust offset supaya titik tetap di mouse
            offsetX = mouseX - beforeZoomX * zoom;
            offsetY = mouseY - beforeZoomY * zoom;

            drawMap(currentSegment, toScreenX(droneX), toScreenY(droneY));
        });
        mapCanvas.setOnMousePressed(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });

        mapCanvas.setOnMouseDragged(e -> {
            double dx = e.getX() - lastMouseX;
            double dy = e.getY() - lastMouseY;

            offsetX += dx;
            offsetY += dy;

            lastMouseX = e.getX();
            lastMouseY = e.getY();

            drawMap(currentSegment, toScreenX(droneX), toScreenY(droneY));
        });

        loadDatasetFromFolder("E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\DatasetGUI");
        loadResults("E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Result\\WOA");
        rebuildUI();

    }

    void loadDatasetFromFolder(String folderPath) {
        File folder = new File(folderPath);
        File[] files = folder.listFiles();

        if (files == null)
            return;

        datasetFiles.clear();
        datasetCombo.getItems().clear(); 

        for (File f : files) {
            if (f.getName().toLowerCase().endsWith(".csv")) {
                datasetFiles.add(f);
                datasetCombo.getItems().add(f.getName()); 
            }
        }

        if (!datasetFiles.isEmpty()) {
            datasetCombo.getSelectionModel().select(0); //  pilih default

            File f = datasetFiles.get(0);
            loadDataset(f.getAbsolutePath());

            log("Auto load dataset: " + f.getName());
        }
    }

    boolean loadDataset(String path) {
        nodes.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String first = br.readLine().trim();
            String[] fp = first.split(",");
            if (fp.length == 2 && isNum(fp[0])) {
                String second = br.readLine().trim();
                String[] sp = second.split(",");
                if (sp.length == 2 && isNum(sp[0])) {
                    numCustomers = Integer.parseInt(sp[0].trim());
                    numCS = Integer.parseInt(sp[1].trim());
                    String h = br.readLine().trim();
                    if (h.toLowerCase().contains("id"))
                        h = br.readLine().trim();
                    String line = h;
                    do {
                        if (line.trim().isEmpty())
                            continue;
                        String[] p = line.split(",");
                        if (p.length >= 4) {
                            int id = Integer.parseInt(p[0].trim());
                            double x = Double.parseDouble(p[1].trim());
                            double y = Double.parseDouble(p[2].trim());
                            String ty = p[3].trim().toUpperCase();
                            int t = ty.contains("DEPOT") ? 0 : ty.contains("CHARG") ? 2 : 1;
                            nodes.add(new double[] { id, x, y, t });
                        }
                    } while ((line = br.readLine()) != null);
                }
            } else if (first.toLowerCase().contains("step") || first.toLowerCase().contains("node")) {
                String line;
                Set<Integer> seen = new HashSet<>();
                while ((line = br.readLine()) != null) {
                    if (line.trim().isEmpty())
                        continue;
                    String[] p = line.split(",");
                    if (p.length >= 5) {
                        int id = Integer.parseInt(p[1].trim());
                        if (seen.contains(id))
                            continue;
                        seen.add(id);
                        String ty = p[2].trim().toUpperCase();
                        double x = Double.parseDouble(p[3].trim());
                        double y = Double.parseDouble(p[4].trim());
                        int t = ty.contains("DEPOT") ? 0 : ty.contains("CHARG") ? 2 : 1;
                        nodes.add(new double[] { id, x, y, t });
                    }
                }
                numCustomers = (int) nodes.stream().filter(n -> n[3] == 1).count();
                numCS = (int) nodes.stream().filter(n -> n[3] == 2).count();
            }
            nodes.sort(Comparator.comparingDouble(a -> a[0]));
            maxCoord = 0;
            for (double[] n : nodes)
                maxCoord = Math.max(maxCoord, Math.max(n[1], n[2]));
            maxCoord += 10;
            return nodes.size() > 0;
        } catch (Exception e) {
            log("Error: " + e.getMessage());
            return false;
        }
    }

    boolean loadResults(String folder) {
        File[] files = new File(folder).listFiles();
        if (files == null)
            return false;
        File rf = null, kf = null, rtf = null;
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (n.endsWith("_ringkasan.csv") && (rf == null || f.lastModified() > rf.lastModified()))
                rf = f;
            if (n.endsWith("_konvergensi.csv") && (kf == null || f.lastModified() > kf.lastModified()))
                kf = f;
            if (n.endsWith("_rute_terbaik.csv") && (rtf == null || f.lastModified() > rtf.lastModified()))
                rtf = f;
        }
        if (rf != null) {
            ringkasanData.clear();
            try (BufferedReader br = new BufferedReader(new FileReader(rf))) {
                ringkasanHeader = br.readLine().split(",");
                String l;
                while ((l = br.readLine()) != null)
                    if (!l.trim().isEmpty())
                        ringkasanData.add(l.split(","));
                log("Ringkasan: " + rf.getName() + " (" + ringkasanData.size() + " skenario)");
            } catch (Exception e) {
                log("Error ringkasan: " + e.getMessage());
            }
        }
        if (kf != null) {
            konvergensiData.clear();
            try (BufferedReader br = new BufferedReader(new FileReader(kf))) {
                konvergensiHeader = br.readLine().split(",");
                String l;
                while ((l = br.readLine()) != null) {
                    if (l.trim().isEmpty())
                        continue;
                    String[] p = l.split(",");
                    double[] row = new double[p.length];
                    for (int i = 0; i < p.length; i++)
                        try {
                            row[i] = Double.parseDouble(p[i].trim());
                        } catch (Exception e) {
                            row[i] = 0;
                        }
                    konvergensiData.add(row);
                }
                log("Konvergensi: " + kf.getName() + " (" + konvergensiData.size() + " iterasi)");
            } catch (Exception e) {
                log("Error konvergensi: " + e.getMessage());
            }
        }
        if (rtf != null) {
            ruteTerbaikData.clear();
            try (BufferedReader br = new BufferedReader(new FileReader(rtf))) {
                br.readLine();
                String l;
                while ((l = br.readLine()) != null) {
                    if (l.trim().isEmpty())
                        continue;
                    ruteTerbaikData.add(smartSplit(l));
                }
                log("Rute: " + rtf.getName() + " (" + ruteTerbaikData.size() + " skenario)");
            } catch (Exception e) {
                log("Error rute: " + e.getMessage());
            }
        }
        if (!ruteTerbaikData.isEmpty())
            selectRoute(0);
        return !ruteTerbaikData.isEmpty() || !ringkasanData.isEmpty();
    }

    String[] smartSplit(String line) {
        ArrayList<String> parts = new ArrayList<>();
        boolean inQ = false;
        StringBuilder sb = new StringBuilder();
        for (char c : line.toCharArray()) {
            if (c == '"')
                inQ = !inQ;
            else if (c == ',' && !inQ) {
                parts.add(sb.toString().trim());
                sb = new StringBuilder();
            } else
                sb.append(c);
        }
        parts.add(sb.toString().trim());
        return parts.toArray(new String[0]);
    }

    int[] parseRoute(String s) {
        String[] parts = s.split("\\s*->\\s*");
        ArrayList<Integer> ids = new ArrayList<>();
        for (String p : parts) {
            p = p.trim();
            if (p.isEmpty())
                continue;
            if (p.startsWith("V0") || p.toLowerCase().contains("depot"))
                ids.add(0);
            else if (p.startsWith("CS")) {
                String num = p.replace("CS", "").replaceAll("[^0-9]", "");
                ids.add(numCustomers + Integer.parseInt(num));
            } else if (p.startsWith("V")) {
                String num = p.replace("V", "").replaceAll("[^0-9]", "");
                ids.add(Integer.parseInt(num));
            }
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    void selectRoute(int idx) {
        if (idx >= 0 && idx < ruteTerbaikData.size()) {
            String[] row = ruteTerbaikData.get(idx);
            bestRoute = parseRoute(row[row.length - 1]);
        }
    }

    void rebuildUI() {
        zoom = 1.0;
        offsetX = 0;
        offsetY = 0;
        if (nodes.isEmpty())
            return;
        lblDatasetInfo.setText(String.format("%d Customer | %d CS | %d Node", numCustomers, numCS, nodes.size()));
        drawMap(-1, toScreenX(nodes.get(0)[1]), toScreenY(nodes.get(0)[2]));
        if (!ringkasanData.isEmpty())
            mainTabs.getTabs().set(1, new Tab("   HASIL EKSPERIMEN   ", buildTableTab()));
        if (!konvergensiData.isEmpty()) {
            convCanvas = new Canvas(canvasW, 500);
            gcConv = convCanvas.getGraphicsContext2D();
            drawConvChart();
            StackPane cc = new StackPane(convCanvas);
            cc.setPadding(new Insets(16));
            cc.setStyle("-fx-background-color:#0a0e17;");
            mainTabs.getTabs().set(2, new Tab("   KONVERGENSI   ", cc));
        }
        if (!ruteTerbaikData.isEmpty()) {
            routeCombo.getItems().clear();
            for (int i = 0; i < ruteTerbaikData.size(); i++) {
                String[] r = ruteTerbaikData.get(i);
                routeCombo.getItems().add(String.format("Customer =%s Populasi=%s G=%s (%.2f km)", r[1], r[2], r[3],
                        Double.parseDouble(r[5])));
            }
            routeCombo.getSelectionModel().select(0);
            double bf = 0;
            int bi = 0;
            for (int i = 0; i < ruteTerbaikData.size(); i++) {
                double f = Double.parseDouble(ruteTerbaikData.get(i)[4]);
                if (f > bf) {
                    bf = f;
                    bi = i;
                }
            }
            String[] br = ruteTerbaikData.get(bi);
            if (routeLabel != null) {
                routeLabel.setText("Rute: " + formatRoute(br[br.length - 1]));
            }
            lblFitness.setText("Fitness:  " + br[4]);
            lblDistance.setText("Jarak:    " + br[5] + " km");
            lblEnergy.setText("Energi:   " + br[6] + " Wh");
            lblRecharge.setText("Recharge: " + br[7] + " kali");
            selectRoute(bi);
            routeCombo.getSelectionModel().select(bi);
        }
        if (bestRoute != null)
            resetAnimation();
        dataLoaded = true;
        log("Data dimuat. Siap animasi.");
    }

    HBox buildTopBar(Stage stage) {
        HBox bar = new HBox();
        bar.setPadding(new Insets(14, 24, 14, 24));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setSpacing(12);
        bar.setStyle(
                "-fx-background-color:linear-gradient(to right,#0d1b2a,#1b2d4a,#0d1b2a);-fx-border-color:#1e3a5f;-fx-border-width:0 0 1 0;");
        StackPane logo = new StackPane();
        Circle c = new Circle(16);
        c.setFill(Color.web("#00b4d8"));
        c.setEffect(new Glow(0.4));
        Label lt = new Label("D");
        lt.setFont(Font.font("Consolas", FontWeight.BOLD, 16));
        lt.setTextFill(Color.WHITE);
        logo.getChildren().addAll(c, lt);
        Label title = new Label("DRONE ROUTING PROBLEM");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setTextFill(Color.web("#e0f0ff"));
        Label pipe = new Label("  |  ");
        pipe.setTextFill(Color.web("#2a4a6a"));
        Label sub = new Label("Whale Optimization Algorithm");
        sub.setFont(Font.font("Segoe UI", 12));
        sub.setTextFill(Color.web("#5a8ab5"));
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        lblDatasetInfo = new Label("Belum ada data");
        lblDatasetInfo.setFont(Font.font("Consolas", 11));
        lblDatasetInfo.setTextFill(Color.web("#4a7a9a"));
        bar.getChildren().addAll(logo, title, pipe, sub, sp, lblDatasetInfo);
        return bar;
    }

    VBox buildTableTab() {
        VBox box = new VBox(12);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color:#0a0e17;");
        Label h = new Label("HASIL EKSPERIMEN");
        h.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        h.setTextFill(Color.web("#00b4d8"));
        TableView<String[]> tv = new TableView<>();
        tv.setPrefHeight(500);
        tv.setStyle(
                "-fx-background-color:#0d1520;-fx-table-cell-border-color:#1a2a3a;-fx-control-inner-background:#0d1520;-fx-control-inner-background-alt:#111d2a;");
        if (ringkasanHeader != null)
            for (int ci = 0; ci < ringkasanHeader.length; ci++) {
                final int col = ci;
                TableColumn<String[], String> tc = new TableColumn<>(ringkasanHeader[ci].trim());
                tc.setPrefWidth(col <= 1 ? 60 : 90);
                tc.setStyle("-fx-alignment:CENTER;");
                tc.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(
                        col < p.getValue().length ? p.getValue()[col].trim() : ""));
                tv.getColumns().add(tc);
            }
        for (String[] r : ringkasanData)
            tv.getItems().add(r);
        box.getChildren().addAll(h, tv);
        return box;
    }

    void drawConvChart() {
        if (konvergensiData.isEmpty())
            return;
        double w = convCanvas.getWidth(), h = convCanvas.getHeight(), pad = 60;
        gcConv.setFill(Color.web("#080c14"));
        gcConv.fillRect(0, 0, w, h);
        gcConv.setFill(Color.web("#00b4d8"));
        gcConv.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        gcConv.setTextAlign(TextAlignment.CENTER);
        gcConv.fillText("GRAFIK KONVERGENSI", w / 2, 25);
        int nc = konvergensiData.get(0).length;
        String[] colors = { "#00b4d8", "#00e676", "#ff6b6b", "#ffd93d", "#c084fc", "#fb923c", "#22d3ee", "#a3e635",
                "#f472b6" };
        double pW = w - 2 * pad, pH = h - 2 * pad - 40, minF = Double.MAX_VALUE, maxF = 0;
        for (double[] r : konvergensiData)
            for (int s = 1; s < r.length; s++)
                if (r[s] > 0) {
                    minF = Math.min(minF, r[s]);
                    maxF = Math.max(maxF, r[s]);
                }
        if (minF >= maxF) {
            minF -= 0.0001;
            maxF += 0.0001;
        }
        gcConv.setStroke(Color.web("#152030"));
        gcConv.setLineWidth(0.5);
        for (int i = 0; i <= 5; i++) {
            double y = pad + 30 + pH * i / 5;
            gcConv.strokeLine(pad, y, pad + pW, y);
            gcConv.setFill(Color.web("#4a6a8a"));
            gcConv.setFont(Font.font("Consolas", 9));
            gcConv.setTextAlign(TextAlignment.RIGHT);
            gcConv.fillText(String.format("%.6f", maxF - (maxF - minF) * i / 5), pad - 8, y + 3);
        }
        int tot = konvergensiData.size();
        for (int s = 1; s < nc && s <= colors.length; s++) {
            gcConv.setStroke(Color.web(colors[(s - 1) % colors.length]));
            gcConv.setLineWidth(1.8);
            gcConv.beginPath();
            boolean first = true;
            for (int i = 0; i < tot; i++) {
                double v = konvergensiData.get(i)[s];
                if (v <= 0)
                    continue;
                double x = pad + (double) i / Math.max(1, tot - 1) * pW,
                        y = pad + 30 + pH * (1 - (v - minF) / (maxF - minF));
                if (first) {
                    gcConv.moveTo(x, y);
                    first = false;
                } else
                    gcConv.lineTo(x, y);
            }
            gcConv.stroke();
        }
        if (konvergensiHeader != null) {
            double lx = pad + 10, ly = pad + 35;
            for (int s = 1; s < nc && s <= colors.length; s++) {
                String lb = s < konvergensiHeader.length ? konvergensiHeader[s].trim() : "S" + s;
                if (lb.length() > 25)
                    lb = lb.substring(0, 25) + "..";
                gcConv.setFill(Color.web(colors[(s - 1) % colors.length]));
                gcConv.fillRect(lx, ly + (s - 1) * 16 - 6, 12, 3);
                gcConv.setFont(Font.font("Consolas", 8));
                gcConv.setTextAlign(TextAlignment.LEFT);
                gcConv.fillText(lb, lx + 16, ly + (s - 1) * 16);
            }
        }
    }

    String formatRoute(String route) {
        return route.replace("->", " → ");
    }

    ScrollPane buildRightPanel(Stage stage) {
        VBox p = new VBox(6);
        p.setPadding(new Insets(14));
        p.setPrefWidth(320);
        p.setStyle("-fx-background-color:#0d1520;-fx-border-color:#1a2a3f;-fx-border-width:0 0 0 1;");
        p.getChildren().add(secTitle("MUAT DATA"));
        Button b1 = new Button("Muat Folder Dataset");
        styleBtn(b1, "#1a5276");

        datasetCombo = new ComboBox<>();
        datasetCombo.setPrefWidth(290);
        datasetCombo.setStyle("-fx-background-color:#1a2a40;-fx-font-family:'Consolas';-fx-font-size:10;");
        datasetCombo.getItems().add("Pilih dataset...");
        b1.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Pilih Folder Dataset");
            File folder = dc.showDialog(stage);

            if (folder != null) {
                datasetFiles.clear();
                datasetCombo.getItems().clear();

                File[] files = folder.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().toLowerCase().endsWith(".csv")) {
                            datasetFiles.add(f);
                            datasetCombo.getItems().add(f.getName());
                        }
                    }
                }

                if (!datasetFiles.isEmpty()) {
                    datasetCombo.getSelectionModel().select(0);

                    // trigger manual load
                    File f = datasetFiles.get(0);
                    if (loadDataset(f.getAbsolutePath())) {
                        log("Dataset: " + f.getName() + " (" + nodes.size() + " node)");
                        rebuildUI();
                    }

                    log("Folder dataset dimuat: " + datasetFiles.size() + " file");
                }
            }
        });
        datasetCombo.getSelectionModel().selectedIndexProperty().addListener((obs, oldVal, newVal) -> {
            int idx = newVal.intValue();

            if (idx >= 0 && idx < datasetFiles.size()) {
                File f = datasetFiles.get(idx);

                if (loadDataset(f.getAbsolutePath())) {
                    log("Dataset: " + f.getName() + " (" + nodes.size() + " node)");
                    rebuildUI();
                }
            }
        });
        Button b2 = new Button("Muat Hasil (folder)");
        styleBtn(b2, "#1a5276");
        b2.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Pilih Folder Hasil");
            File f = dc.showDialog(stage);
            if (f != null) {
                loadResults(f.getAbsolutePath());
                rebuildUI();
            }
        });
        p.getChildren().addAll(b1, datasetCombo, b2, sep());
        p.getChildren().add(secTitle("HASIL OPTIMASI"));
        lblFitness = stat("Fitness", "—");
        lblDistance = stat("Jarak", "—");
        lblEnergy = stat("Energi", "—");
        lblRecharge = stat("Recharge", "—");
        routeLabel = stat("Rute", "—");
        routeLabel.setWrapText(true);
        routeLabel.setMaxWidth(280);
        p.getChildren().addAll(lblFitness, lblDistance, lblEnergy, lblRecharge, routeLabel, sep());
        p.getChildren().add(secTitle("PILIH RUTE"));
        routeCombo = new ComboBox<>();
        routeCombo.setPrefWidth(290);
        routeCombo.setStyle("-fx-background-color:#1a2a40;-fx-font-family:'Consolas';-fx-font-size:10;");
        routeCombo.getItems().add("Muat data dulu");
        routeCombo.getSelectionModel().select(0);
        routeCombo.setOnAction(e -> {
            int idx = routeCombo.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < ruteTerbaikData.size()) {
                selectRoute(idx);
                resetAnimation();
                String[] r = ruteTerbaikData.get(idx);
                lblFitness.setText("Fitness:  " + r[4]);
                lblDistance.setText("Jarak:    " + r[5] + " km");
                lblEnergy.setText("Energi:   " + r[6] + " Wh");
                lblRecharge.setText("Recharge: " + r[7] + " kali");
                routeLabel.setText("Rute: " + formatRoute(r[r.length - 1]));
                routeLabel.setWrapText(true);
                routeLabel.setMaxWidth(260);
                log("Rute: P=" + r[2] + " G=" + r[3]);

            }
        });

        p.getChildren().addAll(routeCombo, sep());
        p.getChildren().add(secTitle("ANIMASI DRONE"));
        lblCurrentPos = info("Posisi: —");
        lblSegment = info("Segmen: —");
        lblBattery = info("Baterai: —");
        batteryBar = new ProgressBar(1.0);
        batteryBar.setPrefWidth(290);
        batteryBar.setPrefHeight(14);
        batteryBar.setStyle("-fx-accent:#00e676;");
        p.getChildren().addAll(lblCurrentPos, lblSegment, lblBattery, batteryBar, sep());
        p.getChildren().add(secTitle("KONTROL"));
        btnPlay = new Button("PLAY");
        styleBtn(btnPlay, "#00b4d8");
        btnPlay.setOnAction(e -> {
            if (!dataLoaded || bestRoute == null) {
                log("Muat data dulu!");
                return;
            }
            if (isAnimating)
                pauseAnim();
            else
                startAnim();
        });
        btnReset = new Button("RESET");
        styleBtn(btnReset, "#e63946");
        btnReset.setOnAction(e -> resetAnimation());
        HBox br2 = new HBox(8, btnPlay, btnReset);
        br2.setAlignment(Pos.CENTER);
        speedSlider = new Slider(0.003, 0.04, 0.012);
        speedSlider.setPrefWidth(290);
        speedSlider.valueProperty().addListener((o, ov, nv) -> animSpeed = nv.doubleValue());
        p.getChildren().addAll(br2, info("Kecepatan:"), speedSlider, sep());
        p.getChildren().add(secTitle("LEGENDA"));
        p.getChildren().addAll(leg("#e67e22", "Depot"), leg("#00b4d8", "Customer"), leg("#00e676", "Charging Station"),
                leg("#00e5ff", "Rute"), leg("#ff5722", "Drone"), sep());
        lblStatus = info("Status: Siap");
        lblStatus.setTextFill(Color.web("#00e676"));
        p.getChildren().add(lblStatus);
        ScrollPane sc = new ScrollPane(p);
        sc.setFitToWidth(true);
        sc.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sc.setStyle("-fx-background:#0d1520;-fx-border-color:transparent;");
        return sc;
    }

    StackPane emptyTab(String msg) {
        Label l = new Label(msg);
        l.setFont(Font.font("Segoe UI", 14));
        l.setTextFill(Color.web("#2a4a6a"));
        StackPane s = new StackPane(l);
        s.setStyle("-fx-background-color:#0a0e17;");
        return s;
    }

    double toScreenX(double x) {
        return (padding + (x / maxCoord) * (canvasW - 2 * padding)) * zoom + offsetX;
    }

    double toScreenY(double y) {
        return (canvasH - padding - (y / maxCoord) * (canvasH - 2 * padding)) * zoom + offsetY;
    }

    void drawMap(int compSeg, double dx, double dy) {
        if (bestRoute == null)
            return;
        if (nodes.isEmpty())
            return;
        gc.setFill(Color.web("#080c14"));
        gc.fillRect(0, 0, canvasW, canvasH);
        gc.setStroke(Color.web("#111c2a"));
        gc.setLineWidth(0.5);
        gc.setFont(Font.font("Consolas", 8));
        int step = (int) (10 / zoom);
        step = Math.max(step, 2);
        for (int i = 0; i <= (int) maxCoord; i += step) {
            double sx = toScreenX(i), sy = toScreenY(i);
            gc.strokeLine(sx, 0, sx, canvasH);
            gc.strokeLine(0, sy, canvasW, sy);
            gc.setFill(Color.web("#253550"));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("" + i, sx, canvasH - padding + 14);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("" + i, padding - 6, sy + 3);
        }
        if (bestRoute != null) {
            for (int i = 0; i < compSeg && i < bestRoute.length - 1; i++) {
                double[] f = gn(bestRoute[i]), t = gn(bestRoute[i + 1]);
                if (f == null || t == null)
                    continue;
                double x1 = toScreenX(f[1]), y1 = toScreenY(f[2]), x2 = toScreenX(t[1]), y2 = toScreenY(t[2]);
                gc.setStroke(Color.web("#00e5ff", 0.15));
                gc.setLineWidth(8);
                gc.strokeLine(x1, y1, x2, y2);
                gc.setStroke(Color.web("#00e5ff", 0.85));
                gc.setLineWidth(2.5);
                gc.strokeLine(x1, y1, x2, y2);
                double mx = (x1 + x2) / 2, my = (y1 + y2) / 2, ang = Math.atan2(y2 - y1, x2 - x1);
                gc.setFill(Color.web("#00e5ff", 0.7));
                gc.fillPolygon(
                        new double[] { mx, mx - 7 * Math.cos(ang - Math.PI / 6), mx - 7 * Math.cos(ang + Math.PI / 6) },
                        new double[] { my, my - 7 * Math.sin(ang - Math.PI / 6), my - 7 * Math.sin(ang + Math.PI / 6) },
                        3);
            }
            if (compSeg >= 0 && compSeg < bestRoute.length - 1) {
                double[] f = gn(bestRoute[compSeg]);
                if (f != null) {
                    gc.setStroke(Color.web("#00e5ff", 0.6));
                    gc.setLineWidth(2);
                    gc.strokeLine(toScreenX(f[1]), toScreenY(f[2]), dx, dy);
                }
            }
            gc.setLineDashes(6, 6);
            gc.setStroke(Color.web("#1a3050", 0.5));
            gc.setLineWidth(1);
            for (int i = Math.max(compSeg + 1, 0); i < bestRoute.length - 1; i++) {
                double[] f = gn(bestRoute[i]), t = gn(bestRoute[i + 1]);
                if (f != null && t != null)
                    gc.strokeLine(toScreenX(f[1]), toScreenY(f[2]), toScreenX(t[1]), toScreenY(t[2]));
            }
            gc.setLineDashes(null);
        }
        for (double[] n : nodes) {
            double sx = toScreenX(n[1]), sy = toScreenY(n[2]);
            int tp = (int) n[3], id = (int) n[0];
            if (tp == 0) {
                gc.setFill(Color.web("#e67e22", 0.12));
                gc.fillOval(sx - 18, sy - 18, 36, 36);
                gc.setFill(Color.web("#e67e22"));
                gc.fillRoundRect(sx - 11, sy - 11, 22, 22, 4, 4);
                gc.setStroke(Color.web("#f5a623"));
                gc.setLineWidth(2);
                gc.strokeRoundRect(sx - 11, sy - 11, 22, 22, 4, 4);
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("D", sx, sy + 5);
            } else if (tp == 2) {
                gc.setFill(Color.web("#00e676", 0.1));
                gc.fillOval(sx - 15, sy - 15, 30, 30);
                gc.save();
                gc.translate(sx, sy);
                gc.rotate(45);
                gc.setFill(Color.web("#00e676"));
                gc.fillRoundRect(-6, -6, 12, 12, 2, 2);
                gc.restore();
                gc.setFill(Color.web("#ffd600"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText("⚡", sx, sy - 13);
                gc.setFill(Color.web("#00e676"));
                gc.setFont(Font.font("Consolas", 8));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText("CS" + (id - numCustomers), sx + 12, sy + 3);
            } else {
                double r = 8;
                boolean vis = false;
                if (bestRoute != null)
                    for (int i = 0; i <= currentSegment && i < bestRoute.length; i++)
                        if (bestRoute[i] == id) {
                            vis = true;
                            break;
                        }
                gc.setFill(Color.web(vis ? "#00b4d8" : "#2a5a7a"));
                gc.fillOval(sx - r, sy - r, r * 2, r * 2);
                gc.setStroke(Color.web(vis ? "#0090b0" : "#1a3a5a"));
                gc.setLineWidth(1.5);
                gc.strokeOval(sx - r, sy - r, r * 2, r * 2);
                gc.setFill(Color.web(vis ? "#a0dff0" : "#4a6a80"));
                gc.setFont(Font.font("Consolas", 8));
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText("" + id, sx + r + 3, sy + 3);
            }
        }
        if (compSeg >= 0) {
            gc.setFill(Color.web("#0a1020", 0.85));
            gc.fillRoundRect(canvasW - 180, 10, 170, 32, 8, 8);
            gc.setFill(Color.web("#00e5ff"));
            gc.setFont(Font.font("Consolas", FontWeight.BOLD, 12));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.format("Step %d / %d", compSeg, bestRoute.length - 1), canvasW - 95, 31);
        }
    }

    void drawDrone(double x, double y) {
        gc.save();
        gc.translate(x, y);
        gc.setFill(Color.web("#ff5722", 0.12));
        gc.fillOval(-24, -24, 48, 48);
        gc.setStroke(Color.web("#b0b0b0"));
        gc.setLineWidth(2);
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45 + i * 90), ex = Math.cos(a) * 16, ey = Math.sin(a) * 16;
            gc.strokeLine(0, 0, ex, ey);
            gc.save();
            gc.translate(ex, ey);
            gc.rotate(propellerAngle + i * 90);
            gc.setStroke(Color.web("#ff5722", 0.7));
            gc.setLineWidth(2.5);
            gc.strokeLine(-8, 0, 8, 0);
            gc.restore();
            gc.setFill(Color.web("#424242"));
            gc.fillOval(ex - 3, ey - 3, 6, 6);
        }
        gc.setFill(Color.web("#ff5722"));
        gc.fillOval(-6, -6, 12, 12);
        gc.setStroke(Color.web("#e64a19"));
        gc.setLineWidth(1.5);
        gc.strokeOval(-6, -6, 12, 12);
        gc.setFill(Color.WHITE);
        gc.fillOval(-2.5, -2.5, 5, 5);
        gc.restore();
    }

    double[] gn(int id) {
        for (double[] n : nodes)
            if ((int) n[0] == id)
                return n;
        return null;
    }

    void setupAnimation() {
        animTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!isAnimating || bestRoute == null)
                    return;
                animProgress += animSpeed;
                propellerAngle += 15;
                animProgress = Math.min(animProgress, 1.0);
                if (animProgress >= 1.0) {
                    animProgress = 0;
                    onSegDone();
                    currentSegment++;
                    if (currentSegment >= bestRoute.length - 1) {
                        isAnimating = false;
                        lblStatus.setText("Status: Selesai!");
                        lblStatus.setTextFill(Color.web("#00e676"));
                        btnPlay.setText("PLAY");
                        log("Selesai!");
                        double[] d = gn(0);
                        if (d != null)
                            drawMap(bestRoute.length - 1, toScreenX(d[1]), toScreenY(d[2]));
                        return;
                    }
                    setupSeg();
                }
                double cx = droneX + (targetX - droneX) * animProgress;
                double cy = droneY + (targetY - droneY) * animProgress;

                double[] f = gn(bestRoute[currentSegment]);
                double[] t = gn(bestRoute[currentSegment + 1]);

                if (f != null && t != null) {
                    double usedSoFar = segmentEnergy * animProgress;
                    double current = segmentStartBattery - usedSoFar;

                    updateBat(Math.max(0, current));
                }
                drawMap(currentSegment, toScreenX(cx), toScreenY(cy));
                drawDrone(toScreenX(cx), toScreenY(cy));
            };
        };
    }

    void startAnim() {
        if (bestRoute == null || bestRoute.length < 2)
            return;
        if (currentSegment >= bestRoute.length - 1)
            resetAnimation();
        isAnimating = true;
        btnPlay.setText("PAUSE");
        lblStatus.setText("Status: Berjalan...");
        lblStatus.setTextFill(Color.web("#ffd600"));
        if (currentSegment == 0) {
            currentBattery = E_MAX;
            log("Lepas landas!");
        }
        setupSeg();
        animTimer.start();
    }

    void pauseAnim() {
        isAnimating = false;
        btnPlay.setText("PLAY");
        lblStatus.setText("Status: Dijeda");
    }

    void resetAnimation() {
        isAnimating = false;
        animTimer.stop();
        currentSegment = 0;
        animProgress = 0;
        currentBattery = E_MAX;
        lblCurrentPos.setText("Posisi: V0");
        lblSegment.setText(bestRoute != null ? "Segmen: 0/" + (bestRoute.length - 1) : "—");
        updateBat(E_MAX);
        btnPlay.setText("PLAY");
        lblStatus.setText("Status: Siap");
        lblStatus.setTextFill(Color.web("#00e676"));
        double[] d = gn(0);
        if (d != null)
            drawMap(-1, toScreenX(d[1]), toScreenY(d[2]));
    }

    void setupSeg() {
        if (bestRoute == null || currentSegment >= bestRoute.length - 1)
            return;

        double[] f = gn(bestRoute[currentSegment]);
        double[] t = gn(bestRoute[currentSegment + 1]);

        if (f != null && t != null) {
            double dist = Math.sqrt(Math.pow(f[1] - t[1], 2) + Math.pow(f[2] - t[2], 2));
            segmentEnergy = dist * ALPHA; // 🔥 TARUH DI SINI
        }
        droneX = f[1];
        droneY = f[2];
        targetX = t[1];
        targetY = t[2];

        lblCurrentPos.setText(nl(bestRoute[currentSegment]) + " → " + nl(bestRoute[currentSegment + 1]));
        lblSegment.setText("Segmen: " + (currentSegment + 1) + "/" + (bestRoute.length - 1));
        segmentStartBattery = currentBattery;
    }

    void onSegDone() {
        int arr = bestRoute[currentSegment + 1];
        double[] f = gn(bestRoute[currentSegment]), t = gn(arr);
        if (f != null && t != null) {
            double d = Math.sqrt(Math.pow(f[1] - t[1], 2) + Math.pow(f[2] - t[2], 2));
            currentBattery = segmentStartBattery - segmentEnergy;
            currentBattery = Math.max(0, currentBattery);
            if (arr > numCustomers && arr != 0) {
                log(String.format("⚡ %s | %.0f→%.0f Wh", nl(arr), currentBattery, E_MAX));
                currentBattery = E_MAX;
            } else if (arr == 0) {
                log(String.format("Depot | %.0f→%.0f Wh", currentBattery, E_MAX));
                currentBattery = E_MAX;
            } else
                log(String.format("→ %s | %.1f km | Sisa: %.0f Wh", nl(arr), d, currentBattery));
        }
        updateBat(currentBattery);
    }

    void updateBat(double b) {
        double p = b / E_MAX;
        batteryBar.setProgress(Math.max(0, p));
        lblBattery.setText(String.format("Baterai: %.0f/%.0f Wh", b, E_MAX));
        batteryBar.setStyle(p > 0.5 ? "-fx-accent:#00e676;" : p > 0.25 ? "-fx-accent:#ffd600;" : "-fx-accent:#e63946;");
    }

    String nl(int id) {
        if (id == 0)
            return "V0(Depot)";
        if (id > numCustomers)
            return "CS" + (id - numCustomers);
        return "V" + id;
    }

    boolean isNum(String s) {
        try {
            Double.parseDouble(s.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    Label secTitle(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        l.setTextFill(Color.web("#00b4d8"));
        l.setPadding(new Insets(8, 0, 4, 0));
        return l;
    }

    Label stat(String t, String v) {
        Label l = new Label(t + ":  " + v);
        l.setFont(Font.font("Consolas", 11));
        l.setTextFill(Color.web("#c0d8e8"));
        l.setPadding(new Insets(2, 0, 2, 8));
        return l;
    }

    Label info(String t) {
        Label l = new Label(t);
        l.setFont(Font.font("Consolas", 10.5));
        l.setTextFill(Color.web("#90aac0"));
        return l;
    }

    Separator sep() {
        Separator s = new Separator();
        s.setStyle("-fx-background-color:#1a2a3f;");
        return s;
    }

    void styleBtn(Button b, String c) {
        b.setPrefWidth(140);
        b.setPrefHeight(36);
        b.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        b.setTextFill(Color.WHITE);
        b.setStyle("-fx-background-color:" + c + ";-fx-background-radius:6;-fx-cursor:hand;");
        b.setOnMouseEntered(e -> b.setOpacity(0.85));
        b.setOnMouseExited(e -> b.setOpacity(1.0));
    }

    HBox leg(String c, String t) {
        HBox r = new HBox(8);
        r.setAlignment(Pos.CENTER_LEFT);
        r.setPadding(new Insets(1, 0, 1, 8));
        Label s = new Label("●");
        s.setFont(Font.font("Segoe UI", 13));
        s.setTextFill(Color.web(c));
        Label l = new Label(t);
        l.setFont(Font.font("Segoe UI", 11));
        l.setTextFill(Color.web("#6a8aa0"));
        r.getChildren().addAll(s, l);
        return r;
    }

    void log(String m) {
        Platform.runLater(() -> {
            logArea.appendText(">> " + m + "\n");
            logArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    String getCSS() {
        return String.join("",
                ".tab-pane .tab-header-background{-fx-background-color:%230d1520;}",
                ".tab{-fx-background-color:%231a2a40;-fx-border-color:%231a2a40;}",
                ".tab:selected{-fx-background-color:%2300b4d8;}",
                ".tab .tab-label{-fx-text-fill:%2390aac0;-fx-font-family:'Segoe UI';-fx-font-weight:bold;-fx-font-size:11;}",
                ".tab:selected .tab-label{-fx-text-fill:white;}", ".scroll-bar{-fx-background-color:%230d1520;}",
                ".scroll-bar .thumb{-fx-background-color:%231a3050;-fx-background-radius:4;}",
                ".scroll-bar .increment-button,.scroll-bar .decrement-button{-fx-background-color:transparent;-fx-padding:0;}",
                ".scroll-bar .increment-arrow,.scroll-bar .decrement-arrow{-fx-shape:'';-fx-padding:0;}",
                ".progress-bar .track{-fx-background-color:%231a2a3f;-fx-background-radius:4;}",
                ".progress-bar .bar{-fx-background-radius:4;-fx-padding:2;}",
                ".slider .track{-fx-background-color:%231a2a3f;}",
                ".slider .thumb{-fx-background-color:%2300b4d8;}",
                ".table-view .column-header{-fx-background-color:%231a2a40;}",
                ".table-view .column-header .label{-fx-text-fill:%2300b4d8;-fx-font-weight:bold;}",
                ".table-row-cell{-fx-background-color:%230d1520;-fx-text-fill:%23b0c4de;}",
                ".table-row-cell:odd{-fx-background-color:%23111d2a;}",
                ".table-row-cell:selected{-fx-background-color:%231a3a5f;}",
                ".combo-box{-fx-background-color:%231a2a40;}",
                ".combo-box .list-cell{-fx-text-fill:%23b0c4de;-fx-background-color:%231a2a40;}",
                ".combo-box-popup .list-view{-fx-background-color:%231a2a40;}",
                ".combo-box-popup .list-cell{-fx-text-fill:%23b0c4de;-fx-background-color:%231a2a40;}",
                ".combo-box-popup .list-cell:hover{-fx-background-color:%232a4a6a;}");
    }

    public static void main(String[] args) {
        launch(args);
    }
}
