package Main;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import Helpers.Datasetreader;
import Helpers.MatrixBuilder;

public class DRP {

    // === KONSTANTA ===
    static final double ALPHA = 20.0;
    static final double BATTERY_CAPACITY = 4000.0;
    static final int MAX_DEPTH = 3;

    public static void main(String[] args) {

        Datasetreader.read("E:\\Skripsian\\WOA\\DRP_MANUAL\\DRP_MANUAL\\src\\Datasets\\10_Customers.csv");
        double[][] adj = MatrixBuilder.generate(Datasetreader.nodes);
        int numCust = Datasetreader.numberOfCustomers;
        int numCS = Datasetreader.numberOfChargingStations;

        printDataset();

        int popSize = 20;
        int maxIter = 200;

        runWOA(adj, numCust, numCS, popSize, maxIter);
    }

    // ============================================================
    // ALGORITMA UTAMA WOA
    // ============================================================
    static void runWOA(double[][] adj, int numCustomers, int numCS,
            int popSize, int maxIter) {

        int n = numCustomers;
        double b = 1.0;

        // === FASE 1: INISIALISASI POPULASI ===
        System.out.println("\n=== INISIALISASI POPULASI ===");
        double[][] population = new double[popSize][n];
        double[] fitnessAll = new double[popSize];
        int[][] permutasiAll = new int[popSize][n];

        for (int i = 0; i < popSize; i++) {
            population[i] = randomWhale(n);
        }

        // === FASE 2: EVALUASI AWAL ===
        int bestIdx = 0;
        double bestFitness = 0; // mulai dari 0, cari TERBESAR

        for (int i = 0; i < popSize; i++) {
            permutasiAll[i] = LOVdecode(population[i]);
            fitnessAll[i] = evaluateFitnessSilent(
                    permutasiAll[i], adj, numCustomers, numCS);

            if (fitnessAll[i] > bestFitness) { // cari TERBESAR
                bestFitness = fitnessAll[i];
                bestIdx = i;
            }
        }

        double[] Xstar = population[bestIdx].clone();
        double fitnessStar = bestFitness;

        System.out.printf("Populasi awal: %d whale\n", popSize);
        System.out.printf("Best awal: Whale-%d | Fitness: %.6f\n\n", bestIdx, fitnessStar);

        // === FASE 3: ITERASI UTAMA ===
        for (int t = 1; t <= maxIter; t++) {

            double a = 2.0 - 2.0 * ((double) t / maxIter);

            for (int i = 0; i < popSize; i++) {

                double r1 = Math.random();
                double r2 = Math.random();
                double p = Math.random();
                double l = Math.random() * 2 - 1;

                double A = 2 * a * r1 - a;
                double C = 2 * r2;

                // === PEMBARUAN POSISI ===
                double[] newPos = new double[n];

                if (Math.abs(A) < 1) {
                    if (p < 0.5) {
                        // Encircling Prey
                        for (int g = 0; g < n; g++) {
                            double D = Math.abs(C * Xstar[g] - population[i][g]);
                            newPos[g] = Xstar[g] - A * D;
                        }
                    } else {
                        // Spiral Bubble-Net
                        for (int g = 0; g < n; g++) {
                            double Dprime = Math.abs(Xstar[g] - population[i][g]);
                            newPos[g] = Dprime * Math.exp(b * l)
                                    * Math.cos(2 * Math.PI * l)
                                    + Xstar[g];
                        }
                    }
                } else {
                    // Exploration
                    int randIdx = (int) (Math.random() * popSize);
                    double[] Xrand = population[randIdx];
                    for (int g = 0; g < n; g++) {
                        double D = Math.abs(C * Xrand[g] - population[i][g]);
                        newPos[g] = Xrand[g] - A * D;
                    }
                }

                // === CLIPPING ===
                for (int g = 0; g < n; g++) {
                    newPos[g] = Math.max(-1, Math.min(1, newPos[g]));
                }

                // === LOV DECODE ===
                int[] newPerm = LOVdecode(newPos);

                // === LOCAL SEARCH ===
                int[] improvedPerm = localSearch(newPerm, adj, numCustomers, numCS);

                // === EVALUASI FITNESS ===
                double newFitness = evaluateFitnessSilent(
                        improvedPerm, adj, numCustomers, numCS);

                // === TERIMA / TOLAK ===
                if (newFitness > fitnessAll[i]) { // LEBIH BESAR = lebih baik
                    population[i] = RLOVencode(improvedPerm);
                    permutasiAll[i] = improvedPerm;
                    fitnessAll[i] = newFitness;
                }
            }

            // === UPDATE X* ===
            for (int i = 0; i < popSize; i++) {
                if (fitnessAll[i] > fitnessStar) { // LEBIH BESAR = lebih baik
                    fitnessStar = fitnessAll[i];
                    Xstar = population[i].clone();
                    bestIdx = i;
                }
            }

            if (t % 10 == 0 || t == 1 || t == maxIter) {
                double bestDist = (fitnessStar > 0) ? (1.0 / fitnessStar) - 1 : 0;
                System.out.printf("Iterasi %4d/%d | Fitness: %.6f | Jarak: %.2f km | a: %.4f\n",
                        t, maxIter, fitnessStar, bestDist, a);
            }
        }

        // === HASIL AKHIR ===
        System.out.println("\n========================================");
        System.out.println("          HASIL AKHIR WOA");
        System.out.println("========================================");

        int[] bestPerm = LOVdecode(Xstar);
        evaluateFitness(bestPerm, adj, numCustomers, numCS);

        double bestDist = (fitnessStar > 0) ? (1.0 / fitnessStar) - 1 : 0;
        double bestEnergy = bestDist * ALPHA;
        System.out.printf("\nFitness terbaik : %.6f\n", fitnessStar);
        System.out.printf("Total jarak     : %.2f km\n", bestDist);
        System.out.printf("Total energi    : %.2f Wh\n", bestEnergy);
    }

