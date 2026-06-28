package com.orderbook.nats;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderbook.model.TradeResult;
import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Publishes {@link TradeResult} events to a NATS JetStream subject.
 *
 * <p>Mirrors the Rust {@code nats.rs} module. Attach as a
 * {@code tradeListener} on the order book.</p>
 */
public final class NatsTradePublisher implements Consumer<TradeResult>, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(NatsTradePublisher.class);

    private final Connection nc;
    private final String subject;
    private final ObjectMapper mapper = new ObjectMapper();

    public NatsTradePublisher(String natsUrl, String subject) throws IOException, InterruptedException {
        Options opts = new Options.Builder().server(natsUrl).build();
        this.nc = Nats.connect(opts);
        this.subject = subject;
        log.info("Connected to NATS at {} — publishing trades to '{}'", natsUrl, subject);
    }

    @Override
    public void accept(TradeResult tradeResult) {
        try {
            byte[] payload = mapper.writeValueAsBytes(tradeResult);
            nc.publish(subject, payload);
        } catch (Exception e) {
            log.error("Failed to publish trade to NATS: {}", e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try { nc.close(); }
        catch (Exception e) { log.warn("Error closing NATS connection: {}", e.getMessage()); }
    }
}
