package Main;

import java.util.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import Helpers.Datasetreader;
import Helpers.MatrixBuilder;

/**
 * Particle Swarm Optimization untuk Drone Routing Problem
 * Komponen evaluasi fitness identik dengan WOA 
 * agar perbandingan fair.
 *
 * Komponen PSO:
 * - Representasi: vektor real + LOV decode (sama dengan WOA)
 * - Velocity update: v = w*v + c1*r1*(pbest-x) + c2*r2*(gbest-x)
 * - Inertia weight: w turun linear dari 0.9 ke 0.4
 * - Cognitive & social: c1 = c2 = 2.0
 * - Clipping posisi ke [-1, 1]
 */
public class PSO_DRP {

    static final double ALPHA = 20.0;
    static final double BATTERY_CAPACITY = 4000.0;
    static final int MAX_DEPTH = 3;

    // Parameter PSO
    static final double W_MAX = 0.9; // inertia weight awal
    static final double W_MIN = 0.4; // inertia weight akhir
    static final double C1 = 2.0; // cognitive coefficient
    static final double C2 = 2.0; // social coefficient
    static final double V_MAX = 1.0; // velocity clamp

    static class RunResult {
        double fitness, totalDistance, totalEnergy;
        int rechargeCount;
        boolean feasible;
        ArrayList<Integer> route;
        int[] permutation;
        double[] convergence;
        long computeTimeMs;
    }

    static class ScenarioResult {
        int popSize, maxIter, numCustomers, numCS;
        String datasetName;
        RunResult[] runs;
        double avgFitness, bestFitness, worstFitness, stdFitness;
        double avgDistance, bestDistance, avgTime;
        int feasibleCount;

        void calculate() {
            int n = runs.length;
            bestFitness = 0;
            worstFitness = Double.MAX_VALUE;
            double sf = 0, sd = 0, st = 0;
            feasibleCount = 0;
            for (RunResult r : runs) {
                sf += r.fitness;
                sd += r.totalDistance;
                st += r.computeTimeMs;
                if (r.fitness > bestFitness)
                    bestFitness = r.fitness;
                if (r.fitness < worstFitness)
                    worstFitness = r.fitness;
                if (r.feasible)
                    feasibleCount++;
            }
            avgFitness = sf / n;
            avgDistance = sd / n;
            avgTime = st / n;
            RunResult br = runs[0];
            for (RunResult r : runs)
                if (r.fitness > br.fitness)
                    br = r;
            bestDistance = br.totalDistance;
            double sq = 0;
            for (RunResult r : runs)
                sq += Math.pow(r.fitness - avgFitness, 2);
            stdFitness = Math.sqrt(sq / n);
        }
    }

