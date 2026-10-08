package com.printdesk.knowledge;

import com.printdesk.catalog.Catalog;
import com.printdesk.catalog.Catalog.CaseRecord;
import com.printdesk.catalog.Catalog.Problem;
import com.printdesk.catalog.Catalog.Step;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The learning loop. For every troubleshooting step it measures
 * <pre>fix rate = cases solved by this step / cases that reached this step</pre>
 * and ranks steps by it: free checks first, part replacements last.
 * Each closed ticket adds a case, so the ranking shifts with real outcomes.
 */
public final class Learning {
    /** Below this many printer-specific cases the stats fall back to all printers. */
    static final int MIN_PRINTER_CASES = 8;

    private final Catalog catalog;

    public Learning(Catalog catalog) { this.catalog = catalog; }

    public record StepStat(Step step, int index, int reached, int fixed) {
        /** null when no case ever reached the step. */
        public Double rate() { return reached == 0 ? null : (double) fixed / reached; }
    }

    public record Stats(String problemId, String basis, int cases, List<StepStat> steps) {}

    public Stats stepStats(String problemId, String printerId) {
        Problem pr = catalog.problem(problemId).orElseThrow(() -> new IllegalArgumentException("Unknown problem " + problemId));
        List<CaseRecord> cs = catalog.cases.stream().filter(c -> problemId.equals(c.problem())).toList();
        String basis = "all printers";
        if (printerId != null) {
            List<CaseRecord> own = cs.stream().filter(c -> printerId.equals(c.printer())).toList();
            if (own.size() >= MIN_PRINTER_CASES) {
                cs = own;
                basis = catalog.printer(printerId).map(Catalog.Printer::name).orElse(printerId);
            }
        }
        List<StepStat> out = new ArrayList<>();
        for (int j = 0; j < pr.steps().size(); j++) {
            final int idx = j;
            int reached = (int) cs.stream().filter(c -> c.stepsTried() > idx || Integer.valueOf(idx).equals(c.solvedStep())).count();
            int fixed = (int) cs.stream().filter(c -> Integer.valueOf(idx).equals(c.solvedStep())).count();
            out.add(new StepStat(pr.steps().get(j), j, reached, fixed));
        }
        return new Stats(problemId, basis, cs.size(), out);
    }

    /** Recommended order: free checks by fix rate, then steps that need a part by fix rate. */
    public List<StepStat> rankedSteps(String problemId, String printerId) {
        Comparator<StepStat> byRate = Comparator.comparingDouble((StepStat s) -> s.rate() == null ? 0 : s.rate()).reversed();
        List<StepStat> all = stepStats(problemId, printerId).steps();
        List<StepStat> out = new ArrayList<>(all.stream().filter(s -> !s.step().needsPart()).sorted(byRate).toList());
        out.addAll(all.stream().filter(s -> s.step().needsPart()).sorted(byRate).toList());
        return out;
    }

    public long timesChosen(String partId) {
        return catalog.cases.stream().filter(c -> partId.equals(c.partId())).count();
    }
}
