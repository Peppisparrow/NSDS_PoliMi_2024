package it.polimi.middleware.spark.batch.iterative;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;


/**
 * Start from a dataset of investments. Each element is a Tuple2(amount_owned, interest_rate).
 * At each iteration the new amount is (amount_owned * (1+interest_rate)).
 *
 * Implement an iterative algorithm that computes the new amount for each investment and stops
 * when the overall amount overcomes 1000.
 */
public class InvestmentSimulator {
    public static void main(String[] args) {
        final String master = args.length > 0 ? args[0] : "local[1]";
        final String filePath = args.length > 1 ? args[1] : "./";
        final double threshold = 35;

        final SparkConf conf = new SparkConf().setMaster(master).setAppName("InvestmentSimulator");
        final JavaSparkContext sc = new JavaSparkContext(conf);
        sc.setLogLevel("ERROR");

        final JavaRDD<String> textFile = sc.textFile(filePath + "files/iterative/investment.txt");
        JavaRDD<Tuple2<Double, Double>> investments= textFile.map(line -> {
            String[] parts = line.split(" ");
            double amount = Double.parseDouble(parts[0]);
            double interest = Double.parseDouble(parts[1]);
            return new Tuple2<>(amount, interest);
        });

        int iteration = 0;
        double sum = sumAmount(investments);
        while(sum < threshold) {
            iteration++;
            investments = investments.map(i-> new Tuple2<>(i._1*(1+i._2), i._2));
            // non è persistent quindi mi serve ri-calcolare ogni volta tutte le map alla 1 una volta alla 2 due volte ecc
            //facendo così invece la si mette in cache e non la ricalcola ogni volta
            investments.cache();
            sum = sumAmount(investments);
            System.out.println("Sum: " + sum + " after " + iteration + " iterations");
        }

        sc.close();
    }

    private static final double sumAmount(JavaRDD<Tuple2<Double, Double>> investments) {
        return investments.mapToDouble(i -> i._1).sum();
    }

}