    // ============================================================
    // INISIALISASI — Random whale [0, 1]
    // ============================================================
    static double[] randomWhale(int numCustomers) {
        double[] whale = new double[numCustomers];
        for (int i = 0; i < whale.length; i++) {
            whale[i] = Math.random();
        }
        return whale;
    }

    // ============================================================
    // LOV DECODE — Real vector → Permutasi (dengan tiebreaker)
    // ============================================================
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

    // ============================================================
    // RLOV ENCODE — Permutasi → Real vector
    // ============================================================
    static double[] RLOVencode(int[] permutation) {
        int n = permutation.length;
        double[] whale = new double[n];
        double step = 1.0 / n;

        for (int pos = 0; pos < n; pos++) {
            int customerId = permutation[pos];
            whale[customerId - 1] = 1.0 - (pos * step);
        }
        return whale;
    }

    // ============================================================
    // EVALUASI FITNESS (dengan print trace)
    // ============================================================
    static double evaluateFitness(int[] permutation, double[][] adj,
            int numCustomers, int numCS) {

        ArrayList<Integer> route = new ArrayList<>();
        route.add(0);

        double battery = BATTERY_CAPACITY;
        double totalEnergy = 0.0;
        boolean feasible = true;
        int rechargeCount = 0;
        int[] option = buildCSOptions(numCustomers, numCS);

        System.out.println("\n=== Evaluasi Fitness ===");
        System.out.printf("  Mulai di V0 (Depot) | Battery: %.2f Wh\n", battery);

        for (int i = 0; i < permutation.length; i++) {
            int origin = route.get(route.size() - 1);
            int destination = permutation[i];

            double distToDest = adj[origin][destination];
            double energyToDest = distToDest * ALPHA;

            boolean isReachable = (energyToDest <= battery);
            boolean isSafe = false;

            if (isReachable) {
                double batteryAfter = battery - energyToDest;

                if (batteryAfter >= adj[destination][0] * ALPHA) {
                    isSafe = true;
                }

                if (!isSafe) {
                    for (int k = numCustomers + 1; k <= numCustomers + numCS; k++) {
                        if (batteryAfter >= adj[destination][k] * ALPHA) {
                            isSafe = true;
                            break;
                        }
                    }
                }
            }

            if (isReachable && isSafe) {
                battery -= energyToDest;
                totalEnergy += energyToDest;
                route.add(destination);
                System.out.printf("  → V%d | jarak: %.2f km | energi: %.2f Wh | sisa: %.2f Wh\n",
                        destination, distToDest, energyToDest, battery);
            } else {
                int[] chargingRoute = steppingStone(
                        option, origin, destination, adj,
                        battery, BATTERY_CAPACITY, MAX_DEPTH);

                if (chargingRoute.length > 0) {
                    int currentOrigin = origin;

                    for (int k = 0; k < chargingRoute.length; k++) {
                        int csNode = chargingRoute[k];
                        double distToCS = adj[currentOrigin][csNode];
                        double energyToCS = distToCS * ALPHA;

                        battery -= energyToCS;
                        totalEnergy += energyToCS;
                        route.add(csNode);
                        rechargeCount++;

                        System.out.printf("  ⚡ %s | jarak: %.2f km | sisa: %.2f → %.2f Wh (recharge)\n",
                                getLabel(csNode, numCustomers), distToCS,
                                battery, BATTERY_CAPACITY);

                        battery = BATTERY_CAPACITY;
                        currentOrigin = csNode;
                    }

                    double distFromCS = adj[currentOrigin][destination];
                    double energyFromCS = distFromCS * ALPHA;
                    battery -= energyFromCS;
                    totalEnergy += energyFromCS;
                    route.add(destination);

                    System.out.printf("  → V%d | jarak: %.2f km | energi: %.2f Wh | sisa: %.2f Wh\n",
                            destination, distFromCS, energyFromCS, battery);
                } else {
                    System.out.printf("  ⚠ INFEASIBLE: %s → V%d\n",
                            getLabel(origin, numCustomers), destination);
                    feasible = false;
                    break;
                }
            }
        }

        // Kembali ke depot
        if (feasible) {
            int lastNode = route.get(route.size() - 1);
            if (lastNode != 0) {
                double distToDepot = adj[lastNode][0];
                double energyToDepot = distToDepot * ALPHA;

                if (energyToDepot <= battery) {
                    battery -= energyToDepot;
                    totalEnergy += energyToDepot;
                    route.add(0);
                    System.out.printf("  → V0 (Depot) | jarak: %.2f km | sisa: %.2f Wh\n",
                            distToDepot, battery);
                } else {
                    int[] chargingRoute = steppingStone(
                            option, lastNode, 0, adj,
                            battery, BATTERY_CAPACITY, MAX_DEPTH);

                    if (chargingRoute.length > 0) {
                        int currentOrigin = lastNode;
                        for (int k = 0; k < chargingRoute.length; k++) {
                            int csNode = chargingRoute[k];
                            battery -= adj[currentOrigin][csNode] * ALPHA;
                            totalEnergy += adj[currentOrigin][csNode] * ALPHA;
                            route.add(csNode);
                            rechargeCount++;

                            System.out.printf("  ⚡ %s (pulang) | sisa: %.2f → %.2f Wh\n",
                                    getLabel(csNode, numCustomers), battery, BATTERY_CAPACITY);

                            battery = BATTERY_CAPACITY;
                            currentOrigin = csNode;
                        }

                        double finalDist = adj[currentOrigin][0];
                        battery -= finalDist * ALPHA;
                        totalEnergy += finalDist * ALPHA;
                        route.add(0);
                        System.out.printf("  → V0 (Depot) | jarak: %.2f km | sisa: %.2f Wh\n",
                                finalDist, battery);
                    } else {
                        System.out.println("  ⚠ INFEASIBLE: tidak bisa pulang");
                        feasible = false;
                    }
                }
            }
        }

        // Hitung total
        double totalDistance = 0;
        for (int k = 0; k < route.size() - 1; k++) {
            totalDistance += adj[route.get(k)][route.get(k + 1)];
        }

        double fitness;
        if (feasible) {
            fitness = 1.0 / (totalDistance + 1);
        } else {
            fitness = 0;
        }

        // Ringkasan
        System.out.println("\n--- Ringkasan ---");
        System.out.print("  Rute: ");
        for (int i = 0; i < route.size(); i++) {
            if (i > 0)
                System.out.print(" → ");
            System.out.print(getLabel(route.get(i), numCustomers));
        }
        System.out.println();
        System.out.printf("  Total jarak  : %.2f km\n", totalDistance);
        System.out.printf("  Total energi : %.2f Wh\n", totalEnergy);
        System.out.printf("  Recharge     : %d kali\n", rechargeCount);
        System.out.printf("  Feasible     : %s\n", feasible ? "YA" : "TIDAK");
        System.out.printf("  Fitness      : %.6f\n", fitness);

        return fitness;
    }

