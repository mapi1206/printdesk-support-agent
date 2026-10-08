package com.printdesk.orders;

import com.printdesk.catalog.Catalog;
import com.printdesk.catalog.Catalog.Order;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

/** Order lookup against the (simulated) shop system and warranty checks. */
public final class OrderService {
    private final Catalog catalog;
    private final Clock clock;

    public OrderService(Catalog catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Order number wins; the sender's email is the fallback. */
    public Optional<Order> find(String orderNo, String email) {
        if (orderNo != null && !orderNo.isBlank()) {
            String no = orderNo.trim().toUpperCase(Locale.ROOT);
            Optional<Order> o = catalog.orders.stream().filter(x -> x.orderNo().equalsIgnoreCase(no)).findFirst();
            if (o.isPresent()) return o;
        }
        if (email != null && !email.isBlank()) {
            String em = email.trim().toLowerCase(Locale.ROOT);
            return catalog.orders.stream().filter(x -> x.email().equalsIgnoreCase(em)).findFirst();
        }
        return Optional.empty();
    }

    public enum Warranty { IN, OUT, UNKNOWN }

    public Warranty warranty(Order o) { return o.inWarranty(LocalDate.now(clock)) ? Warranty.IN : Warranty.OUT; }

    /** Warranty from a purchase date the customer mentions (EU: 24 months by default). */
    public Warranty warrantyFromPurchase(String isoDate) {
        try {
            LocalDate d = LocalDate.parse(isoDate);
            return d.plusMonths(catalog.warrantyMonths).isBefore(LocalDate.now(clock)) ? Warranty.OUT : Warranty.IN;
        } catch (DateTimeParseException | NullPointerException e) {
            return Warranty.UNKNOWN;
        }
    }
}
