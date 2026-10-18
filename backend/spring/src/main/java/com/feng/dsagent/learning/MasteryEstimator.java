package com.feng.dsagent.learning;

/**
 * Turning behaviour into a mastery estimate, and being honest about how much of it is guesswork.
 *
 * <p>Three rules decide the design:
 * <ol>
 *   <li><b>No data is not a zero.</b> A dimension with no evidence is left out of the average, and a node
 *       with no evidence at all has no mastery at all - "not started" and "tried and failed" are different
 *       things to say about a learner.</li>
 *   <li><b>Little evidence must not look like certainty.</b> Counts go through a Beta prior, so one correct
 *       answer is not full marks and three animations are not mastery; the confidence is reported separately
 *       so the picture can show it.</li>
 *   <li><b>Only what a learner produced counts as proof.</b> Answering and running code carry correctness;
 *       watching an animation and asking a question carry behaviour, so they are capped and weighted lower.</li>
 * </ol>
 */
public final class MasteryEstimator {

    /** Theory and practice weigh the same; inside theory, being taught counts more than browsing. */
    public static final double W_CLASSROOM = 0.55;
    public static final double W_CODE = 0.30;
    public static final double W_ANIMATION = 0.15;
    /** Asking a question is participation, not evidence of mastery: it never enters the score. */
    public static final double W_CHAT = 0.0;

    /** Pseudo-counts of the Beta prior on correctness: two right and two wrong to start with. */
    private static final double PRIOR_CORRECT = 2.0;
    private static final double PRIOR_WRONG = 2.0;
    /** A lesson is about this many steps, which is what "walked through it" is measured against. */
    private static final double TYPICAL_STEPS = 12.0;
    /** Below this, the estimate is reported as too thin to act on. */
    private static final double LOW_EVIDENCE = 0.35;
    private static final double EPSILON = 1e-9;

    private MasteryEstimator() {
    }

    /** All the behaviour behind one node, already weighted for recency and de-duplicated. */
    public record Evidence(double classroomAttempts, double classroomCorrect, double classroomHints,
                           double classroomSkips, double classroomSteps, double animationUnique,
                           double animationRequests, double chatQuestions, double codeRuns, double codePassed) {

        public static Evidence none() {
            return new Evidence(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        boolean empty() {
            return classroomAttempts + classroomHints + classroomSkips + classroomSteps
                + animationRequests + chatQuestions + codeRuns < EPSILON;
        }
    }

    /** What the map draws: a score, how much it can be trusted, and the pieces it came from. */
    public record Estimate(Double mastery, double confidence, String status, Double classroom,
                           Double animation, Double code) {
    }

    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String LOW = "LOW_EVIDENCE";
    public static final String ESTIMATED = "ESTIMATED";

    public static Estimate estimate(Evidence evidence) {
        if (evidence.empty()) {
            return new Estimate(null, 0, NOT_STARTED, null, null, null);
        }
        Double classroom = classroom(evidence);
        Double animation = animation(evidence);
        Double code = code(evidence);

        // Only dimensions with evidence are averaged, and each one is weighted by how much it can be
        // trusted: a dimension seen twice must not outvote one seen fifty times.
        double numerator = 0;
        double denominator = 0;
        if (classroom != null) { numerator += W_CLASSROOM * classConfidence(evidence) * classroom; denominator += W_CLASSROOM * classConfidence(evidence); }
        if (code != null) { numerator += W_CODE * codeConfidence(evidence) * code; denominator += W_CODE * codeConfidence(evidence); }
        if (animation != null) { numerator += W_ANIMATION * animationConfidence(evidence) * animation; denominator += W_ANIMATION * animationConfidence(evidence); }
        double quality = denominator < EPSILON ? 0.5 : numerator / denominator;

        double confidence = confidence(evidence);
        String status = confidence < LOW_EVIDENCE ? LOW : ESTIMATED;
        return new Estimate(round(quality), round(confidence), status, round(classroom), round(animation), round(code));
    }

    /** Correctness through a Beta prior, then a pull toward the middle that lasts while the evidence is thin. */
    private static Double classroom(Evidence evidence) {
        double attempts = evidence.classroomAttempts();
        double hints = evidence.classroomHints();
        double skips = evidence.classroomSkips();
        double steps = evidence.classroomSteps();
        if (attempts + hints + skips + steps < EPSILON) return null;

        double correctness = (evidence.classroomCorrect() + PRIOR_CORRECT) / (attempts + PRIOR_CORRECT + PRIOR_WRONG);
        // Hints are a small penalty - the learner still worked - while skipping a question is a missed
        // chance to learn, and is punished harder.
        double independence = clamp(1 - 0.25 * min(1, hints / (attempts + 1)) - 0.40 * min(1, skips / (attempts + 1)));
        double progress = 1 - Math.exp(-steps / TYPICAL_STEPS);
        double raw = 0.75 * correctness + 0.15 * independence + 0.10 * progress;
        return 0.5 + shrink(attempts, 6) * (raw - 0.5);
    }

    /**
     * Watching an animation proves the learner met the structure, not that they can use it, so this
     * dimension is capped and starts from a low floor - it can lift a picture, never carry it.
     */
    private static Double animation(Evidence evidence) {
        double requests = evidence.animationRequests();
        if (requests < EPSILON) return null;
        double breadth = 1 - Math.exp(-evidence.animationUnique() / 3);
        double depth = 1 - Math.exp(-requests / 5);
        return min(0.70, 0.40 + 0.20 * breadth + 0.10 * depth);
    }

    /** The second strongest evidence after the classroom, because the learner had to produce something. */
    private static Double code(Evidence evidence) {
        double runs = evidence.codeRuns();
        if (runs < EPSILON) return null;
        double passed = (evidence.codePassed() + PRIOR_CORRECT) / (runs + PRIOR_CORRECT + PRIOR_WRONG);
        return 0.5 + shrink(runs, 5) * (passed - 0.5);
    }

    /** How much of a dimension's observation can be believed: 0 at no data, approaching 1 as it accumulates. */
    private static double shrink(double count, double half) {
        return count / (count + half);
    }

    private static double classConfidence(Evidence evidence) {
        return 1 - Math.exp(-evidence.classroomAttempts() / 6);
    }

    private static double codeConfidence(Evidence evidence) {
        return 1 - Math.exp(-evidence.codeRuns() / 5);
    }

    private static double animationConfidence(Evidence evidence) {
        return 1 - Math.exp(-evidence.animationRequests() / 5);
    }

    /** Overall confidence: watching counts for a quarter of what answering does, asking for a tenth. */
    static double confidence(Evidence evidence) {
        double weight = 1.0 * evidence.classroomAttempts()
            + 0.8 * evidence.codeRuns()
            + 0.25 * evidence.animationRequests()
            + 0.10 * evidence.chatQuestions();
        return 1 - Math.exp(-weight / 10);
    }

    private static double min(double left, double right) {
        return Math.min(left, right);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(1, value));
    }

    private static Double round(Double value) {
        return value == null ? null : Math.round(value * 1000) / 1000.0;
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
