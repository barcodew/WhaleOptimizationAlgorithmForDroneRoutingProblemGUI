package Helpers;

import java.util.List;

import Model.Node;

public class MatrixBuilder {

    public static double[][] generate(List<Node> node) {

        int n = node.size();

        double[][] matrix = new double[n][n];

        for (int i = 0; i < n; i++) {
            Node a = node.get(i);

            for (int j = 0; j < n; j++) {
                Node b = node.get(j);

                double dx = a.x - b.x;
                double dy = a.y - b.y;
                matrix[i][j] = Math.sqrt(dx * dx + dy * dy);
            }
        }
        return matrix;

    }

}
