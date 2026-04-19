package Helpers;

import java.io.*;
import java.util.*;

import Model.Node;

public class Datasetreader {

    public static int width = 0;
    public static int height = 0;
    public static int numberOfCustomers = 0;
    public static int numberOfChargingStations = 0;
    public static List<Node> nodes = new ArrayList<>();

    public static void read(String path) {
        // String path =
        // "E:\\Skripsian\\WOA\\WOA_DRP\\DRP_SEMHAS\\src\\Dataset\\10CUSTOMERS.csv"; //
        // sesuaikan path
        nodes.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line;
            boolean readNodes = false;

            while ((line = br.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty())
                    continue;

                // Parsing metadata
                if (line.startsWith("WIDTH")) {
                    width = Integer.parseInt(line.split(":")[1]);
                } else if (line.startsWith("HEIGHT")) {
                    height = Integer.parseInt(line.split(":")[1]);
                } else if (line.startsWith("NUMBER_OF_CUSTOMER")) {
                    numberOfCustomers = Integer.parseInt(line.split(":")[1]);
                } else if (line.startsWith("NUMBER_OF_CHARGING_STATION")) {
                    numberOfChargingStations = Integer.parseInt(line.split(":")[1]);
                }
                // Header node
                else if (line.startsWith("NODE")) {
                    readNodes = true;
                }
                // Parsing node
                else if (readNodes) {
                    String[] parts = line.split("\\s+");

                    int id = Integer.parseInt(parts[0]);
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    String type = parts[3];

                    nodes.add(new Node(id, x, y, type));
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Debug hasil parsing
        // System.out.println("Width: " + width);
        // System.out.println("Height: " + height);
        // System.out.println("Customers: " + numberOfCustomers);
        // System.out.println("Charging Stations: " + numberOfChargingStations);
        // System.out.println("Total Nodes: " + nodes.size());

    }

    public static int getWidth() {
        return width;
    }

    public static int getHeight() {
        return height;
    }

    public static int getNumberOfCustomers() {
        return numberOfCustomers;
    }

    public static int getNumberOfChargingStations() {
        return numberOfChargingStations;
    }

    public static List<Node> getNodes() {
        return nodes;
    }

}