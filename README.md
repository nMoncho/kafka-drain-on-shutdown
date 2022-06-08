# Kafka Drain on Shutdown

This repository shows how to setup _drain on shutdown_ while using Akka Streams on Kafka.

There is a [docker-compose.yaml](docker-compose.yaml) with four containers:
- Kafka
- Zookeeper
- Producer: Sends records to Kafka in batches
- Consumer: Consumes records send by Producer, with some delay between committing an offset
  and processing the records (printing them to the console).

## How To Use

**First**, build the image `sbt docker:publishLocal`.

To improve readability, run each of the following commands in separate terminals:

- Run Kafka and Zookeeper: `docker-compose up kafka zookeeper`
- Run Producer: `docker-compose up producer`
- Run Consumer: `docker-compose up consumer`
- Let the Consumer commit some batches, you should see `Kafka offset committed` printed in the console.
  You will also see messages being processed after commits (printed in the console).
- As soon as you see a **commit** message, stop the Consumer: `docker-compose stop --timeout 300 consumer`.
  You should see eventually more records being processed as the stream drains.
  **DON'T** use `ctrl-c` to stop the container, as it may not be stopped gracefully and the shutdown hook won't be
  invoker correctly.
- If you run the Consumer again, it should pick up from the latest processed message.

## Environment Variables

You can tweak the execution through these environment variables.

- `KAFKA_BOOTSTRAP_SERVERS`: Where Kafka is advertising its listener.
- `KAFKA_TOPIC`: Which topic to consume from and produce to.
- `PRODUCER_BATCH_SIZE`: Producer's batch size
- `PRODUCER_BATCH_INTERVAL`: Producer's batch interval
- `CONSUMER_GROUP_ID`: Consumer's group id
- `CONSUMER_BATCH_SIZE`: Consumer's batch size, how many messages to consume before committing offset.
- `CONSUMER_BATCH_INTERVAL`: Consumer's commit interval
