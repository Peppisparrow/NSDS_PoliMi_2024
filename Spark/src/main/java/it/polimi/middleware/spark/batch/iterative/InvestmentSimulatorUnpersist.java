package it.polimi.middleware.spark.batch.iterative;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import scala.Tuple2;

public class InvestmentSimulatorUnpersist {
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

        JavaRDD<Tuple2<Double, Double>> oldInvestments = investments;
        investments.cache();

        int iteration = 0;
        double sum = sumAmount(investments);
        while(sum < threshold) {
            iteration++;
            investments = investments.map(i-> {
                System.out.println("AAA");
                return new Tuple2<>(i._1*(1+i._2), i._2);
            });
            // non è persistent quindi mi serve ri-calcolare ogni volta tutte le map alla 1 una volta alla 2 due volte ecc
            // facendo così invece la si mette in cache e non la ricalcola ogni volta
            investments.cache();
            sum = sumAmount(investments);

            // Important: this need to be done after the action (subAmount)
            // otherwise the old investments would be unpersisted before the
            // new investments are computed and cached.
            // Try to move it before the action and see what happens.
            oldInvestments.unpersist();
            oldInvestments = investments;

            System.out.println("Sum: " + sum + " after " + iteration + " iterations");
        }

        sc.close();
    }

    private static final double sumAmount(JavaRDD<Tuple2<Double, Double>> investments) {
        return investments.mapToDouble(i -> i._1).sum();
    }

}
