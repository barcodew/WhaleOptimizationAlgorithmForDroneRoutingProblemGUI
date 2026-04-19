package Main;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import Helpers.Datasetreader;
import Helpers.MatrixBuilder;

/**
 * Whale Optimization Algorithm For Drone Routing Problem
 * Made With ❤️ by Barcodew1337
 */

public class DRP_Full {

    // === KONSTANTA ===
    static final double ALPHA = 20.0;
    static final double BATTERY_CAPACITY = 4000.0;
    static final int MAX_DEPTH = 3;

    // === HASIL PER RUN ===
    static class RunResult {
        double fitness;
        double totalDistance;
        double totalEnergy;
        int rechargeCount;
        boolean feasible;
        ArrayList<Integer> route;
        int[] permutation;
        double[] convergence; // fitness terbaik per iterasi
        long computeTimeMs;

        @Override
        public String toString() {
            return String.format(
                    "Fitness: %.6f | Jarak: %.2f km | Energi: %.2f Wh | Recharge: %d | Feasible: %s | Waktu: %d ms",
                    fitness, totalDistance, totalEnergy, rechargeCount,
                    feasible ? "YA" : "TIDAK", computeTimeMs);
        }
    }

    // === HASIL PER SKENARIO ===
    static class ScenarioResult {
        int popSize, maxIter, numCustomers, numCS;
        String datasetName;
        RunResult[] runs;
        double avgFitness, bestFitness, worstFitness, stdFitness;
        double avgDistance, bestDistance;
        double avgTime;
        int feasibleCount;

        void calculate() {
            int totalRuns = runs.length;
            bestFitness = 0;
            worstFitness = Double.MAX_VALUE;
            double sumFit = 0, sumDist = 0, sumTime = 0;
            feasibleCount = 0;

            for (RunResult r : runs) {
                sumFit += r.fitness;
                sumDist += r.totalDistance;
                sumTime += r.computeTimeMs;
                if (r.fitness > bestFitness)
                    bestFitness = r.fitness;
                if (r.fitness < worstFitness)
                    worstFitness = r.fitness;
                if (r.feasible)
                    feasibleCount++;
            }

            avgFitness = sumFit / totalRuns;
            avgDistance = sumDist / totalRuns;
            avgTime = sumTime / totalRuns;

            // Hitung best distance dari best fitness
            RunResult bestRun = runs[0];

            for (RunResult r : runs) {
                if (r.fitness > bestRun.fitness) {
                    bestRun = r;
                }
            }

            bestDistance = bestRun.totalDistance;

            // Standar deviasi
            double sumSq = 0;
            for (RunResult r : runs) {
                sumSq += Math.pow(r.fitness - avgFitness, 2);
            }
            stdFitness = Math.sqrt(sumSq / totalRuns);
        }
    }