    // ============================================================
    // MAIN
    // ============================================================
    public static void main(String[] args) {
        String[] datasets = {
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\10_Customers.csv",
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\20_Customers.csv",
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\30_Customers.csv",
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\40_Customers.csv",
                "E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\50_Customers.csv",
        };

        int[][] skenario = {
                { 20, 200 }, { 20, 500 }, { 20, 1000 },
                { 50, 200 }, { 50, 500 }, { 50, 1000 },
                { 100, 200 }, { 100, 500 }, { 100, 1000 },
        };

        int totalRuns = 10;
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String prefix = "hasil_PSO_" + ts;

        ArrayList<ScenarioResult> all = new ArrayList<>();

        for (String dataset : datasets) {
            Datasetreader.read(dataset);
            double[][] adj = MatrixBuilder.generate(Datasetreader.nodes);
            int nc = Datasetreader.numberOfCustomers;
            int ncs = Datasetreader.numberOfChargingStations;
            String dsName = dataset.substring(dataset.lastIndexOf("\\") + 1).replace(".csv", "");

            System.out.println("\n" + "=".repeat(60));
            System.out.println("PSO — DATASET: " + dsName);
            System.out.println("=".repeat(60));

            for (int[] sk : skenario) {
                int pop = sk[0], iter = sk[1];
                System.out.printf("\n--- PSO: P=%d, Iter=%d ---\n", pop, iter);

                ScenarioResult sc = new ScenarioResult();
                sc.popSize = pop;
                sc.maxIter = iter;
                sc.numCustomers = nc;
                sc.numCS = ncs;
                sc.datasetName = dsName;
                sc.runs = new RunResult[totalRuns];

                for (int run = 0; run < totalRuns; run++) {
                    System.out.printf("  Run %2d/%d ... ", run + 1, totalRuns);
                    RunResult r = runPSO(adj, nc, ncs, pop, iter);
                    sc.runs[run] = r;
                    System.out.printf("Fitness: %.6f | Jarak: %.2f km | Waktu: %d ms\n",
                            r.fitness, r.totalDistance, r.computeTimeMs);
                }

                sc.calculate();
                all.add(sc);
                System.out.printf("  Best: %.6f (%.2f km) | Avg: %.6f | Feasible: %d/%d\n",
                        sc.bestFitness, sc.bestDistance, sc.avgFitness, sc.feasibleCount, totalRuns);
            }
        }

        exportCSV(all, prefix + "_ringkasan.csv");
        exportKonvergensi(all, prefix + "_konvergensi.csv");
        exportRute(all, prefix + "_rute_terbaik.csv");
        printTabel(all, "PARTICLE SWARM OPTIMIZATION");
    }

    // ============================================================
    // PSO UTAMA
    // ============================================================
    static RunResult runPSO(double[][] adj, int numCust, int numCS, int popSize, int maxIter) {
        long start = System.currentTimeMillis();
        int n = numCust;
        double[] convergence = new double[maxIter];

        // === INISIALISASI ===
        double[][] positions = new double[popSize][n]; // posisi partikel
        double[][] velocities = new double[popSize][n]; // velocity
        double[][] pBest = new double[popSize][n]; // personal best position
        double[] pBestFit = new double[popSize]; // personal best fitness
        double[] fitnessAll = new double[popSize];

        // Inisialisasi posisi acak [-1, 1] dan velocity acak kecil
        for (int i = 0; i < popSize; i++) {
            for (int g = 0; g < n; g++) {
                positions[i][g] = Math.random() * 2 - 1;
                velocities[i][g] = (Math.random() * 2 - 1) * 0.1; // velocity awal kecil
            }
            pBest[i] = positions[i].clone();
        }

        // Evaluasi awal
        double[] gBest = null;
        double gBestFit = -Double.MAX_VALUE;

        for (int i = 0; i < popSize; i++) {
            int[] perm = LOVdecode(positions[i]);
            fitnessAll[i] = evaluateFitnessSilent(perm, adj, numCust, numCS);
            pBestFit[i] = fitnessAll[i];

            if (fitnessAll[i] > gBestFit) {
                gBestFit = fitnessAll[i];
                gBest = positions[i].clone();
            }
        }

        // === LOOP ITERASI ===
        for (int t = 1; t <= maxIter; t++) {
            // Inertia weight turun linear
            double w = W_MAX - (W_MAX - W_MIN) * ((double) t / maxIter);

            for (int i = 0; i < popSize; i++) {
                double r1 = Math.random();
                double r2 = Math.random();

                for (int g = 0; g < n; g++) {
                    // Update velocity
                    velocities[i][g] = w * velocities[i][g]
                            + C1 * r1 * (pBest[i][g] - positions[i][g])
                            + C2 * r2 * (gBest[g] - positions[i][g]);

                    // Clamp velocity
                    velocities[i][g] = Math.max(-V_MAX, Math.min(V_MAX, velocities[i][g]));

                    // Update posisi
                    positions[i][g] += velocities[i][g];

                    // Clamp posisi ke [-1, 1]
                    positions[i][g] = Math.max(-1, Math.min(1, positions[i][g]));
                }

                // Evaluasi
                int[] perm = LOVdecode(positions[i]);
                fitnessAll[i] = evaluateFitnessSilent(perm, adj, numCust, numCS);

                // Update personal best
                if (fitnessAll[i] > pBestFit[i]) {
                    pBestFit[i] = fitnessAll[i];
                    pBest[i] = positions[i].clone();
                }

                // Update global best
                if (fitnessAll[i] > gBestFit) {
                    gBestFit = fitnessAll[i];
                    gBest = positions[i].clone();
                }
            }

            convergence[t - 1] = gBestFit;
        }

        // === HASIL ===
        RunResult result = new RunResult();
        result.fitness = gBestFit;
        int[] bestPerm = LOVdecode(gBest);
        result.permutation = bestPerm;
        result.convergence = convergence;
        result.computeTimeMs = System.currentTimeMillis() - start;
        buildFullRoute(bestPerm, adj, numCust, numCS, result);
        return result;
    }

