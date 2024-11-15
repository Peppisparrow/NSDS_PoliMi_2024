package it.polimi.middleware.spark.batch.friends;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;

import java.util.ArrayList;
import java.util.List;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.desc;

/**
 * Implement an iterative algorithm that implements the transitive closure of friends
 * (people that are friends of friends of ... of my friends).
 *
 * Set the value of the flag useCache to see the effects of caching.
 */
public class FriendsComputation {
    private static final boolean useCache = true;

    public static void main(String[] args) {
        final String master = args.length > 0 ? args[0] : "local[4]";
        final String filePath = args.length > 1 ? args[1] : "./";
        final String appName = useCache ? "FriendsCache" : "FriendsNoCache";

        final SparkSession spark = SparkSession
                .builder()
                .master(master)
                .appName(appName)
                .getOrCreate();
        spark.sparkContext().setLogLevel("ERROR");

        final List<StructField> fields = new ArrayList<>();
        fields.add(DataTypes.createStructField("person", DataTypes.StringType, false));
        fields.add(DataTypes.createStructField("friend", DataTypes.StringType, false));
        final StructType schema = DataTypes.createStructType(fields);

        final Dataset<Row> input = spark
                .read()
                .option("header", "false")
                .option("delimiter", ",")
                .schema(schema)
                .csv(filePath + "files/friends/friends.csv");

        // TODO
        input.show();
        // Calcolo della chiusura transitiva
        Dataset<Row> transitiveClosure = input;

        boolean hasNewRows = true;
        while (hasNewRows) {
            // Alias per evitare ambiguità nel self-join
            Dataset<Row> transitiveAlias = transitiveClosure.as("tc");
            Dataset<Row> inputAlias = input.as("in");

            // Uniamo il dataset transitiveClosure con input per trovare nuove relazioni indirette
            Dataset<Row> newConnections = transitiveAlias
                    .join(inputAlias, col("tc.friend").equalTo(col("in.person")))
                    .select(col("tc.person").alias("person"), col("in.friend").alias("friend"))
                    .distinct();

            // Sottraiamo le relazioni già presenti per trovare solo le nuove connessioni
            Dataset<Row> newEntries = newConnections.except(transitiveClosure);

            // Verifichiamo se ci sono nuove righe da aggiungere
            hasNewRows = newEntries.count() > 0;

            // Aggiungiamo le nuove relazioni a transitiveClosure
            transitiveClosure = transitiveClosure.union(newEntries).distinct();
        }

        System.out.println("Chiusura transitiva:");
        transitiveClosure.orderBy("person", "friend").show();

        spark.close();
    }
}