    // ============================================================
    // MAIN — Eksperimen Lengkap
    // ============================================================
    public static void main(String[] args) {

        // === KONFIGURASI EKSPERIMEN ===
        String[] datasets = {
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\10_Customers.csv",
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\20_Customers.csv",
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\30_Customers.csv",
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\40_Customers.csv",
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\50_Customers.csv",

        };

        int[][] skenario = {
                // {popSize, maxIter}
                { 20, 200 },
                { 20, 500 },
                { 20, 1000 },
                { 50, 200 },
                { 50, 500 },
                { 50, 1000 },
                { 100, 200 },
                { 100, 500 },
                { 100, 1000 },
        };

        int totalRuns = 10; // jumlah run per skenario

        // === OUTPUT FOLDER ===
        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String outputPrefix = "hasil_" + timestamp;

        // === JALANKAN EKSPERIMEN ===
        ArrayList<ScenarioResult> allResults = new ArrayList<>();

        for (String dataset : datasets) {
            Datasetreader.read(dataset);
            double[][] adj = MatrixBuilder.generate(Datasetreader.nodes);
            int numCust = Datasetreader.numberOfCustomers;
            int numCS = Datasetreader.numberOfChargingStations;

            // Ambil nama file dataset untuk label
            String dsName = dataset.substring(dataset.lastIndexOf("\\") + 1)
                    .replace(".csv", "");

            printDataset();
            System.out.println("\n" + "=".repeat(60));
            System.out.println("DATASET: " + dsName);
            System.out.println("=".repeat(60));
            for (int i = 0; i < adj.length; i++) {
                for (int j = 0; j < adj[i].length; j++) {
                    if (j == 0) {
                        System.out.print("baris ke " + i + "[" + adj[i][j]);

                    }
                    System.out.print("," + adj[i][j]);
                }
                System.out.println();
            }
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Matrix Adjacency: " + dsName);
            System.out.println("=".repeat(60));

            for (int[] sk : skenario) {
                int popSize = sk[0];
                int maxIter = sk[1];

                System.out.printf("\n--- Skenario: P=%d, G=%d ---\n", popSize, maxIter);

                ScenarioResult scenario = new ScenarioResult();
                scenario.popSize = popSize;
                scenario.maxIter = maxIter;
                scenario.numCustomers = numCust;
                scenario.numCS = numCS;
                scenario.datasetName = dsName;
                scenario.runs = new RunResult[totalRuns];

                for (int run = 0; run < totalRuns; run++) {
                    System.out.printf("  Run %2d/%d ... ", run + 1, totalRuns);

                    RunResult result = runWOA(adj, numCust, numCS, popSize, maxIter);
                    scenario.runs[run] = result;

                    System.out.printf("Fitness: %.6f | Jarak: %.2f km | Waktu: %d ms\n",
                            result.fitness, result.totalDistance, result.computeTimeMs);
                }

                scenario.calculate();
                allResults.add(scenario);

                // Cetak ringkasan skenario
                System.out.printf("\n  Ringkasan P=%d, G=%d:\n", popSize, maxIter);
                System.out.printf("    Best Fitness   : %.6f (jarak: %.2f km)\n",
                        scenario.bestFitness, scenario.bestDistance);
                System.out.printf("    Avg Fitness    : %.6f\n", scenario.avgFitness);
                System.out.printf("    Std Fitness    : %.6f\n", scenario.stdFitness);
                System.out.printf("    Avg Jarak      : %.2f km\n", scenario.avgDistance);
                System.out.printf("    Avg Waktu      : %.0f ms\n", scenario.avgTime);
                System.out.printf("    Feasible       : %d/%d\n",
                        scenario.feasibleCount, totalRuns);
            }
        }

        // === EXPORT HASIL ===
        exportHasilCSV(allResults, outputPrefix + "_ringkasan.csv");
        exportKonvergensiCSV(allResults, outputPrefix + "_konvergensi.csv");
        exportRuteTerbaikCSV(allResults, outputPrefix + "_rute_terbaik.csv");

        // === CETAK TABEL AKHIR ===
        printTabelAkhir(allResults);
    }

