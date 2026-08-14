
 * Amazon SDE2 LLD - Real-Time Audio Buffer Processing Pipeline
 *
 * Problem reconstruction:
 * -----------------------
 * We receive audio at a fixed rate (for example, X buffers/second).
 * The audio must pass through multiple processing stages:
 *
 *      Producer
 *          |
 *          v
 *      Queue 1
 *          |
 *          v
 *       Stage 1
 *          |
 *          v
 *      Queue 2
 *          |
 *          v
 *       Stage 2
 *          |
 *          v
 *       Queue 3
 *          |
 *          v
 *       Stage 3
 *          |
 *          v
 *        Output
 *
 * The important LLD concepts are:
 *   1. Producer-consumer pattern
 *   2. Bounded queues
 *   3. Backpressure
 *   4. Pipeline parallelism
 *   5. Ordering using sequence numbers
 *   6. Graceful shutdown
 *   7. Latency and throughput
 *
 * This is a reconstructed version of the problem from the description
 * "audio buffers across different stages at X frames per second".
 * It is NOT claimed to be the exact original Amazon question.
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class AudioPipelineLLD {

    // ============================================================
    // 1. AUDIO BUFFER
    // ============================================================
    //
    // One AudioBuffer represents one chunk of audio.
    //
    // sequenceNumber is important because if we later process buffers
    // concurrently, completion order may differ from arrival order.
    //
    static class AudioBuffer {
        private final long sequenceNumber;
        private final long timestamp;
        private final byte[] data;

        public AudioBuffer(long sequenceNumber,
                           long timestamp,
                           byte[] data) {
            this.sequenceNumber = sequenceNumber;
            this.timestamp = timestamp;
            this.data = data;
        }

        public long getSequenceNumber() {
            return sequenceNumber;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public byte[] getData() {
            return data;
        }

        @Override
        public String toString() {
            return "AudioBuffer{seq=" + sequenceNumber + "}";
        }
    }

    // ============================================================
    // 2. AUDIO PROCESSOR
    // ============================================================
    //
    // Every processing stage can implement this interface.
    //
    interface AudioProcessor {
        AudioBuffer process(AudioBuffer buffer) throws InterruptedException;
    }

    // Example processor: Noise Reduction
    static class NoiseReductionProcessor implements AudioProcessor {

        @Override
        public AudioBuffer process(AudioBuffer buffer)
                throws InterruptedException {

            // Simulate CPU work.
            Thread.sleep(3);

            System.out.println(
                    Thread.currentThread().getName()
                    + " -> Noise reduction -> "
                    + buffer);

            return buffer;
        }
    }

    // Example processor: Echo Cancellation
    static class EchoCancellationProcessor implements AudioProcessor {

        @Override
        public AudioBuffer process(AudioBuffer buffer)
                throws InterruptedException {

            Thread.sleep(4);

            System.out.println(
                    Thread.currentThread().getName()
                    + " -> Echo cancellation -> "
                    + buffer);

            return buffer;
        }
    }

    // Example processor: Encoding
    static class EncodingProcessor implements AudioProcessor {

        @Override
        public AudioBuffer process(AudioBuffer buffer)
                throws InterruptedException {

            Thread.sleep(2);

            System.out.println(
                    Thread.currentThread().getName()
                    + " -> Encoding -> "
                    + buffer);

            return buffer;
        }
    }

    // ============================================================
    // 3. BOUNDED BUFFER QUEUE
    // ============================================================
    //
    // Why bounded?
    //
    // Suppose:
    //
    // Producer = 100 buffers/sec
    // Stage 1  = 100 buffers/sec
    // Stage 2  = 50 buffers/sec
    //
    // If Queue 2 is unbounded, it will keep growing:
    //
    //     1 sec  -> 50 pending buffers
    //     10 sec -> 500 pending buffers
    //     60 sec -> 3000 pending buffers
    //
    // Eventually memory becomes a problem.
    //
    // A bounded queue gives us backpressure.
    //
    static class BufferQueue {

        private final BlockingQueue<AudioBuffer> queue;

        public BufferQueue(int capacity) {
            this.queue = new ArrayBlockingQueue<>(capacity);
        }

        // put() blocks when the queue is full.
        // This is the simplest backpressure strategy.
        public void put(AudioBuffer buffer) throws InterruptedException {
            queue.put(buffer);
        }

        // take() blocks when the queue is empty.
        public AudioBuffer take() throws InterruptedException {
            return queue.take();
        }

        public int size() {
            return queue.size();
        }
    }

    // ============================================================
    // 4. AUDIO STAGE
    // ============================================================
    //
    // Each stage has:
    //
    //     Input Queue
    //          |
    //          v
    //     Processor
    //          |
    //          v
    //     Output Queue
    //
    // Every stage has its own worker thread.
    //
    // Therefore:
    //
    // Stage 1 can process B2
    // while
    // Stage 2 processes B1.
    //
    // This is pipeline parallelism.
    //
    static class AudioStage implements Runnable {

        private final String name;
        private final AudioProcessor processor;
        private final BufferQueue inputQueue;
        private final BufferQueue outputQueue;

        private final AtomicBoolean running = new AtomicBoolean(true);

        public AudioStage(String name,
                          AudioProcessor processor,
                          BufferQueue inputQueue,
                          BufferQueue outputQueue) {

            this.name = name;
            this.processor = processor;
            this.inputQueue = inputQueue;
            this.outputQueue = outputQueue;
        }

        @Override
        public void run() {

            while (running.get()) {

                try {

                    // Wait until a buffer is available.
                    AudioBuffer input = inputQueue.take();

                    // Process the buffer.
                    AudioBuffer output = processor.process(input);

                    // Pass it to the next stage.
                    if (outputQueue != null) {
                        outputQueue.put(output);
                    }

                } catch (InterruptedException e) {

                    // Interrupt is our shutdown mechanism.
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println(name + " stopped.");
        }

        public void stop() {
            running.set(false);
        }
    }

    // ============================================================
    // 5. AUDIO PRODUCER
    // ============================================================
    //
    // Example:
    //
    // Sample rate = 48,000 samples/sec
    // Buffer size = 480 samples
    //
    // Therefore:
    //
    //     buffers/sec = 48000 / 480
    //                  = 100 buffers/sec
    //
    // Each buffer represents:
    //
    //     480 / 48000 = 10ms
    //
    // For an interview, we can simulate this with sleep().
    // A real audio implementation would normally receive data
    // from an audio device/callback rather than use Thread.sleep().
    //
    static class AudioProducer implements Runnable {

        private final BufferQueue outputQueue;
        private final int bufferSize;
        private final long intervalMs;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicLong produced = new AtomicLong();

        public AudioProducer(BufferQueue outputQueue,
                             int bufferSize,
                             long intervalMs) {

            this.outputQueue = outputQueue;
            this.bufferSize = bufferSize;
            this.intervalMs = intervalMs;
        }

        @Override
        public void run() {

            long sequence = 0;

            while (running.get()) {

                try {

                    byte[] data = captureAudio(bufferSize);

                    AudioBuffer buffer = new AudioBuffer(
                            sequence++,
                            System.currentTimeMillis(),
                            data
                    );

                    // If the queue is full, this blocks.
                    // That is backpressure.
                    outputQueue.put(buffer);

                    produced.incrementAndGet();

                    System.out.println(
                            "Producer -> " + buffer);

                    Thread.sleep(intervalMs);

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println("Producer stopped.");
        }

        private byte[] captureAudio(int size) {
            return new byte[size];
        }

        public void stop() {
            running.set(false);
        }

        public long getProducedCount() {
            return produced.get();
        }
    }

    // ============================================================
    // 6. AUDIO CONSUMER
    // ============================================================
    //
    // The final stage pushes the processed buffer to the output.
    //
    static class AudioConsumer implements Runnable {

        private final BufferQueue inputQueue;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private final AtomicLong consumed = new AtomicLong();

        public AudioConsumer(BufferQueue inputQueue) {
            this.inputQueue = inputQueue;
        }

        @Override
        public void run() {

            while (running.get()) {

                try {

                    AudioBuffer buffer = inputQueue.take();

                    // Simulate sending audio to speaker/network.
                    System.out.println(
                            "Output -> " + buffer);

                    consumed.incrementAndGet();

                } catch (InterruptedException e) {

                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println("Consumer stopped.");
        }

        public void stop() {
            running.set(false);
        }

        public long getConsumedCount() {
            return consumed.get();
        }
    }

    // ============================================================
    // 7. PIPELINE
    // ============================================================
    //
    // Responsible for wiring stages together and controlling them.
    //
    static class AudioPipeline {

        private final AudioProducer producer;
        private final List<AudioStage> stages;
        private final AudioConsumer consumer;

        private final List<Thread> stageThreads = new ArrayList<>();
        private Thread producerThread;
        private Thread consumerThread;

        public AudioPipeline(AudioProducer producer,
                             List<AudioStage> stages,
                             AudioConsumer consumer) {

            this.producer = producer;
            this.stages = stages;
            this.consumer = consumer;
        }

        public void start() {

            // Start processing stages first so they are ready
            // before the producer starts producing data.
            for (AudioStage stage : stages) {

                Thread thread = new Thread(
                        stage,
                        stage.name
                );

                stageThreads.add(thread);
                thread.start();
            }

            consumerThread =
                    new Thread(consumer, "Audio-Consumer");

            consumerThread.start();

            producerThread =
                    new Thread(producer, "Audio-Producer");

            producerThread.start();
        }

        public void stop() {

            System.out.println("\nStopping pipeline...\n");

            producer.stop();

            for (AudioStage stage : stages) {
                stage.stop();
            }

            consumer.stop();

            // Interrupt blocked threads.
            if (producerThread != null) {
                producerThread.interrupt();
            }

            for (Thread thread : stageThreads) {
                thread.interrupt();
            }

            if (consumerThread != null) {
                consumerThread.interrupt();
            }

            // Wait for all threads to finish.
            join(producerThread);

            for (Thread thread : stageThreads) {
                join(thread);
            }

            join(consumerThread);

            System.out.println("\nPipeline stopped.");
        }

        private void join(Thread thread) {

            if (thread == null) {
                return;
            }

            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ============================================================
    // 8. MAIN
    // ============================================================
    //
    // Example configuration:
    //
    // Sample rate = 48,000 samples/sec
    // Buffer size = 480 samples
    //
    //     48000 / 480 = 100 buffers/sec
    //
    // So:
    //
    //     interval = 1000 / 100 = 10ms
    //
    public static void main(String[] args) throws Exception {

        int sampleRate = 48_000;
        int samplesPerBuffer = 480;

        int buffersPerSecond =
                sampleRate / samplesPerBuffer;

        long intervalMs =
                1000L / buffersPerSecond;

        System.out.println(
                "Sample rate      : " + sampleRate);

        System.out.println(
                "Samples/buffer   : " + samplesPerBuffer);

        System.out.println(
                "Buffers/sec      : " + buffersPerSecond);

        System.out.println(
                "Buffer duration  : " + intervalMs + "ms");

        // --------------------------------------------------------
        // Create queues.
        //
        // Capacity = 10 buffers.
        //
        // At 100 buffers/sec, this represents approximately:
        //
        //     10 / 100 = 100ms
        //
        // of buffering.
        // --------------------------------------------------------

        BufferQueue queue1 = new BufferQueue(10);
        BufferQueue queue2 = new BufferQueue(10);
        BufferQueue queue3 = new BufferQueue(10);

        // --------------------------------------------------------
        // Create processing stages.
        // --------------------------------------------------------

        AudioStage noiseReduction =
                new AudioStage(
                        "Stage-1-NoiseReduction",
                        new NoiseReductionProcessor(),
                        queue1,
                        queue2
                );

        AudioStage echoCancellation =
                new AudioStage(
                        "Stage-2-EchoCancellation",
                        new EchoCancellationProcessor(),
                        queue2,
                        queue3
                );

        AudioStage encoding =
                new AudioStage(
                        "Stage-3-Encoding",
                        new EncodingProcessor(),
                        queue3,
                        null
                );

        // --------------------------------------------------------
        // Producer and consumer.
        // --------------------------------------------------------

        AudioProducer producer =
                new AudioProducer(
                        queue1,
                        samplesPerBuffer,
                        intervalMs
                );

        // Since the final stage currently has no output queue,
        // we will use queue3 as the consumer input only if Stage 3
        // writes to a queue. To keep the stage chain explicit,
        // create one more queue for final output.
        //
        // Therefore this demo reconstructs the final stage below.
        // --------------------------------------------------------

        BufferQueue outputQueue = new BufferQueue(10);

        encoding =
                new AudioStage(
                        "Stage-3-Encoding",
                        new EncodingProcessor(),
                        queue3,
                        outputQueue
                );

        AudioConsumer consumer =
                new AudioConsumer(outputQueue);

        List<AudioStage> stages =
                Arrays.asList(
                        noiseReduction,
                        echoCancellation,
                        encoding
                );

        AudioPipeline pipeline =
                new AudioPipeline(
                        producer,
                        stages,
                        consumer
                );

        // --------------------------------------------------------
        // Start.
        // --------------------------------------------------------

        pipeline.start();

        // Run the demo for 1 second.
        Thread.sleep(1000);

        // --------------------------------------------------------
        // Graceful shutdown.
        // --------------------------------------------------------

        pipeline.stop();
    }
}

/*
 * ================================================================
 * INTERVIEW EXPLANATION / TALKING POINTS
 * ================================================================
 *
 * 1. WHY AudioBuffer?
 *
 * Each buffer represents a small chunk of audio.
 * The sequence number guarantees logical ordering.
 * Timestamp allows us to measure end-to-end latency.
 *
 *
 * 2. WHY BOUNDED QUEUES?
 *
 * If producer is faster than a processing stage, an unbounded queue
 * can grow indefinitely.
 *
 * A bounded queue provides backpressure.
 *
 *
 * 3. WHY ONE THREAD PER STAGE?
 *
 * It allows pipelining.
 *
 * Example:
 *
 *     Stage 1 -> B3
 *     Stage 2 -> B2
 *     Stage 3 -> B1
 *
 * All three can run at the same time.
 *
 * Throughput is therefore approximately limited by the slowest
 * processing stage, rather than the sum of all stage times.
 *
 *
 * 4. WHAT IF STAGE 2 IS SLOW?
 *
 * Suppose:
 *
 *     Producer = 100 buffers/sec
 *     Stage 1  = 100 buffers/sec
 *     Stage 2  = 50 buffers/sec
 *
 * Queue 2 will eventually fill.
 *
 * With queue.put(), Stage 1 blocks when Queue 2 is full.
 * This propagates backpressure toward the producer.
 *
 *
 * 5. SHOULD WE ALWAYS BLOCK?
 *
 * No.
 *
 * For a real-time voice call, old audio may become useless if it
 * is delayed too much. In that case we may prefer:
 *
 *     - Drop oldest
 *     - Drop newest
 *     - Reduce processing quality
 *     - Increase worker count
 *
 * The correct policy depends on the product requirement.
 *
 *
 * 6. WHAT IF WE HAVE MULTIPLE WORKERS?
 *
 * Then completion order can change:
 *
 *     B1 -> Worker 1 -> slow
 *     B2 -> Worker 2 -> fast
 *
 * Output:
 *
 *     B2, B1
 *
 * To preserve ordering, attach sequence numbers and introduce a
 * ReorderBuffer:
 *
 *     Queue
 *       |
 *       +---- Worker 1 ----+
 *       +---- Worker 2 ----+--> ReorderBuffer --> Output
 *       +---- Worker 3 ----+
 *
 * The reorder buffer waits for the next expected sequence number.
 *
 *
 * 7. BUFFER SIZING
 *
 * Example:
 *
 *     Sample rate = 48,000 samples/sec
 *     Buffer size = 480 samples
 *
 *     buffers/sec = 48000 / 480
 *                 = 100
 *
 * Each buffer represents:
 *
 *     480 / 48000 = 10ms
 *
 * If queue capacity is 10:
 *
 *     10 * 10ms = 100ms
 *
 * of audio can be buffered.
 *
 *
 * 8. LATENCY VS THROUGHPUT
 *
 * Smaller buffers:
 *     + Lower latency
 *     - More overhead
 *     - More sensitive to scheduling delays
 *
 * Larger buffers:
 *     + Better throughput/efficiency
 *     - Higher latency
 *
 *
 * 9. FAILURE HANDLING
 *
 * In a production design, a stage could fail.
 *
 * Possible strategies:
 *
 *     - Retry
 *     - Skip/drop the buffer
 *     - Restart the stage
 *     - Degrade quality
 *     - Stop the pipeline
 *
 * For audio, the right choice depends on whether the application
 * prioritizes continuity or correctness.
 *
 *
 * 10. GRACEFUL SHUTDOWN
 *
 * We:
 *
 *     1. Stop producer
 *     2. Stop accepting new work
 *     3. Interrupt blocked workers
 *     4. Stop consumer
 *     5. Join all threads
 *
 * This prevents threads from being leaked.
 *
 *
 * 11. IMPORTANT INTERVIEW LINE
 *
 * A concise way to explain the design:
 *
 * "I model each audio chunk as an immutable AudioBuffer with a
 * sequence number and timestamp. Each processing stage owns a
 * bounded blocking queue and a worker. The stages form a pipeline,
 * so multiple buffers can be processed concurrently at different
 * stages. Bounded queues provide backpressure, while sequence
 * numbers allow us to preserve ordering if we later introduce
 * multiple workers per stage."
 *
 * ================================================================
 */
'''
