/**
 * Tests for SequenceProcessor
 * Run with: java SequenceProcessorTest.java
 */
public class SequenceProcessorTest {

    private static int passed = 0;
    private static int failed = 0;

    private static SequenceProcessor processor;

    public static void main(String[] args) {
        processor = new SequenceProcessor();

        System.out.println("\n--- Stage A: Whitespace + Uppercase ---");
        testWhitespaceUppercase();

        System.out.println("\n--- Stage B: Unmerged Reads Detection ---");
        testUnmergedReads();

        System.out.println("\n--- Stage C: Gap Removal ---");
        testGapRemoval();

        System.out.println("\n--- Stage D: Anchor Trimming ---");
        testAnchorTrimming();

        System.out.println("\n--- Stage E: U to T Conversion ---");
        testUtoTConversion();

        System.out.println("\n--- Stage F: N-run Capping ---");
        testNrunCapping();

        System.out.println("\n--- Metrics ---");
        testMetrics();

        System.out.println("\n--- MD5 Hashes ---");
        testMd5();

        System.out.println("\n--- Full Pipeline Examples ---");
        testFullPipelineExamples();

        System.out.println("\n--- Edge Cases ---");
        testEdgeCases();

        System.out.println("\n========================================");
        System.out.println("Tests: " + (passed + failed) + " | Passed: " + passed + " | Failed: " + failed);
        System.out.println("========================================\n");

        System.exit(failed > 0 ? 1 : 0);
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    private static void test(String name, Runnable testFn) {
        try {
            testFn.run();
            System.out.println("✓ " + name);
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ " + name);
            System.out.println("  " + e.getMessage());
            failed++;
        }
    }

    private static void assertEqual(Object actual, Object expected) {
        if (actual == null && expected == null) return;
        if (actual == null || !actual.equals(expected)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Expected true, got false");
        }
    }

    private static void assertClose(Double actual, double expected, double tolerance) {
        if (actual == null) {
            throw new AssertionError("Expected " + expected + ", got null");
        }
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void assertClose(Double actual, double expected) {
        assertClose(actual, expected, 0.0001);
    }

    // =========================================================================
    // Stage A: Whitespace normalization + uppercase
    // =========================================================================

    private static void testWhitespaceUppercase() {
        test("converts lowercase to uppercase", () -> {
            var result = processor.processOneSequence("acgtacgtacgt");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
        });

        test("removes whitespace", () -> {
            var result = processor.processOneSequence("acgt acgt  acgt");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
            assertEqual(result.gapAndWhitespaceRemoved(), true);
        });

        test("removes tabs and newlines", () -> {
            var result = processor.processOneSequence("acgt\tacgt\nacgt");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
        });
    }

    // =========================================================================
    // Stage B: Unmerged reads detection
    // =========================================================================

    private static void testUnmergedReads() {
        test("detects UNMERGED marker", () -> {
            var result = processor.processOneSequence("ACGTACGTUNMERGEDACGTACGT");
            assertEqual(result.unmergedReadsDetected(), true);
        });

        test("no detection when marker absent", () -> {
            var result = processor.processOneSequence("ACGTACGTACGTACGT");
            assertEqual(result.unmergedReadsDetected(), false);
        });
    }

    // =========================================================================
    // Stage C: Gap removal
    // =========================================================================

    private static void testGapRemoval() {
        test("removes hyphens", () -> {
            var result = processor.processOneSequence("ACGT-ACGT-ACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
            assertEqual(result.gapAndWhitespaceRemoved(), true);
        });

        test("removes dots", () -> {
            var result = processor.processOneSequence("ACGT..ACGT..ACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
        });

        test("removes mixed gaps", () -> {
            var result = processor.processOneSequence("ACGT--.ACGT");
            assertEqual(result.cleanSequence(), "ACGTACGT");
        });
    }

    // =========================================================================
    // Stage D: Anchor trimming
    // =========================================================================

    private static void testAnchorTrimming() {
        test("trims non-anchor prefix", () -> {
            var result = processor.processOneSequence("XXXXACGTACGTACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
            assertEqual(result.endsTrimmed(), true);
        });

        test("trims non-anchor suffix", () -> {
            var result = processor.processOneSequence("ACGTACGTACGTXXXX");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
            assertEqual(result.endsTrimmed(), true);
        });

        test("trims both ends", () -> {
            var result = processor.processOneSequence("XXXXACGTACGTACGTXXXX");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
            assertEqual(result.endsTrimmed(), true);
        });

        test("no trim when sequence is all anchors", () -> {
            var result = processor.processOneSequence("ACGTACGTACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
            assertEqual(result.endsTrimmed(), false);
        });

        test("wipes sequence with no valid anchor run", () -> {
            var result = processor.processOneSequence("ACGTXXXXACGT");
            assertEqual(result.cleanSequence(), "");
            assertEqual(result.endsTrimmed(), true);
        });
    }

    // =========================================================================
    // Stage E: U to T conversion
    // =========================================================================

    private static void testUtoTConversion() {
        test("converts U to T", () -> {
            var result = processor.processOneSequence("ACGUACGUACGU");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
        });

        test("converts mixed U and T", () -> {
            var result = processor.processOneSequence("ACGUACGTACGU");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
        });
    }

    // =========================================================================
    // Stage F: N-run capping
    // =========================================================================

    private static void testNrunCapping() {
        test("caps long N-runs to 5", () -> {
            var result = processor.processOneSequence("ACGTACGTNNNNNNNNNNACGTACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTNNNNNACGTACGT");
            assertEqual(result.nNrunsCapped(), 1);
        });

        test("caps multiple N-runs", () -> {
            var result = processor.processOneSequence("ACGTACGTNNNNNNACGTNNNNNNNNACGTACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTNNNNNACGTNNNNNACGTACGT");
            assertEqual(result.nNrunsCapped(), 2);
        });

        test("does not cap short N-runs", () -> {
            var result = processor.processOneSequence("ACGTACGTNNNNNACGTACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTNNNNNACGTACGT");
            assertEqual(result.nNrunsCapped(), 0);
        });
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    private static void testMetrics() {
        test("calculates clean_length", () -> {
            var result = processor.processOneSequence("ACGTACGTACGT");
            assertEqual(result.cleanLength(), 12);
        });

        test("calculates n_fraction", () -> {
            var result = processor.processOneSequence("ACGTACGTNNNNACGTACGT"); // 4 N out of 20
            assertClose(result.nFraction(), 4.0 / 20.0);
        });

        test("calculates gc_content", () -> {
            var result = processor.processOneSequence("AACCGGTT"); // 4 GC out of 8 ACGT
            assertClose(result.gcContent(), 0.5);
        });

        test("calculates gc_content with N", () -> {
            var result = processor.processOneSequence("AACCGGTTNN"); // 4 GC out of 8 ACGT (N excluded)
            assertClose(result.gcContent(), 0.5);
        });

        test("calculates non_acgtn_fraction", () -> {
            // 8 anchor chars at start, 4 ambiguous (RYRY), 8 anchor chars at end = 20 total
            var result = processor.processOneSequence("ACGTACGTRYRYACGTACGT");
            assertClose(result.nonAcgtnFraction(), 4.0 / 20.0);
        });

        test("non_iupac_fraction is 0 for valid IUPAC", () -> {
            var result = processor.processOneSequence("ACGTACGTACGTRYSWKMBDHVN");
            assertEqual(result.nonIupacFraction(), 0.0);
        });

        test("handles empty sequence metrics", () -> {
            var result = processor.processOneSequence("XXXX"); // wipes to empty
            assertEqual(result.cleanLength(), 0);
            assertEqual(result.nFraction(), null);
            assertEqual(result.gcContent(), null);
        });
    }

    // =========================================================================
    // MD5 hashes
    // =========================================================================

    private static void testMd5() {
        test("generates md5_clean", () -> {
            var result = processor.processOneSequence("ACGTACGTACGT");
            assertTrue(result.md5Clean() != null);
            assertEqual(result.md5Clean().length(), 32);
            assertTrue(result.md5Clean().matches("^[a-f0-9]{32}$"));
        });

        test("md5_raw strips non-IUPAC before hashing", () -> {
            var result1 = processor.processOneSequence("ACGTACGTACGT");
            var result2 = processor.processOneSequence("ACGT---ACGT...ACGT");
            assertEqual(result1.md5(), result2.md5());
        });

        test("md5 is null for empty sequence", () -> {
            var result = processor.processOneSequence("");
            assertEqual(result.md5Clean(), null);
        });
    }

    // =========================================================================
    // Full pipeline examples from pseudocode
    // =========================================================================

    private static void testFullPipelineExamples() {
        test("example: whitespace normalization", () -> {
            var result = processor.processOneSequence("acgtac gta  cgt");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
        });

        test("example: gap removal", () -> {
            var result = processor.processOneSequence("ACGT-ACGT..ACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTACGT");
        });

        test("example: anchor trimming", () -> {
            var result = processor.processOneSequence("THISISMYGBIFSEQUENCEACGTACGTACGTNNNNNENDOFSEQUENCE");
            assertTrue(result.cleanSequence().startsWith("ACGTACGT"));
            assertEqual(result.endsTrimmed(), true);
        });

        test("example: U to T conversion", () -> {
            var result = processor.processOneSequence("ACGTUACGTU");
            assertEqual(result.cleanSequence(), "ACGTTACGTT");
        });

        test("example: N-run capping", () -> {
            var result = processor.processOneSequence("ACGTACGTNNNNNNNNNNNNNNACGTACGTNNNNNNNNNACGTACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTNNNNNACGTACGTNNNNNACGTACGT");
        });
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    private static void testEdgeCases() {
        test("handles null input", () -> {
            var result = processor.processOneSequence(null);
            assertEqual(result.rawSequence(), "");
            assertEqual(result.cleanSequence(), "");
        });

        test("handles empty input", () -> {
            var result = processor.processOneSequence("");
            assertEqual(result.rawSequence(), "");
            assertEqual(result.cleanSequence(), "");
        });

        test("preserves seq_id", () -> {
            var result = processor.processOneSequence("ACGTACGTACGT", "my-sequence-001");
            assertEqual(result.seqId(), "my-sequence-001");
        });

        test("custom config: different nrun_cap", () -> {
            var customConfig = new SequenceProcessor.Config(
                "ACGTU", 8, "ACGTU", "[-\\.]", "UNMERGED",
                "ACGTURYSWKMBDHVN", "ACGTRYSWKMBDHVN", 3, 2
            );
            var customProcessor = new SequenceProcessor(customConfig);
            var result = customProcessor.processOneSequence("ACGTACGTNNNACGTACGT");
            assertEqual(result.cleanSequence(), "ACGTACGTNNACGTACGT");
        });

        test("custom config: different anchor_minrun", () -> {
            var customConfig = new SequenceProcessor.Config(
                "ACGTU", 4, "ACGTU", "[-\\.]", "UNMERGED",
                "ACGTURYSWKMBDHVN", "ACGTRYSWKMBDHVN", 6, 5
            );
            var customProcessor = new SequenceProcessor(customConfig);
            var result = customProcessor.processOneSequence("XXXACGTXXX");
            assertEqual(result.cleanSequence(), "ACGT");
        });
    }
}