    // ============================================================
    // WOA UTAMA — Return RunResult
    // ============================================================
    static RunResult runWOA(double[][] adj, int numCustomers, int numCS,
            int popSize, int maxIter) {

        long startTime = System.currentTimeMillis();
        int n = numCustomers;
        double b = 1.0;
        double[] convergence = new double[maxIter];

        // === INISIALISASI ===
        double[][] population = new double[popSize][n];
        double[] fitnessAll = new double[popSize];
        int[][] permutasiAll = new int[popSize][n];

        for (int i = 0; i < popSize; i++) {
            population[i] = randomWhale(n);
        }

        // === EVALUASI AWAL ===
        int bestIdx = 0;
        double bestFitness = -Double.MAX_VALUE;

        for (int i = 0; i < popSize; i++) {
            permutasiAll[i] = LOVdecode(population[i]);
            fitnessAll[i] = evaluateFitnessSilent(
                    permutasiAll[i], adj, numCustomers, numCS);
            if (fitnessAll[i] > bestFitness) {
                bestFitness = fitnessAll[i];
                bestIdx = i;
            }
        }

        double[] Xstar = population[bestIdx].clone();
        double fitnessStar = bestFitness;

        // === ITERASI UTAMA ===
        for (int t = 1; t <= maxIter; t++) {
            double a = 2.0 - 2.0 * ((double) t / maxIter);

            for (int i = 0; i < popSize; i++) {
                double r1 = Math.random();
                double r2 = Math.random();
                double p = Math.random();
                double l = Math.random() * 2 - 1;

                double A = 2 * a * r1 - a;
                double C = 2 * r2;

                double[] newPos = new double[n];

                if (Math.abs(A) < 1) {
                    if (p < 0.5) {
                        for (int g = 0; g < n; g++) {
                            double D = Math.abs(C * Xstar[g] - population[i][g]);
                            newPos[g] = Xstar[g] - A * D;
                        }
                    } else {
                        for (int g = 0; g < n; g++) {
                            double Dprime = Math.abs(Xstar[g] - population[i][g]);
                            newPos[g] = Dprime * Math.exp(b * l)
                                    * Math.cos(2 * Math.PI * l)
                                    + Xstar[g];
                        }
                    }
                } else {
                    int randIdx = (int) (Math.random() * popSize);
                    double[] Xrand = population[randIdx];
                    for (int g = 0; g < n; g++) {
                        double D = Math.abs(C * Xrand[g] - population[i][g]);
                        newPos[g] = Xrand[g] - A * D;
                    }
                }

                for (int g = 0; g < n; g++) {
                    newPos[g] = Math.max(-1, Math.min(1, newPos[g]));
                }

                int[] newPerm = LOVdecode(newPos);

                // LOCAL SEARCH HANYA UNTUK BEST ATAU SEBAGIAN
                int[] improvedPerm;
                if (Math.random() < 0.2) { // 20% saja
                    improvedPerm = localSearch(newPerm, adj, numCustomers, numCS);
                } else {
                    improvedPerm = newPerm;
                }

                double newFitness = evaluateFitnessSilent(improvedPerm, adj, numCustomers, numCS);

                if (newFitness > fitnessAll[i]) {
                    population[i] = RLOVencode(improvedPerm); // 🔥 FIX
                    permutasiAll[i] = improvedPerm;
                    fitnessAll[i] = newFitness;
                }
            }

            for (int i = 0; i < popSize; i++) {
                if (fitnessAll[i] > fitnessStar) {
                    fitnessStar = fitnessAll[i];
                    Xstar = population[i].clone();
                    bestIdx = i;
                }
            }

            convergence[t - 1] = fitnessStar;
        }

        // === BANGUN HASIL ===
        long endTime = System.currentTimeMillis();

        int[] bestPerm = LOVdecode(Xstar);
        RunResult result = new RunResult();
        result.fitness = fitnessStar;
        // result.totalDistance = (fitnessStar > 0) ? (1.0 / fitnessStar) - 1 : 0;
        // result.totalEnergy = result.totalDistance * ALPHA;
        result.permutation = bestPerm;
        result.convergence = convergence;
        result.computeTimeMs = endTime - startTime;

        // Bangun rute lengkap untuk hasil terbaik
        buildFullRoute(bestPerm, adj, numCustomers, numCS, result);

        return result;
    }

    // ============================================================
    // BANGUN RUTE LENGKAP + Detail untuk RunResult
    // ============================================================
    static void buildFullRoute(int[] permutation, double[][] adj,
            int numCustomers, int numCS, RunResult result) {
        ArrayList<Integer> route = new ArrayList<>();
        route.add(0);
        double battery = BATTERY_CAPACITY;
        boolean feasible = true;
        int rechargeCount = 0;
        int[] option = buildCSOptions(numCustomers, numCS);

        for (int i = 0; i < permutation.length; i++) {
            int origin = route.get(route.size() - 1);
            int dest = permutation[i];
            double energyNeeded = adj[origin][dest] * ALPHA;

            boolean reachable = (energyNeeded <= battery);
            boolean safe = false;

            if (reachable) {
                double batteryAfter = battery - energyNeeded;
                if (batteryAfter >= adj[dest][0] * ALPHA)
                    safe = true;
                if (!safe) {
                    for (int k = numCustomers + 1; k <= numCustomers + numCS; k++) {
                        if (batteryAfter >= adj[dest][k] * ALPHA) {
                            safe = true;
                            break;
                        }
                    }
                }
            }

            if (reachable && safe) {
                battery -= energyNeeded;
                route.add(dest);
            } else {
                int[] cr = steppingStone(option, origin, dest, adj,
                        battery, BATTERY_CAPACITY, MAX_DEPTH);
                if (cr.length > 0) {
                    int cur = origin;
                    for (int csNode : cr) {
                        battery -= adj[cur][csNode] * ALPHA;
                        route.add(csNode);
                        battery = BATTERY_CAPACITY;
                        rechargeCount++;
                        cur = csNode;
                    }
                    battery -= adj[cur][dest] * ALPHA;
                    route.add(dest);
                } else {
                    feasible = false;
                    break;
                }
            }
        }

        if (feasible) {
            int last = route.get(route.size() - 1);
            if (last != 0) {
                double e = adj[last][0] * ALPHA;
                if (e <= battery) {
                    route.add(0);
                } else {
                    int[] cr = steppingStone(option, last, 0, adj,
                            battery, BATTERY_CAPACITY, MAX_DEPTH);
                    if (cr.length > 0) {
                        int cur = last;
                        for (int csNode : cr) {
                            route.add(csNode);
                            battery = BATTERY_CAPACITY;
                            rechargeCount++;
                            cur = csNode;
                        }
                        route.add(0);
                    } else {
                        feasible = false;
                    }
                }
            }
        }

        result.route = route;
        result.feasible = feasible;
        result.rechargeCount = rechargeCount;

        // Hitung ulang total distance dari rute lengkap
        double totalDist = 0;
        double totalEnergy = 0;

        for (int i = 0; i < route.size() - 1; i++) {
            double d = adj[route.get(i)][route.get(i + 1)];
            totalDist += d;
            totalEnergy += d * ALPHA;
        }

        result.totalDistance = totalDist;
        result.totalEnergy = totalEnergy;
    }

