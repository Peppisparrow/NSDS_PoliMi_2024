package it.polimi.nsds.kafka.eval;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.IntegerDeserializer;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;

// Group number: 22
// Group members: Giuseppe Vitello, Andrea Giangrande, Paolo Gennaro

// Number of partitions for inputTopic (min, max): Since the producer creates from 1 to 1000 different key, we have min 1 to max 1000 possible partitions
// Number of partitions for outputTopic1 (min, max): The number of partitions is exactly 1, since we just have one key which is "sum"
// Number of partitions for outputTopic2 (min, max): The same number of partitions as inputTopic since the keys are exactly the same, so 1-1000.

// Number of instances of Consumer1 (and groupId of each instance) (min, max): We could have multiple groups, but they will be redundant. If I have n groups they would
//                                                                             publish n times the same output so the same message. For each group I can have max 1000 consumer (the maximum number of partition of inputTopic)
//                                                                             A consumer receives is messages considering the partition he is in, thus we can have multiple consumers related to different keys. Thus changing the number of consumers means changing the outputTopic.

// Number of instances of Consumer2 (and groupId of each instance) (min, max): As for Consumer1 creating multiple groups creates redundancy.
//                                                                             In this case it does makes
//                                                                             sense to have multiple consumer per groups, because every consumer will work on different
//                                                                             partitions so on different keys, in this way we distribute the computation. Each group has
//                                                                             at least 1 consumer and max 1000 (number of partitions), if I create more than 1000 consumers,
//                                                                             the other more are in idle state.

// Please, specify below any relation between the number of partitions for the topics
// and the number of instances of each Consumer

public class Consumers22 {
    public static void main(String[] args) {
        String serverAddr = "localhost:9092";
        int consumerId = Integer.valueOf(args[0]);
        String groupId = args[1];
        if (consumerId == 1) {
            Consumer1 consumer = new Consumer1(serverAddr, groupId);
            consumer.execute();
        } else if (consumerId == 2) {
            Consumer2 consumer = new Consumer2(serverAddr, groupId);
            consumer.execute();
        }
    }

    private static class Consumer1 {
        private final String serverAddr;
        private final String consumerGroupId;

        private static final String inputTopic = "inputTopic";
        private static final String outputTopic = "outputTopic1";

        public Consumer1(String serverAddr, String consumerGroupId) {
            this.serverAddr = serverAddr;
            this.consumerGroupId = consumerGroupId;
        }

        public void execute() {
            int numMessages = 0;
            final int commitEvery = 10;
            int sum = 0;
            final String offsetResetStrategy = "earliest";
            String producerTransactionalID = "forwarderTransactionalID";

            // Consumer
            final Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddr);
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);

            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(false));
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetResetStrategy);

            KafkaConsumer<String, Integer> consumer = new KafkaConsumer<>(consumerProps);
            consumer.subscribe(Collections.singletonList(inputTopic));

            // Producer
            final Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddr);

            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());
            producerProps.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, producerTransactionalID);
            producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, String.valueOf(true));

            final KafkaProducer<String, Integer> producer = new KafkaProducer<>(producerProps);
            producer.initTransactions();


            while (true) {
                final ConsumerRecords<String, Integer> records = consumer.poll(Duration.of(5, ChronoUnit.MINUTES));
                producer.beginTransaction();
                for (final ConsumerRecord<String, Integer> record : records) {
                    // This print is just for developing reasons, to check if everything works fine
                    System.out.println(
                            "\tTopic: " + record.topic() +
                            "\tKey: " + record.key() +
                                    "\t Value: " + record.value()
                    );
                    sum += record.value();
                    numMessages++;
                    if(numMessages == commitEvery){
                        System.out.println("Sum: " + sum);
                        producer.send(new ProducerRecord<>(outputTopic, "sum", sum));
                        sum = 0;
                        numMessages = 0;
                        // The producer manually commits the offsets for the consumer within the transaction
                        final Map<TopicPartition, OffsetAndMetadata> map = new HashMap<>();
                        for (final TopicPartition partition : records.partitions()) {
                            final List<ConsumerRecord<String, Integer>> partitionRecords = records.records(partition);
                            final long lastOffset = partitionRecords.get(partitionRecords.size() - 1).offset();
                            map.put(partition, new OffsetAndMetadata(lastOffset + 1));
                        }
                        producer.sendOffsetsToTransaction(map, consumer.groupMetadata());
                    }
                }
                producer.commitTransaction();
            }
        }
    }

    private static class Consumer2 {
        private final String serverAddr;
        private final String consumerGroupId;

        private static final String inputTopic = "inputTopic";
        private static final String outputTopic = "outputTopic2";

        public Consumer2(String serverAddr, String consumerGroupId) {
            this.serverAddr = serverAddr;
            this.consumerGroupId = consumerGroupId;
        }

        public void execute() {
            final String offsetResetStrategy = "earliest";

            // Consumer
            final Properties consumerProps = new Properties();
            consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddr);
            consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);

            consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, IntegerDeserializer.class.getName());
            consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(false));
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, offsetResetStrategy);

            KafkaConsumer<String, Integer> consumer = new KafkaConsumer<>(consumerProps);
            consumer.subscribe(Collections.singletonList(inputTopic));

            // Producer
            final Properties producerProps = new Properties();
            producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, serverAddr);

            producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());

            final KafkaProducer<String, Integer> producer = new KafkaProducer<>(producerProps);

            final Map<String, Integer> countMap = new HashMap<>();
            final Map<String, Integer> sumMap = new HashMap<>();
            while (true) {
                final ConsumerRecords<String, Integer> records = consumer.poll(Duration.of(5, ChronoUnit.MINUTES));
                for (final ConsumerRecord<String, Integer> record : records) {
                    System.out.println(
                            "\tTopic: " + record.topic() +
                                    "\tKey: " + record.key() +
                                    "\t Value: " + record.value()
                    );

                    final String key = record.key();
                    final int value = record.value();

                    countMap.put(key, countMap.getOrDefault(key, 0) + 1);
                    sumMap.put(key, sumMap.getOrDefault(key, 0) + value);

                    if (countMap.get(key) == 10) {
                        int totalSum = sumMap.get(key);
                        System.out.println("Sending sum for key " + key + ": " + totalSum);
                        producer.send(new ProducerRecord<>(outputTopic, key, totalSum));
                        countMap.put(key, 0);
                        sumMap.put(key, 0);
                        consumer.commitAsync();
                    }
                }
            }
        }
    }
}

