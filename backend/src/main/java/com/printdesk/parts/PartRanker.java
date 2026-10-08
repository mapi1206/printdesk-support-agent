package com.printdesk.parts;

import com.printdesk.catalog.Catalog;
import com.printdesk.catalog.Catalog.Part;
import com.printdesk.knowledge.Learning;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "Best offer" ranking of compatible parts. The balanced score is an explainable sum
 * (lower is better):
 * <pre>price + shipping + 1.2 × delivery days + 4 if not original + 25 if out of stock − bonus for parts that fixed past cases</pre>
 */
public final class PartRanker {

    public enum Priority { BALANCED, CHEAPEST, FASTEST, ORIGINAL_ONLY }

    public record Ranked(Part part, double totalCost, double score) {}

    private final Catalog catalog;
    private final Learning learning;

    public PartRanker(Catalog catalog, Learning learning) {
        this.catalog = catalog;
        this.learning = learning;
    }

    public double totalCost(Part p) {
        return p.price() + (p.price() >= catalog.freeShippingFrom ? 0 : catalog.standardShipping);
    }

    /**
     * @param printerId only parts compatible with this printer (null = any)
     * @param category  part category such as "hotend" (null = any)
     * @param nameHint  optional substring that narrows the result (e.g. "feeder"); ignored if nothing matches
     */
    public List<Ranked> rank(String printerId, String category, String nameHint, Priority prio) {
        List<Part> ps = catalog.parts.stream()
                .filter(p -> p.fits(printerId))
                .filter(p -> category == null || category.equals(p.cat()))
                .filter(p -> prio != Priority.ORIGINAL_ONLY || p.official())
                .toList();
        if (nameHint != null && !nameHint.isBlank()) {
            String h = nameHint.toLowerCase(Locale.ROOT);
            List<Part> narrowed = ps.stream().filter(p -> p.name().toLowerCase(Locale.ROOT).contains(h)).toList();
            if (!narrowed.isEmpty()) ps = narrowed;
        }
        return ps.stream()
                .map(p -> new Ranked(p, totalCost(p), score(p, prio)))
                .sorted(Comparator.comparingDouble(Ranked::score))
                .toList();
    }

    double score(Part p, Priority prio) {
        double cost = totalCost(p);
        double outOfStock = p.stock() == 0 ? 1 : 0;
        return switch (prio) {
            case CHEAPEST -> cost + outOfStock * 1000;
            case FASTEST -> p.days() * 100 + cost + outOfStock * 1000;
            case BALANCED, ORIGINAL_ONLY -> cost + p.days() * 1.2 + (p.official() ? 0 : 4) + outOfStock * 25
                    - Math.min(6, learning.timesChosen(p.id()) * 0.4);
        };
    }
}
