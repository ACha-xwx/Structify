package com.feng.dsagent.learning;

import static org.assertj.core.api.Assertions.assertThat;

import com.feng.dsagent.learning.MasteryEstimator.Estimate;
import com.feng.dsagent.learning.MasteryEstimator.Evidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The properties the map's colours have to hold, whatever the numbers end up being.
 *
 * <p>Most of these are about not lying: no evidence must not look like failure, thin evidence must not look
 * like certainty, and clicking the same button again must not look like learning.
 */
class MasteryEstimatorTest {

    private static Evidence classroom(double attempts, double correct, double hints, double skips, double steps) {
        return new Evidence(attempts, correct, hints, skips, steps, 0, 0, 0, 0, 0);
    }

    private static Evidence code(double runs, double passed) {
        return new Evidence(0, 0, 0, 0, 0, 0, 0, 0, runs, passed);
    }

    private static Evidence animation(double unique, double requests) {
        return new Evidence(0, 0, 0, 0, 0, unique, requests, 0, 0, 0);
    }

    @Test
    @DisplayName("no evidence at all is 'not started', not a zero")
    void noEvidenceIsNotAZero() {
        Estimate estimate = MasteryEstimator.estimate(Evidence.none());

        assertThat(estimate.mastery()).isNull();
        assertThat(estimate.confidence()).isZero();
        assertThat(estimate.status()).isEqualTo(MasteryEstimator.NOT_STARTED);
        assertThat(estimate.classroom()).isNull();
    }

    @Test
    @DisplayName("a handful of right answers is not mastery")
    void smallSamplesDoNotSaturate() {
        Estimate three = MasteryEstimator.estimate(classroom(3, 3, 0, 0, 12));
        Estimate thirty = MasteryEstimator.estimate(classroom(30, 30, 0, 0, 12));

        assertThat(three.mastery()).isLessThan(0.8);
        assertThat(thirty.mastery()).isGreaterThan(three.mastery());
        // and the thinner the evidence, the less it may be trusted
        assertThat(three.confidence()).isLessThan(0.35).as("3 answers is thin evidence");
        assertThat(thirty.confidence()).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("more right answers never lower the score; more wrong ones never raise it")
    void monotonicInCorrectness() {
        double previous = -1;
        for (int correct = 0; correct <= 20; correct++) {
            Estimate estimate = MasteryEstimator.estimate(classroom(20, correct, 0, 0, 20));
            assertThat(estimate.mastery()).isGreaterThan(previous);
            previous = estimate.mastery();
        }
    }

    @Test
    @DisplayName("hints and skips both cost, and skipping costs more")
    void hintsAndSkipsCost() {
        double clean = MasteryEstimator.estimate(classroom(10, 8, 0, 0, 12)).mastery();
        double hinted = MasteryEstimator.estimate(classroom(10, 8, 5, 0, 12)).mastery();
        double skipped = MasteryEstimator.estimate(classroom(10, 8, 0, 5, 12)).mastery();

        assertThat(hinted).isLessThan(clean);
        assertThat(skipped).isLessThan(clean);
        assertThat(skipped).isLessThanOrEqualTo(hinted);
    }

    @Test
    @DisplayName("watching more animations can never carry a chapter on its own")
    void animationIsCapped() {
        Estimate estimate = MasteryEstimator.estimate(animation(50, 500));

        assertThat(estimate.animation()).isLessThanOrEqualTo(0.70);
        assertThat(estimate.mastery()).isLessThanOrEqualTo(0.70).as("animation alone cannot reach mastery");
    }

    @Test
    @DisplayName("breadth beats grinding: many structures once, not one structure many times")
    void breadthBeatsGrinding() {
        Estimate broad = MasteryEstimator.estimate(animation(6, 6));
        Estimate narrow = MasteryEstimator.estimate(animation(1, 100));

        assertThat(broad.animation()).isGreaterThan(narrow.animation());
    }

    @Test
    @DisplayName("code that runs is the strongest self-produced evidence")
    void codeCountsButNotTooFast() {
        Estimate three = MasteryEstimator.estimate(code(3, 3));
        Estimate twenty = MasteryEstimator.estimate(code(20, 18));

        assertThat(three.mastery()).isLessThan(0.7);
        assertThat(twenty.mastery()).isGreaterThan(three.mastery());
        assertThat(twenty.mastery()).isGreaterThan(0.7);
    }

    @Test
    @DisplayName("asking questions is participation, never evidence of mastery")
    void questionsDoNotRaiseMastery() {
        Evidence withQuestions = new Evidence(10, 5, 0, 0, 12, 0, 0, 40, 0, 0);
        Evidence without = new Evidence(10, 5, 0, 0, 12, 0, 0, 0, 0, 0);

        assertThat(MasteryEstimator.estimate(withQuestions).mastery())
            .isEqualTo(MasteryEstimator.estimate(without).mastery());
        // but it does mean we have seen more of the learner
        assertThat(MasteryEstimator.estimate(withQuestions).confidence())
            .isGreaterThan(MasteryEstimator.estimate(without).confidence());
    }

    @Test
    @DisplayName("a strong dimension is not outvoted by a thin one")
    void thinDimensionsDoNotOutvoteStrongOnes() {
        // One animation request must not drag a well-evidenced classroom score around.
        Evidence strong = new Evidence(30, 27, 0, 0, 40, 1, 1, 0, 0, 0);
        Estimate estimate = MasteryEstimator.estimate(strong);

        assertThat(estimate.mastery()).isGreaterThan(0.75).as("27 of 30 correct should still read as strong");
    }

    @Test
    @DisplayName("missing dimensions are left out, never counted as zero")
    void missingDimensionsAreNotFailures() {
        Estimate classroomOnly = MasteryEstimator.estimate(classroom(20, 18, 0, 0, 20));
        Estimate withEmptyCode = MasteryEstimator.estimate(new Evidence(20, 18, 0, 0, 20, 0, 0, 0, 0, 0));

        assertThat(classroomOnly.mastery()).isEqualTo(withEmptyCode.mastery());
        assertThat(classroomOnly.code()).isNull();
    }
}
