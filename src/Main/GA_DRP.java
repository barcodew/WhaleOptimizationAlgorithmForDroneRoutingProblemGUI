package Main;

import java.util.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import Helpers.Datasetreader;
import Helpers.MatrixBuilder;

/**
 * Genetic Algorithm untuk Drone Routing Problem
 * Komponen evaluasi fitness identik dengan WOA (DRP_Full.java)
 * agar perbandingan fair.
 *
 * Komponen GA:
 * - Representasi: permutasi langsung (integer array)
 * - Seleksi: Tournament Selection (size 3)
 * - Crossover: Order Crossover (OX)
 * - Mutasi: swap / insert / inverse 
 * - Elitism: simpan individu terbaik
 */
public class GA_DRP {

    static final double ALPHA = 20.0;
    static final double BATTERY_CAPACITY = 1000.0;
    static final int MAX_DEPTH = 3;
    static final double CROSSOVER_RATE = 0.9;
    static final double MUTATION_RATE = 0.3;
    static final int TOURNAMENT_SIZE = 3;

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

        int totalRuns = 30;
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String prefix = "hasil_GA_" + ts;

        ArrayList<ScenarioResult> all = new ArrayList<>();

        for (String dataset : datasets) {
            Datasetreader.read(dataset);
            double[][] adj = MatrixBuilder.generate(Datasetreader.nodes);
            int nc = Datasetreader.numberOfCustomers;
            int ncs = Datasetreader.numberOfChargingStations;
            String dsName = dataset.substring(dataset.lastIndexOf("\\") + 1).replace(".csv", "");

            System.out.println("\n" + "=".repeat(60));
            System.out.println("GA — DATASET: " + dsName);
            System.out.println("=".repeat(60));

            for (int[] sk : skenario) {
                int pop = sk[0], gen = sk[1];
                System.out.printf("\n--- GA: P=%d, G=%d ---\n", pop, gen);

                ScenarioResult sc = new ScenarioResult();
                sc.popSize = pop;
                sc.maxIter = gen;
                sc.numCustomers = nc;
                sc.numCS = ncs;
                sc.datasetName = dsName;
                sc.runs = new RunResult[totalRuns];

                for (int run = 0; run < totalRuns; run++) {
                    System.out.printf("  Run %2d/%d ... ", run + 1, totalRuns);
                    RunResult r = runGA(adj, nc, ncs, pop, gen);
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
        printTabel(all, "GENETIC ALGORITHM");
    }

    // ============================================================
    // GA UTAMA
    // ============================================================
    static RunResult runGA(double[][] adj, int numCust, int numCS, int popSize, int maxGen) {
        long start = System.currentTimeMillis();
        int n = numCust;
        double[] convergence = new double[maxGen];

        // === INISIALISASI POPULASI ===
        int[][] population = new int[popSize][n];
        double[] fitnessAll = new double[popSize];

        for (int i = 0; i < popSize; i++) {
            population[i] = randomPermutation(n);
            fitnessAll[i] = evaluateFitnessSilent(population[i], adj, numCust, numCS);
        }

        int bestIdx = 0;
        double bestFitness = -Double.MAX_VALUE;
        for (int i = 0; i < popSize; i++) {
            if (fitnessAll[i] > bestFitness) {
                bestFitness = fitnessAll[i];
                bestIdx = i;
            }
        }
        int[] bestChromosome = population[bestIdx].clone();
        double fitnessBest = bestFitness;

        // === LOOP GENERASI ===
        for (int gen = 1; gen <= maxGen; gen++) {
            int[][] newPop = new int[popSize][n];
            double[] newFit = new double[popSize];

            // Elitism: simpan individu terbaik
            newPop[0] = bestChromosome.clone();
            newFit[0] = fitnessBest;

            for (int i = 1; i < popSize; i++) {
                // Seleksi: Tournament
                int p1 = tournamentSelect(fitnessAll, popSize);
                int p2 = tournamentSelect(fitnessAll, popSize);

                int[] child;

                // Crossover: Order Crossover (OX)
                if (Math.random() < CROSSOVER_RATE) {
                    child = orderCrossover(population[p1], population[p2]);
                } else {
                    child = population[p1].clone();
                }

                // Mutasi: swap / insert / inverse
                if (Math.random() < MUTATION_RATE) {
                    child = mutate(child);
                }

                newPop[i] = child;
                newFit[i] = evaluateFitnessSilent(child, adj, numCust, numCS);
            }

            population = newPop;
            fitnessAll = newFit;

            // Update best
            for (int i = 0; i < popSize; i++) {
                if (fitnessAll[i] > fitnessBest) {
                    fitnessBest = fitnessAll[i];
                    bestChromosome = population[i].clone();
                }
            }

            convergence[gen - 1] = fitnessBest;
        }

        // === HASIL ===
        RunResult result = new RunResult();
        result.fitness = fitnessBest;
        result.permutation = bestChromosome;
        result.convergence = convergence;
        result.computeTimeMs = System.currentTimeMillis() - start;
        buildFullRoute(bestChromosome, adj, numCust, numCS, result);
        return result;
    }

    // ============================================================
    // OPERATOR GA
    // ============================================================

    // Random permutation [1, 2, ..., n] shuffled
    static int[] randomPermutation(int n) {
        int[] perm = new int[n];
        for (int i = 0; i < n; i++)
            perm[i] = i + 1;
        for (int i = n - 1; i > 0; i--) {
            int j = (int) (Math.random() * (i + 1));
            int tmp = perm[i];
            perm[i] = perm[j];
            perm[j] = tmp;
        }
        return perm;
    }

    // Tournament selection
    static int tournamentSelect(double[] fitness, int popSize) {
        int best = (int) (Math.random() * popSize);
        for (int i = 1; i < TOURNAMENT_SIZE; i++) {
            int candidate = (int) (Math.random() * popSize);
            if (fitness[candidate] > fitness[best])
                best = candidate;
        }
        return best;
    }

    // Order Crossover (OX) — standar untuk permutasi
    static int[] orderCrossover(int[] p1, int[] p2) {
        int n = p1.length;
        int[] child = new int[n];
        Arrays.fill(child, -1);

        // Pilih segmen acak dari parent1
        int start = (int) (Math.random() * n);
        int end = (int) (Math.random() * n);
        if (start > end) {
            int tmp = start;
            start = end;
            end = tmp;
        }

        // Copy segmen dari parent1
        Set<Integer> used = new HashSet<>();
        for (int i = start; i <= end; i++) {
            child[i] = p1[i];
            used.add(p1[i]);
        }

        // Isi sisanya dari parent2 (urutan)
        int pos = (end + 1) % n;
        for (int i = 0; i < n; i++) {
            int idx = (end + 1 + i) % n;
            int gene = p2[idx];
            if (!used.contains(gene)) {
                child[pos] = gene;
                pos = (pos + 1) % n;
            }
        }

        return child;
    }

    // Mutasi: swap / insert / inverse 
    static int[] mutate(int[] perm) {
        int[] result = perm.clone();
        int n = result.length;
        double r = Math.random();
        int k = (int) (Math.random() * n);
        int l = (int) (Math.random() * n);
        while (l == k)
            l = (int) (Math.random() * n);

        if (r < 1.0 / 3) {
            // Swap
            int tmp = result[k];
            result[k] = result[l];
            result[l] = tmp;
        } else if (r < 2.0 / 3) {
            // Insert
            int val = result[k];
            ArrayList<Integer> list = new ArrayList<>();
            for (int v : result)
                list.add(v);
            list.remove(k);
            if (l > k)
                l--;
            if (l >= list.size())
                l = list.size() - 1;
            list.add(l, val);
            for (int i = 0; i < n; i++)
                result[i] = list.get(i);
        } else {
            // Inverse
            if (k > l) {
                int tmp = k;
                k = l;
                l = tmp;
            }
            while (k < l) {
                int tmp = result[k];
                result[k] = result[l];
                result[l] = tmp;
                k++;
                l--;
            }
        }
        return result;
    }

    // ============================================================
    //  EVALUASI - Hindia :v
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
            System.out.println("✓ GA Ringkasan: " + fn);
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

            // cari panjang maksimum
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

            System.out.println("✓ GA Konvergensi (FIXED): " + fn);

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
            System.out.println("✓ GA Rute: " + fn);
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
