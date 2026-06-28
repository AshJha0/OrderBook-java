package com.orderbook.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.model.PriceLevelChangedEvent;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Publishes {@link PriceLevelChangedEvent} events to a NATS subject.
 *
 * <p>Mirrors the Rust {@code nats_book_change.rs} module.</p>
 */
public final class NatsBookChangePublisher implements Consumer<PriceLevelChangedEvent>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NatsBookChangePublisher.class);

    private final Connection nc;
    private final String subject;
    private final ObjectMapper mapper = new ObjectMapper();

    public NatsBookChangePublisher(String natsUrl, String subject) throws IOException, InterruptedException {
        Options opts = new Options.Builder().server(natsUrl).build();
        this.nc = Nats.connect(opts);
        this.subject = subject;
    }

    @Override
    public void accept(PriceLevelChangedEvent event) {
        try {
            byte[] payload = mapper.writeValueAsBytes(event);
            nc.publish(subject, payload);
        } catch (Exception e) {
            log.error("Failed to publish book change to NATS: {}", e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try { nc.close(); }
        catch (Exception e) { log.warn("Error closing NATS connection: {}", e.getMessage()); }
    }
}