    // ============================================================
    // EXPORT CSV — Ringkasan semua skenario
    // ============================================================
    static void exportHasilCSV(ArrayList<ScenarioResult> results, String filename) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("No,Dataset,Customers,CS,PopSize,MaxIter,"
                    + "BestFitness,AvgFitness,StdFitness,WorstFitness,"
                    + "BestDistance,AvgDistance,"
                    + "FeasibleCount,TotalRuns,AvgTimeMs");

            int no = 1;
            for (ScenarioResult s : results) {
                pw.printf("%d,%s,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.2f,%.2f,%d,%d,%.0f\n",
                        no++, s.datasetName, s.numCustomers, s.numCS,
                        s.popSize, s.maxIter,
                        s.bestFitness, s.avgFitness, s.stdFitness, s.worstFitness,
                        s.bestDistance, s.avgDistance,
                        s.feasibleCount, s.runs.length, s.avgTime);
            }

            System.out.println("\n✓ Ringkasan disimpan: " + filename);
        } catch (Exception e) {
            System.out.println("✗ Gagal simpan ringkasan: " + e.getMessage());
        }
    }

    // ============================================================
    // EXPORT CSV — Konvergensi (fitness per iterasi)
    // ============================================================
    static void exportKonvergensiCSV(ArrayList<ScenarioResult> results, String filename) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {

            // HEADER
            pw.print("Iterasi");
            for (ScenarioResult s : results) {
                pw.printf(",P%d_G%d_%s", s.popSize, s.maxIter, s.datasetName);
            }
            pw.println();

            // cari iterasi terpanjang
            int maxLen = 0;
            for (ScenarioResult s : results) {
                for (RunResult r : s.runs) {
                    if (r.convergence.length > maxLen)
                        maxLen = r.convergence.length;
                }
            }

            // LOOP ITERASI
            for (int t = 0; t < maxLen; t++) {
                pw.print(t + 1);

                for (ScenarioResult s : results) {

                    double sum = 0;
                    int count = 0;

                    // ambil SEMUA RUN
                    for (RunResult r : s.runs) {
                        if (t < r.convergence.length) {
                            sum += r.convergence[t];
                            count++;
                        }
                    }

                    double avg = (count > 0) ? sum / count : 0;

                    pw.printf(",%.12f", avg);
                }

                pw.println();
            }

            System.out.println("✓ Konvergensi (FIXED AVG) disimpan: " + filename);

        } catch (Exception e) {
            System.out.println("✗ Gagal simpan konvergensi: " + e.getMessage());
        }
    }

    // ============================================================
    // EXPORT CSV — Rute terbaik per skenario
    // ============================================================
    static void exportRuteTerbaikCSV(ArrayList<ScenarioResult> results, String filename) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename))) {
            pw.println("No,Dataset,PopSize,MaxIter,Fitness,TotalDistance,TotalEnergy,"
                    + "RechargeCount,Feasible,Route");

            int no = 1;
            for (ScenarioResult s : results) {
                // Cari run terbaik
                RunResult best = s.runs[0];
                for (RunResult r : s.runs) {
                    if (r.fitness > best.fitness)
                        best = r;
                }

                // Bangun string rute
                StringBuilder routeStr = new StringBuilder();
                for (int i = 0; i < best.route.size(); i++) {
                    if (i > 0)
                        routeStr.append(" -> ");
                    routeStr.append(getLabel(best.route.get(i), s.numCustomers));
                }

                pw.printf("%d,%s,%d,%d,%.6f,%.2f,%.2f,%d,%s,\"%s\"\n",
                        no++, s.datasetName, s.popSize, s.maxIter,
                        best.fitness, best.totalDistance, best.totalEnergy,
                        best.rechargeCount, best.feasible ? "YA" : "TIDAK",
                        routeStr.toString());
            }

            System.out.println("✓ Rute terbaik disimpan: " + filename);
        } catch (Exception e) {
            System.out.println("✗ Gagal simpan rute: " + e.getMessage());
        }
    }

    // ============================================================
    // CETAK TABEL AKHIR
    // ============================================================
    static void printTabelAkhir(ArrayList<ScenarioResult> results) {
        System.out.println("\n" + "=".repeat(120));
        System.out.println("                              TABEL HASIL EKSPERIMEN");
        System.out.println("=".repeat(120));
        System.out.printf("%-4s %-18s %5s %6s %12s %12s %12s %12s %10s %10s\n",
                "No", "Dataset", "P", "G", "Best Fit", "Avg Fit", "Std Fit",
                "Best Dist", "Feasible", "Avg Time");
        System.out.println("-".repeat(120));

        int no = 1;
        for (ScenarioResult s : results) {
            System.out.printf("%-4d %-18s %5d %6d %12.6f %12.6f %12.6f %10.2f km %7d/%d %8.0f ms\n",
                    no++, s.datasetName, s.popSize, s.maxIter,
                    s.bestFitness, s.avgFitness, s.stdFitness,
                    s.bestDistance,
                    s.feasibleCount, s.runs.length,
                    s.avgTime);
        }

        System.out.println("=".repeat(120));

        // Cetak rute terbaik keseluruhan
        ScenarioResult overallBest = results.get(0);
        for (ScenarioResult s : results) {
            if (s.bestFitness > overallBest.bestFitness) {
                overallBest = s;
            }
        }

        RunResult bestRun = overallBest.runs[0];
        for (RunResult r : overallBest.runs) {
            if (r.fitness > bestRun.fitness)
                bestRun = r;
        }

        System.out.println("\n🏆 SOLUSI TERBAIK KESELURUHAN:");
        System.out.printf("   Skenario  : %s | P=%d, G=%d\n",
                overallBest.datasetName, overallBest.popSize, overallBest.maxIter);
        System.out.printf("   Fitness   : %.6f\n", bestRun.fitness);
        System.out.printf("   Jarak     : %.2f km\n", bestRun.totalDistance);
        System.out.printf("   Energi    : %.2f Wh\n", bestRun.totalEnergy);
        System.out.printf("   Recharge  : %d kali\n", bestRun.rechargeCount);
        System.out.printf("   Feasible  : %s\n", bestRun.feasible ? "YA" : "TIDAK");
        System.out.printf("   Waktu     : %d ms\n", bestRun.computeTimeMs);
        System.out.print("   Rute      : ");
        for (int i = 0; i < bestRun.route.size(); i++) {
            if (i > 0)
                System.out.print(" → ");
            System.out.print(getLabel(bestRun.route.get(i), overallBest.numCustomers));
        }
        System.out.println();
    }

    // ============================================================
    // KOMPONEN ALGORITMA
    // ============================================================

    static double[] randomWhale(int numCustomers) {
        double[] whale = new double[numCustomers];
        for (int i = 0; i < whale.length; i++) {
            whale[i] = Math.random() * 2 - 1; // range [-1, 1]
        }
        return whale;
    }

    static int[] LOVdecode(double[] whale) {
        int n = whale.length;
        int[] phi = new int[n];
        for (int g = 0; g < n; g++) {
            int rank = 1;
            for (int j = 0; j < n; j++) {
                if (whale[j] > whale[g] || (whale[j] == whale[g] && j < g)) {
                    rank++;
                }
            }
            phi[g] = rank;
        }
        int[] permutasi = new int[n];
        for (int g = 0; g < n; g++) {
            permutasi[phi[g] - 1] = g + 1;
        }
        return permutasi;
    }

    static double[] RLOVencode(int[] permutation) {
        int n = permutation.length;
        double[] whale = new double[n];
        double step = 2.0 / (n + 1); // range [-1, 1]

        for (int pos = 0; pos < n; pos++) {
            int customerId = permutation[pos];
            double baseValue = 1.0 - ((pos + 1) * step);
            // Tambah noise kecil agar tidak identik
            double noise = (Math.random() - 0.5) * step * 0.5;
            whale[customerId - 1] = Math.max(-1, Math.min(1, baseValue + noise));
        }
        return whale;
    }

    static double evaluateFitnessSilent(int[] permutation, double[][] adj,
            int numCustomers, int numCS) {
        ArrayList<Integer> route = new ArrayList<>();
        route.add(0);
        double battery = BATTERY_CAPACITY;
        double totalEnergy = 0;
        boolean feasible = true;
        int[] option = buildCSOptions(numCustomers, numCS);

        for (int i = 0; i < permutation.length; i++) {
            int origin = route.get(route.size() - 1);
            int dest = permutation[i];
            double energyNeeded = adj[origin][dest] * ALPHA;

            boolean reachable = (energyNeeded <= battery);
            boolean safe = false;

            if (reachable) {
                double batteryAfter = battery - energyNeeded;
                if (batteryAfter >= adj[dest][0] * ALPHA)
                    safe = true;
                if (!safe) {
                    for (int k = numCustomers + 1; k <= numCustomers + numCS; k++) {
                        if (batteryAfter >= adj[dest][k] * ALPHA) {
                            safe = true;
                            break;
                        }
                    }
                }
            }

            if (reachable && safe) {
                battery -= energyNeeded;
                totalEnergy += energyNeeded;
                route.add(dest);
            } else {
                int[] cr = steppingStone(option, origin, dest, adj,
                        battery, BATTERY_CAPACITY, MAX_DEPTH);
                if (cr.length > 0) {
                    int cur = origin;
                    for (int csNode : cr) {
                        battery -= adj[cur][csNode] * ALPHA;
                        totalEnergy += adj[cur][csNode] * ALPHA;
                        route.add(csNode);
                        battery = BATTERY_CAPACITY;
                        cur = csNode;
                    }
                    battery -= adj[cur][dest] * ALPHA;
                    totalEnergy += adj[cur][dest] * ALPHA;
                    route.add(dest);
                } else {
                    feasible = false;
                    break;
                }
            }
        }

        if (feasible) {
            int last = route.get(route.size() - 1);
            if (last != 0) {
                double e = adj[last][0] * ALPHA;
                if (e <= battery) {
                    totalEnergy += e;
                    route.add(0);
                } else {
                    int[] cr = steppingStone(option, last, 0, adj,
                            battery, BATTERY_CAPACITY, MAX_DEPTH);
                    if (cr.length > 0) {
                        int cur = last;
                        for (int csNode : cr) {
                            totalEnergy += adj[cur][csNode] * ALPHA;
                            route.add(csNode);
                            battery = BATTERY_CAPACITY;
                            cur = csNode;
                        }
                        totalEnergy += adj[cur][0] * ALPHA;
                        route.add(0);
                    } else {
                        feasible = false;
                    }
                }
            }
        }

        if (!feasible) {
            return 1e-10; // death penalty
        }

        return 1.0 / (totalEnergy + 1);
    }

    static int[] localSearch(int[] permutation, double[][] adj,
            int numCustomers, int numCS) {
        int[] best = permutation.clone();
        double bestFitness = evaluateFitnessSilent(best, adj, numCustomers, numCS);

        for (int iter = 0; iter < 30; iter++) {
            int[] candidate = best.clone();
            double r = Math.random();
            int n = candidate.length;
            int k = (int) (Math.random() * n);
            int l = (int) (Math.random() * n);
            while (l == k)
                l = (int) (Math.random() * n);

            if (r < 1.0 / 3) {
                int temp = candidate[k];
                candidate[k] = candidate[l];
                candidate[l] = temp;
            } else if (r < 2.0 / 3) {
                int val = candidate[k];
                ArrayList<Integer> list = new ArrayList<>();
                for (int v : candidate)
                    list.add(v);
                list.remove(k);
                if (l > k)
                    l--;
                if (l >= list.size())
                    l = list.size() - 1;
                list.add(l, val);
                for (int i = 0; i < n; i++)
                    candidate[i] = list.get(i);
            } else {
                if (k > l) {
                    int tmp = k;
                    k = l;
                    l = tmp;
                }
                while (k < l) {
                    int tmp = candidate[k];
                    candidate[k] = candidate[l];
                    candidate[l] = tmp;
                    k++;
                    l--;
                }
            }

            double candidateFitness = evaluateFitnessSilent(
                    candidate, adj, numCustomers, numCS);

            if (candidateFitness > bestFitness) {
                best = candidate;
                bestFitness = candidateFitness;
            }
        }
        return best;
    }

    static int[] steppingStone(int[] option, int origin, int destination,
            double[][] adjacency, double remainingBattery,
            double batteryCapacity, int maxDepth) {

        Queue<ArrayList<Integer>> antrian = new LinkedList<>();
        for (int i = 0; i < option.length; i++) {
            ArrayList<Integer> c = new ArrayList<>();
            c.add(option[i]);
            antrian.add(c);
        }

        ArrayList<Integer> best = new ArrayList<>();
        double bestEnergy = Double.MAX_VALUE;
        int bestDepth = maxDepth + 1; // track kedalaman solusi terbaik

        while (!antrian.isEmpty()) {
            ArrayList<Integer> head = antrian.poll();

            // OPTIMASI: skip kalau sudah lebih panjang dari solusi terbaik
            if (head.size() >= bestDepth)
                continue;

            double totalEnergi = 0;
            double remaining = remainingBattery;
            int ori = origin;
            boolean reachable = true;

            for (int node : head) {
                double energy = adjacency[ori][node] * ALPHA;
                if (energy <= remaining) {
                    totalEnergi += energy;
                    remaining = batteryCapacity;
                    ori = node;
                } else {
                    reachable = false;
                    break;
                }
            }

            if (reachable) {
                double energy = adjacency[ori][destination] * ALPHA;
                if (energy <= remaining) {
                    totalEnergi += energy;
                    if (totalEnergi < bestEnergy) {
                        bestEnergy = totalEnergi;
                        best = head;
                        bestDepth = head.size(); // update kedalaman terbaik
                    }
                }
            }

            // OPTIMASI: hanya ekspansi kalau masih bisa lebih baik
            if (head.size() < maxDepth && head.size() < bestDepth) {
                for (int opt : option) {
                    if (!head.contains(opt)) {
                        ArrayList<Integer> c = new ArrayList<>(head);
                        c.add(opt);
                        antrian.add(c);
                    }
                }
            }
        }

        return best.stream().mapToInt(Integer::intValue).toArray();
    }

    // ============================================================
    // HELPERS
    // ============================================================
    static int[] buildCSOptions(int numCustomers, int numCS) {
        int[] option = new int[numCS];
        for (int k = 0; k < numCS; k++) {
            option[k] = numCustomers + k + 1;
        }
        return option;
    }

    static String getLabel(int id, int numCustomers) {
        if (id == 0)
            return "V0(Depot)";
        if (id > numCustomers)
            return "CS" + (id - numCustomers);
        return "V" + id;
    }

    static void printDataset() {
        System.out.println("Width: " + Datasetreader.width);
        System.out.println("Height: " + Datasetreader.height);
        System.out.println("Customers: " + Datasetreader.numberOfCustomers);
        System.out.println("CS: " + Datasetreader.numberOfChargingStations);
        System.out.println("Total Nodes: " + Datasetreader.nodes.size());
        System.out.println("\n=== Nodes ===");
        for (var node : Datasetreader.nodes) {
            System.out.printf("ID: %d | (%.2f, %.2f) | Type: %s\n",
                    node.id, node.x, node.y, node.type);
        }
    }
}