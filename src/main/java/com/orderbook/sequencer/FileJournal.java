package com.orderbook.sequencer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.orderbook.OrderBookException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * File-backed journal that persists each command as a newline-delimited JSON record.
 *
 * <p>Supports deterministic replay by reading back all records from the file.</p>
 */
public final class FileJournal<T> implements Journal<T> {

    private final Path path;
    private final ObjectMapper mapper;
    private final AtomicLong seq = new AtomicLong(0);
    private final PrintWriter writer;
    private final Class<T> extraType;

    public FileJournal(Path path, Class<T> extraType) throws OrderBookException {
        this.path = path;
        this.extraType = extraType;
        this.mapper = new ObjectMapper();
        try {
            // Scan existing file to recover sequence counter
            if (Files.exists(path)) {
                long count = Files.lines(path).count();
                seq.set(count);
            }
            this.writer = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(
                            new FileOutputStream(path.toFile(), true),
                            StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new OrderBookException.SerializationError("Failed to open journal: " + e.getMessage(), e);
        }
    }

    @Override
    public synchronized long append(SequencerCommand<T> command) throws OrderBookException {
        long s = seq.getAndIncrement();
        try {
            JournalEntry<T> entry = new JournalEntry<>(s, System.nanoTime(), command);
            writer.println(mapper.writeValueAsString(entry));
            writer.flush();
        } catch (IOException e) {
            throw new OrderBookException.SerializationError("Failed to write journal entry: " + e.getMessage(), e);
        }
        return s;
    }

    @Override
    public List<JournalEntry<T>> readFrom(long fromSequence) throws OrderBookException {
        List<JournalEntry<T>> result = new ArrayList<>();
        try (var lines = Files.lines(path, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                if (line.isBlank()) continue;
                var entry = mapper.readValue(line,
                        TypeFactory.defaultInstance().constructParametricType(JournalEntry.class, extraType));
                @SuppressWarnings("unchecked")
                JournalEntry<T> e = (JournalEntry<T>) entry;
                if (e.sequenceNum() >= fromSequence) result.add(e);
            }
        } catch (IOException ex) {
            throw new OrderBookException.DeserializationError("Failed to read journal: " + ex.getMessage(), ex);
        }
        return result;
    }

    @Override
    public long size() { return seq.get(); }

    @Override
    public void close() { writer.close(); }
}
