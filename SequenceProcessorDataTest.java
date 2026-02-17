import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data-driven tests using test_sequences.json
 */
public class SequenceProcessorDataTest {

    private static int passed = 0;
    private static int failed = 0;
    private static List<String> failures = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        SequenceProcessor processor = new SequenceProcessor();

        // Load test data
        String json = Files.readString(Path.of("testData/test_sequences.json"));
        List<TestCase> testCases = parseTestCases(json);

        System.out.println("Running data-driven tests with " + testCases.size() + " sequences...\n");

        // Group by category
        String currentCategory = null;
        int categoryCount = 0;

        for (TestCase tc : testCases) {
            if (!tc.category.equals(currentCategory)) {
                if (currentCategory != null) {
                    System.out.println("  ✓ " + categoryCount + " passed\n");
                }
                currentCategory = tc.category;
                categoryCount = 0;
                System.out.println("--- " + tc.category + " ---");
            }

            test(tc.seqId, () -> {
                SequenceProcessor.Result result = processor.processOneSequence(tc.rawSequence, tc.seqId);

                assertEqual(result.sequence(), tc.expectedCleanSequence, "sequence");
                assertEqual(result.sequenceLength(), tc.expectedCleanLength, "sequence_length");
                assertEqual(result.nNrunsCapped(), tc.expectedNrunsCapped, "n_nruns_capped");
                assertEqual(result.naturalLanguageDetected(), tc.expectedNaturalLanguageDetected, "natural_language_detected");
                assertEqual(result.endsTrimmed(), tc.expectedEndsTrimmed, "ends_trimmed");
                assertEqual(result.gapAndWhitespaceRemoved(), tc.expectedGapAndWhitespaceRemoved, "gap_and_whitespace_removed");

                assertClose(result.nonIupacFraction(), tc.expectedNonIupacFraction, "non_iupac_fraction");
                assertClose(result.nonAcgtnFraction(), tc.expectedNonAcgtnFraction, "non_acgtn_fraction");
                assertClose(result.nFraction(), tc.expectedNFraction, "n_fraction");
                assertClose(result.gcContent(), tc.expectedGcContent, "gc_content");
            });

            categoryCount++;
        }

        if (currentCategory != null) {
            System.out.println("  ✓ " + categoryCount + " passed\n");
        }

        // Summary
        System.out.println("========================================");
        System.out.println("Tests: " + (passed + failed) + " | Passed: " + passed + " | Failed: " + failed);
        System.out.println("========================================\n");

        if (!failures.isEmpty()) {
            System.out.println("Failures:");
            for (String f : failures) {
                System.out.println("  - " + f);
            }
        }

        System.exit(failed > 0 ? 1 : 0);
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    private static void test(String name, Runnable testFn) {
        try {
            testFn.run();
            passed++;
        } catch (AssertionError e) {
            System.out.println("✗ " + name);
            System.out.println("  " + e.getMessage());
            failures.add(name + ": " + e.getMessage());
            failed++;
        }
    }

    private static void assertEqual(Object actual, Object expected, String field) {
        if (actual == null && expected == null) return;
        if (actual == null || !actual.equals(expected)) {
            throw new AssertionError(field + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEqual(int actual, int expected, String field) {
        if (actual != expected) {
            throw new AssertionError(field + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEqual(boolean actual, boolean expected, String field) {
        if (actual != expected) {
            throw new AssertionError(field + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertClose(Double actual, Double expected, String field) {
        double tolerance = 0.0001;
        if (actual == null && expected == null) return;
        if (actual == null || expected == null) {
            throw new AssertionError(field + ": expected " + expected + ", got " + actual);
        }
        if (Math.abs(actual - expected) > tolerance) {
            throw new AssertionError(field + ": expected " + expected + ", got " + actual);
        }
    }

    // =========================================================================
    // Simple JSON parser for test_sequences.json
    // =========================================================================

    private static class TestCase {
        String seqId;
        String rawSequence;
        String category;
        String expectedCleanSequence;
        int expectedCleanLength;
        Double expectedNonIupacFraction;
        Double expectedNonAcgtnFraction;
        Double expectedNFraction;
        int expectedNrunsCapped;
        Double expectedGcContent;
        boolean expectedNaturalLanguageDetected;
        boolean expectedEndsTrimmed;
        boolean expectedGapAndWhitespaceRemoved;
    }

    private static List<TestCase> parseTestCases(String json) {
        List<TestCase> testCases = new ArrayList<>();

        // Find sequences array
        int seqStart = json.indexOf("\"sequences\"");
        if (seqStart == -1) return testCases;

        // Find array start
        int arrayStart = json.indexOf("[", seqStart);
        if (arrayStart == -1) return testCases;

        // Parse each sequence object
        int pos = arrayStart + 1;
        while (pos < json.length()) {
            int objStart = json.indexOf("{", pos);
            if (objStart == -1) break;

            // Find matching closing brace (handling nested objects)
            int objEnd = findMatchingBrace(json, objStart);
            if (objEnd == -1) break;

            String objJson = json.substring(objStart, objEnd + 1);
            TestCase tc = parseTestCase(objJson);
            if (tc != null) {
                testCases.add(tc);
            }

            pos = objEnd + 1;

            // Check if we've reached the end of the array
            int nextComma = json.indexOf(",", pos);
            int arrayEnd = json.indexOf("]", pos);
            if (arrayEnd != -1 && (nextComma == -1 || arrayEnd < nextComma)) {
                break;
            }
        }

        return testCases;
    }

    private static int findMatchingBrace(String json, int start) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escape) {
                escape = false;
                continue;
            }

            if (c == '\\') {
                escape = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }

        return -1;
    }

    private static TestCase parseTestCase(String json) {
        TestCase tc = new TestCase();

        tc.seqId = extractString(json, "seq_id");
        tc.rawSequence = extractString(json, "raw_sequence");
        tc.category = extractString(json, "category");

        // Extract expected values
        int expectedStart = json.indexOf("\"expected\"");
        if (expectedStart != -1) {
            int expectedObjStart = json.indexOf("{", expectedStart);
            int expectedObjEnd = findMatchingBrace(json, expectedObjStart);
            String expectedJson = json.substring(expectedObjStart, expectedObjEnd + 1);

            tc.expectedCleanSequence = extractString(expectedJson, "sequence");
            tc.expectedCleanLength = extractInt(expectedJson, "sequence_length");
            tc.expectedNonIupacFraction = extractDouble(expectedJson, "non_iupac_fraction");
            tc.expectedNonAcgtnFraction = extractDouble(expectedJson, "non_acgtn_fraction");
            tc.expectedNFraction = extractDouble(expectedJson, "n_fraction");
            tc.expectedNrunsCapped = extractInt(expectedJson, "n_nruns_capped");
            tc.expectedGcContent = extractDouble(expectedJson, "gc_content");
            tc.expectedNaturalLanguageDetected = extractBoolean(expectedJson, "natural_language_detected");
            tc.expectedEndsTrimmed = extractBoolean(expectedJson, "ends_trimmed");
            tc.expectedGapAndWhitespaceRemoved = extractBoolean(expectedJson, "gap_and_whitespace_removed");
        }

        return tc;
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return null;
    }

    private static int extractInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 0;
    }

    private static Double extractDouble(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+\\.?\\d*(?:[eE][+-]?\\d+)?|null)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            String val = m.group(1);
            if ("null".equals(val)) return null;
            return Double.parseDouble(val);
        }
        return null;
    }

    private static boolean extractBoolean(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return false;
    }
}
