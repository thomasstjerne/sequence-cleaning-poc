import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DNA/RNA sequence cleaning processor.
 * Processes raw sequences through a multi-stage pipeline to produce cleaned sequences with metrics.
 */
public class SequenceProcessor {

    /**
     * Configuration for sequence processing
     */
    public record Config(
        String anchorChars,
        int anchorMinrun,
        String anchorStrict,
        String gapRegex,
        String naturalLanguageRegex,
        String iupacRna,
        String iupacDna,
        int nrunCapFrom,
        int nrunCapTo
    ) {
        /**
         * Load configuration from the default config.yaml file
         */
        public static Config defaultConfig() {
            try {
                return loadFromYaml("config.yaml");
            } catch (IOException e) {
                // Fallback to hardcoded defaults if file not found
                return new Config(
                    "ACGTU",
                    8,
                    "ACGTU",
                    "[-\\.]",
                    "UNMERGED",
                    "ACGTURYSWKMBDHVN",
                    "ACGTRYSWKMBDHVN",
                    6,
                    5
                );
            }
        }

        /**
         * Load configuration from a YAML file
         */
        public static Config loadFromYaml(String filePath) throws IOException {
            String content = Files.readString(Path.of(filePath));
            Map<String, String> values = parseSimpleYaml(content);

            return new Config(
                values.getOrDefault("anchor_chars", "ACGTU"),
                Integer.parseInt(values.getOrDefault("anchor_minrun", "8")),
                values.getOrDefault("anchor_strict", "ACGTU"),
                values.getOrDefault("gap_regex", "[-\\.]"),
                values.getOrDefault("natural_language_regex", "UNMERGED"),
                values.getOrDefault("iupac_rna", "ACGTURYSWKMBDHVN"),
                values.getOrDefault("iupac_dna", "ACGTRYSWKMBDHVN"),
                Integer.parseInt(values.getOrDefault("nrun_cap_from", "6")),
                Integer.parseInt(values.getOrDefault("nrun_cap_to", "5"))
            );
        }

        /**
         * Parse a simple YAML file (supports basic key: value pairs)
         */
        private static Map<String, String> parseSimpleYaml(String yamlContent) {
            Map<String, String> config = new HashMap<>();

            for (String line : yamlContent.split("\n")) {
                String trimmed = line.trim();

                // Skip comments and empty lines
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                // Parse key: value
                int colonIndex = line.indexOf(':');
                if (colonIndex == -1) {
                    continue;
                }

                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                // Remove inline comments
                int commentIndex = value.indexOf('#');
                if (commentIndex != -1) {
                    value = value.substring(0, commentIndex).trim();
                }

                // Remove quotes if present
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }

                config.put(key, value);
            }

            return config;
        }
    }

    /**
     * Result of sequence processing containing cleaned sequence and metrics
     */
    public record Result(
        String seqId,
        String rawSequence,
        String sequence,
        int sequenceLength,
        Double nonIupacFraction,
        Double nonACGTNFraction,
        Double nFraction,
        int nNrunsCapped,
        Double gcContent,
        boolean naturalLanguageDetected,
        boolean endsTrimmed,
        boolean gapsOrWhitespaceRemoved,
        String nucleotideSequenceID,
        boolean invalid
    ) {}

    /**
     * Helper record for trim operations
     */
    private record TrimResult(String s, boolean did) {}

    private final Config config;

    public SequenceProcessor() {
        this(Config.defaultConfig());
    }

    public SequenceProcessor(Config config) {
        this.config = config;
    }

    /**
     * Process a single sequence through the cleaning pipeline
     */
    public Result processOneSequence(String seq) {
        return processOneSequence(seq, null);
    }

    /**
     * Process a single sequence through the cleaning pipeline with an optional ID
     */
    public Result processOneSequence(String seq, String seqId) {
        String raw = seq != null ? seq : "";

        // Stage A: normalize whitespace + uppercase
        // example: "acgtac gta  cgt" -> "ACGTACGTACGT"
        boolean rawHasWs = Pattern.compile("\\s").matcher(raw).find();
        String s0 = raw.replaceAll("\\s+", "");
        String s1 = s0.toUpperCase();

        // Stage B: detect natural language
        // example: "ACGTUNMERGEDACGT" -> naturalLanguageDetected = true
        Pattern naturalLanguagePattern = Pattern.compile(config.naturalLanguageRegex());
        boolean naturalLanguageDetected = naturalLanguagePattern.matcher(s1).find();

        // Stage C: remove gaps
        // example: "ACGT-ACGT..ACGT" -> "ACGTACGTACGT"
        Pattern gapPattern = Pattern.compile(config.gapRegex());
        boolean hasGaps = gapPattern.matcher(s1).find();
        String s2 = s1.replaceAll(config.gapRegex(), "");
        boolean gapsOrWhitespaceRemoved = rawHasWs || hasGaps;

        // Stage D: trim to anchors (front & back)
        // example: with anchor_chars="ACGTU" and anchor_minrun=8:
        // "THISISMYGBIFSEQUENCEACGTACGTACGTNNNNNENDOFSEQUENCE" -> "ACGTACGTACGT"
        TrimResult tFirst = trimToFirstAnchorOrWipe(s2, config.anchorChars(), config.anchorMinrun());
        String s3 = tFirst.s();
        TrimResult tLast = trimToLastAnchor(s3, config.anchorChars(), config.anchorMinrun());
        String s4 = tLast.s();
        boolean endsTrimmed = tFirst.did() || tLast.did();

        // Stage E: U -> T (RNA->DNA)
        // example: "ACGTUACGTU" -> "ACGTTACGTT"
        String s5 = s4.replace("U", "T");

        // Stage F: cap N-runs (apply N-run cap to s5 -> s6)
        // example: with nrun_cap_from=6 and nrun_cap_to=5:
        // "ACGTACGTNNNNNNNNNNNNNNACGTACGTNNNNNNNNNACGTACGT" -> "ACGTACGTNNNNNACGTACGTNNNNNACGTACGT"
        String capPattern = "N{" + config.nrunCapFrom() + ",}";
        int nNrunsCapped = countRegex(s5, capPattern);
        String capToStr = "N".repeat(config.nrunCapTo());
        String s6 = s5.replaceAll(capPattern, capToStr);

        // Additional metrics (on s6)
        int sequenceLength = s6.length();
        int nCount = countFixed(s6, 'N');
        Double nFraction = sequenceLength > 0 ? (double) nCount / sequenceLength : null;

        // Compute ambiguous/non-IUPAC counts AFTER capping (on s6)
        int nonAcgtnCount = countRegex(s6, "[^ACGTN]");
        int nonIupacCount = countRegex(s6, "[^" + config.iupacDna() + "]");
        Double nonACGTNFraction = sequenceLength > 0 ? (double) nonAcgtnCount / sequenceLength : null;
        Double nonIupacFraction = sequenceLength > 0 ? (double) nonIupacCount / sequenceLength : null;

        // GC content (A/C/G/T only in denominator)
        int gc = countRegex(s6, "[GC]");
        int acgt = countRegex(s6, "[ACGT]");
        Double gcContent = acgt > 0 ? (double) gc / acgt : null;

        // MD5 of the final cleaned sequence
        String nucleotideSequenceID = md5(s6);

        // Invalid if non-IUPAC characters found or natural language detected
        boolean invalid = (nonIupacFraction != null && nonIupacFraction > 0) || naturalLanguageDetected;

        return new Result(
            seqId,
            raw,
            invalid ? null : s6,
            sequenceLength,
            nonIupacFraction,
            nonACGTNFraction,
            nFraction,
            nNrunsCapped,
            gcContent,
            naturalLanguageDetected,
            endsTrimmed,
            gapsOrWhitespaceRemoved,
            invalid ? null : nucleotideSequenceID,
            invalid
        );
    }

    /**
     * Trims the sequence to the first anchor run of at least minRun consecutive anchor characters.
     * If no valid anchor is found, returns empty string (wipes the sequence).
     */
    private TrimResult trimToFirstAnchorOrWipe(String seq, String anchorChars, int minRun) {
        if (seq == null || seq.isEmpty()) {
            return new TrimResult("", false);
        }

        Pattern anchorPattern = Pattern.compile("[" + anchorChars + "]{" + minRun + ",}");
        Matcher matcher = anchorPattern.matcher(seq);

        if (!matcher.find()) {
            // No valid anchor found - wipe the sequence
            return new TrimResult("", true);
        }

        int startIndex = matcher.start();
        String trimmed = seq.substring(startIndex);

        return new TrimResult(trimmed, startIndex > 0);
    }

    /**
     * Trims the sequence to the last anchor run of at least minRun consecutive anchor characters.
     */
    private TrimResult trimToLastAnchor(String seq, String anchorChars, int minRun) {
        if (seq == null || seq.isEmpty()) {
            return new TrimResult("", false);
        }

        Pattern anchorPattern = Pattern.compile("[" + anchorChars + "]{" + minRun + ",}");
        Matcher matcher = anchorPattern.matcher(seq);

        int lastMatchEnd = -1;
        while (matcher.find()) {
            lastMatchEnd = matcher.end();
        }

        if (lastMatchEnd == -1) {
            return new TrimResult(seq, false);
        }

        String trimmed = seq.substring(0, lastMatchEnd);
        return new TrimResult(trimmed, lastMatchEnd < seq.length());
    }

    /**
     * Counts regex matches in a string
     */
    private int countRegex(String str, String pattern) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        Matcher matcher = Pattern.compile(pattern).matcher(str);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * Counts occurrences of a character in a string
     */
    private int countFixed(String str, char c) {
        if (str == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    /**
     * Computes MD5 hash of a string
     */
    private String md5(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(str.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * Main method for demonstration
     */
    public static void main(String[] args) {
        SequenceProcessor processor = new SequenceProcessor();

        // Example from pseudocode
        Result result = processor.processOneSequence(
            "acgtac gta  cgt",
            "example-001"
        );

        System.out.println("Input:  " + result.rawSequence());
        System.out.println("Output: " + result.sequence());
        System.out.println("Length: " + result.sequenceLength());
        System.out.println("GC:     " + result.gcContent());
        System.out.println("nucleotideSequenceID: " + result.nucleotideSequenceID());
    }
}
