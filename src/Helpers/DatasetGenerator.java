package Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Random;

import javax.management.RuntimeErrorException;

public class DatasetGenerator {
    public static void main(String[] args) {
        int width = 100;
        int height = 100;
        int[] numberOfCustomers = { 10, 20, 30, 40, 50 };
        int[] numberOfChargingStations = { 2, 3, 4, 5, 6 };

        
        for (int k = 0; k < numberOfChargingStations.length; k++) {
            String fileName = "src\\Datasets\\" + numberOfCustomers[k] + "_Customers.csv";
            if (1 + numberOfCustomers[k] + numberOfChargingStations[k] <= width * height) {
                try {

                    File file = new File(fileName);
                    Path filePath = file.toPath();

                    StringBuffer sb = new StringBuffer();

                    sb.append("WIDTH:" + width + "\n");
                    sb.append("HEIGHT:" + height + "\n");
                    sb.append("NUMBER_OF_CUSTOMER:" + numberOfCustomers[k] + "\n");
                    sb.append("NUMBER_OF_CHARGING_STATION:" + numberOfChargingStations[k] + "\n");
                    sb.append("NODE_X_Y_TYPE\n");
                    Random rand = new Random();

                    int id = 0;
                    ArrayList<Integer> listX = new ArrayList<>();
                    ArrayList<Integer> listY = new ArrayList<>();
                    // depot
                    int x = rand.nextInt((width - 0) + 1);
                    int y = rand.nextInt((height - 0) + 1);
                    listX.add(x);
                    listY.add(y);
                    sb.append((id) + " " + x + " " + y + " DEPOT\n");
                    id++;
                    // customer
                    while (id <= numberOfCustomers[k]) {
                        x = rand.nextInt((width - 0) + 1);
                        y = rand.nextInt((height - 0) + 1);
                        // validasi koordinat duplikat
                        boolean isDuplicate = false;
                        for (int i = 0; i < listX.size(); i++) {
                            if (x == listX.get(i) && y == listY.get(i)) {
                                isDuplicate = true;
                                System.out.println("Kembar");
                                break;
                            }
                        }
                        if (!isDuplicate) {
                            listX.add(x);
                            listY.add(y);
                            sb.append(id + " " + x + " " + y + " CUSTOMER\n");
                            id++;
                        }
                    }
                    // Charging station
                    while (id <= (numberOfCustomers[k] + numberOfChargingStations[k])) {
                        x = rand.nextInt((width - 0) + 1);
                        y = rand.nextInt((height - 0) + 1);
                        // validasi koordinat duplikat
                        boolean isDuplicate = false;
                        for (int i = 0; i < listX.size(); i++) {
                            if (x == listX.get(i) && y == listY.get(i)) {
                                isDuplicate = true;
                                System.out.println("Kembar");
                                break;
                            }
                        }
                        if (!isDuplicate) {
                            listX.add(x);
                            listY.add(y);
                            sb.append(id + " " + x + " " + y + " CHARGING_STATION\n");
                            id++;
                        }
                    }
                    String data = sb.toString();
                    Files.write(filePath, data.getBytes(), StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                throw new RuntimeErrorException(null, "Config tidak valid");
            }
        }

    }

}