    // ============================================================
    // LOV DECODE 
    // ============================================================
    static int[] LOVdecode(double[] whale) {
        int n = whale.length;
        int[] phi = new int[n];
        for (int g = 0; g < n; g++) {
            int rank = 1;
            for (int j = 0; j < n; j++) {
                if (whale[j] > whale[g] || (whale[j] == whale[g] && j < g))
                    rank++;
            }
            phi[g] = rank;
        }
        int[] perm = new int[n];
        for (int g = 0; g < n; g++)
            perm[phi[g] - 1] = g + 1;
        return perm;
    }

    // ============================================================
    //  EVALUASI 
    // ============================================================

    static double evaluateFitnessSilent(int[] perm, double[][] adj, int nc, int ncs) {
        ArrayList<Integer> route = new ArrayList<>();
        route.add(0);
        double battery = BATTERY_CAPACITY, totalEnergy = 0;
        boolean feasible = true;
        int[] option = buildCSOptions(nc, ncs);

        for (int i = 0; i < perm.length; i++) {
            int origin = route.get(route.size() - 1), dest = perm[i];
            double en = adj[origin][dest] * ALPHA;
            boolean reach = en <= battery, safe = false;

            if (reach) {
                double ba = battery - en;
                if (ba >= adj[dest][0] * ALPHA)
                    safe = true;
                if (!safe)
                    for (int k = nc + 1; k <= nc + ncs; k++)
                        if (ba >= adj[dest][k] * ALPHA) {
                            safe = true;
                            break;
                        }
            }

            if (reach && safe) {
                battery -= en;
                totalEnergy += en;
                route.add(dest);
            } else {
                int[] cr = steppingStone(option, origin, dest, adj, battery, BATTERY_CAPACITY, MAX_DEPTH);
                if (cr.length > 0) {
                    int cur = origin;
                    for (int cs : cr) {
                        battery -= adj[cur][cs] * ALPHA;
                        totalEnergy += adj[cur][cs] * ALPHA;
                        route.add(cs);
                        battery = BATTERY_CAPACITY;
                        cur = cs;
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
                    int[] cr = steppingStone(option, last, 0, adj, battery, BATTERY_CAPACITY, MAX_DEPTH);
                    if (cr.length > 0) {
                        int cur = last;
                        for (int cs : cr) {
                            totalEnergy += adj[cur][cs] * ALPHA;
                            route.add(cs);
                            battery = BATTERY_CAPACITY;
                            cur = cs;
                        }
                        totalEnergy += adj[cur][0] * ALPHA;
                        route.add(0);
                    } else
                        feasible = false;
                }
            }
        }
        return feasible ? 1.0 / (totalEnergy + 1) : 1e-10;
    }

    static int[] steppingStone(int[] option, int origin, int dest,
            double[][] adj, double remBat, double cap, int maxD) {
        Queue<ArrayList<Integer>> q = new LinkedList<>();
        for (int o : option) {
            ArrayList<Integer> c = new ArrayList<>();
            c.add(o);
            q.add(c);
        }
        ArrayList<Integer> best = new ArrayList<>();
        double bestE = Double.MAX_VALUE;
        int bestDepth = maxD + 1;

        while (!q.isEmpty()) {
            ArrayList<Integer> h = q.poll();
            if (h.size() >= bestDepth)
                continue;
            double te = 0, rem = remBat;
            int ori = origin;
            boolean ok = true;
            for (int nd : h) {
                double e = adj[ori][nd] * ALPHA;
                if (e <= rem) {
                    te += e;
                    rem = cap;
                    ori = nd;
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                double e = adj[ori][dest] * ALPHA;
                if (e <= rem) {
                    te += e;
                    if (te < bestE) {
                        bestE = te;
                        best = h;
                        bestDepth = h.size();
                    }
                }
            }
            if (h.size() < maxD && h.size() < bestDepth)
                for (int o : option)
                    if (!h.contains(o)) {
                        ArrayList<Integer> c = new ArrayList<>(h);
                        c.add(o);
                        q.add(c);
                    }
        }
        return best.stream().mapToInt(Integer::intValue).toArray();
    }

    static void buildFullRoute(int[] perm, double[][] adj, int nc, int ncs, RunResult r) {
        ArrayList<Integer> route = new ArrayList<>();
        route.add(0);
        double battery = BATTERY_CAPACITY;
        boolean feasible = true;
        int rc = 0;
        int[] option = buildCSOptions(nc, ncs);

        for (int i = 0; i < perm.length; i++) {
            int origin = route.get(route.size() - 1), dest = perm[i];
            double en = adj[origin][dest] * ALPHA;
            boolean reach = en <= battery, safe = false;
            if (reach) {
                double ba = battery - en;
                if (ba >= adj[dest][0] * ALPHA)
                    safe = true;
                if (!safe)
                    for (int k = nc + 1; k <= nc + ncs; k++)
                        if (ba >= adj[dest][k] * ALPHA) {
                            safe = true;
                            break;
                        }
            }
            if (reach && safe) {
                battery -= en;
                route.add(dest);
            } else {
                int[] cr = steppingStone(option, origin, dest, adj, battery, BATTERY_CAPACITY, MAX_DEPTH);
                if (cr.length > 0) {
                    int cur = origin;
                    for (int cs : cr) {
                        battery -= adj[cur][cs] * ALPHA;
                        route.add(cs);
                        battery = BATTERY_CAPACITY;
                        rc++;
                        cur = cs;
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
                if (e <= battery)
                    route.add(0);
                else {
                    int[] cr = steppingStone(option, last, 0, adj, battery, BATTERY_CAPACITY, MAX_DEPTH);
                    if (cr.length > 0) {
                        int cur = last;
                        for (int cs : cr) {
                            route.add(cs);
                            battery = BATTERY_CAPACITY;
                            rc++;
                            cur = cs;
                        }
                        route.add(0);
                    } else
                        feasible = false;
                }
            }
        }
        r.route = route;
        r.feasible = feasible;
        r.rechargeCount = rc;
        double td = 0, te = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            double d = adj[route.get(i)][route.get(i + 1)];
            td += d;
            te += d * ALPHA;
        }
        r.totalDistance = td;
        r.totalEnergy = te;
    }

    static int[] buildCSOptions(int nc, int ncs) {
        int[] o = new int[ncs];
        for (int k = 0; k < ncs; k++)
            o[k] = nc + k + 1;
        return o;
    }

    static String getLabel(int id, int nc) {
        return id == 0 ? "V0(Depot)" : id > nc ? "CS" + (id - nc) : "V" + id;
    }

    // ============================================================
    // EXPORT 
    // ============================================================
    static void exportCSV(ArrayList<ScenarioResult> res, String fn) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fn))) {
            pw.println(
                    "No,Dataset,Customers,CS,PopSize,MaxIter,BestFitness,AvgFitness,StdFitness,WorstFitness,BestDistance,AvgDistance,FeasibleCount,TotalRuns,AvgTimeMs");
            int no = 1;
            for (ScenarioResult s : res)
                pw.printf("%d,%s,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.2f,%.2f,%d,%d,%.0f\n",
                        no++, s.datasetName, s.numCustomers, s.numCS, s.popSize, s.maxIter,
                        s.bestFitness, s.avgFitness, s.stdFitness, s.worstFitness, s.bestDistance, s.avgDistance,
                        s.feasibleCount, s.runs.length, s.avgTime);
            System.out.println("✓ PSO Ringkasan: " + fn);
        } catch (Exception e) {
            System.out.println("✗ " + e.getMessage());
        }
    }

    static void exportKonvergensi(ArrayList<ScenarioResult> res, String fn) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fn))) {

            // HEADER
            pw.print("Iterasi");
            for (ScenarioResult s : res)
                pw.printf(",P%d_G%d_%s", s.popSize, s.maxIter, s.datasetName);
            pw.println();

            // cari iterasi maksimum
            int maxLen = 0;
            for (ScenarioResult s : res)
                for (RunResult r : s.runs)
                    maxLen = Math.max(maxLen, r.convergence.length);

            // LOOP ITERASI
            for (int t = 0; t < maxLen; t++) {
                pw.print(t + 1);

                for (ScenarioResult s : res) {

                    double sum = 0;
                    int count = 0;

                    // ambil semua run
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

            System.out.println("✓ PSO Konvergensi (FIXED AVG): " + fn);

        } catch (Exception e) {
            System.out.println("✗ " + e.getMessage());
        }
    }

    static void exportRute(ArrayList<ScenarioResult> res, String fn) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(fn))) {
            pw.println("No,Dataset,PopSize,MaxIter,Fitness,TotalDistance,TotalEnergy,RechargeCount,Feasible,Route");
            int no = 1;
            for (ScenarioResult s : res) {
                RunResult b = s.runs[0];
                for (RunResult r : s.runs)
                    if (r.fitness > b.fitness)
                        b = r;
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < b.route.size(); i++) {
                    if (i > 0)
                        sb.append(" -> ");
                    sb.append(getLabel(b.route.get(i), s.numCustomers));
                }
                pw.printf("%d,%s,%d,%d,%.6f,%.2f,%.2f,%d,%s,\"%s\"\n",
                        no++, s.datasetName, s.popSize, s.maxIter, b.fitness, b.totalDistance, b.totalEnergy,
                        b.rechargeCount, b.feasible ? "YA" : "TIDAK", sb);
            }
            System.out.println("✓ PSO Rute: " + fn);
        } catch (Exception e) {
            System.out.println("✗ " + e.getMessage());
        }
    }

    static void printTabel(ArrayList<ScenarioResult> res, String title) {
        System.out.println("\n" + "=".repeat(120));
        System.out.println("                    TABEL HASIL — " + title);
        System.out.println("=".repeat(120));
        System.out.printf("%-4s %-18s %5s %6s %12s %12s %12s %10s %10s %10s\n",
                "No", "Dataset", "P", "G", "Best Fit", "Avg Fit", "Std Fit", "Best Dist", "Feasible", "Avg Time");
        System.out.println("-".repeat(120));
        int no = 1;
        for (ScenarioResult s : res)
            System.out.printf("%-4d %-18s %5d %6d %12.6f %12.6f %12.6f %8.2f km %7d/%d %7.0f ms\n",
                    no++, s.datasetName, s.popSize, s.maxIter, s.bestFitness, s.avgFitness, s.stdFitness,
                    s.bestDistance, s.feasibleCount, s.runs.length, s.avgTime);
        System.out.println("=".repeat(120));
    }
}