    // ============================================================
    // EVALUASI FITNESS SILENT ( untuk LS dan WOA loop)
    // ============================================================
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

        double totalDist = 0;
        for (int i = 0; i < route.size() - 1; i++)
            totalDist += adj[route.get(i)][route.get(i + 1)];

        if (feasible) {
            return 1.0 / (totalDist + 1); // semakin BESAR semakin BAGUS
        } else {
            return 0; // infeasible = fitness nol
        }
    }

    // ============================================================
    // LOCAL SEARCH — Swap / Insert / Inverse
    // ============================================================
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
                // SWAP
                int temp = candidate[k];
                candidate[k] = candidate[l];
                candidate[l] = temp;
            } else if (r < 2.0 / 3) {
                // INSERT
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
                // INVERSE
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

            if (candidateFitness > bestFitness) { // LEBIH BESAR = lebih baik
                best = candidate;
                bestFitness = candidateFitness;
            }
        }

        return best;
    }

    // ============================================================
    // STEPPING STONE — Cari rute CS terbaik
    // ============================================================
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

        while (!antrian.isEmpty()) {
            ArrayList<Integer> head = antrian.poll();
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
                    }
                }
            }

            if (head.size() < maxDepth) {
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
        int[] option = new int[1 + numCS];
        option[0] = 0;
        for (int k = 1; k <= numCS; k++) {
            option[k] = numCustomers + k;
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