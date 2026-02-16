# Sequence Cleaning

DNA/RNA sequence cleaning and normalization library with implementations in JavaScript and Java.

## Overview

This library processes raw DNA/RNA sequences through a multi-stage cleaning pipeline, producing normalized sequences along with quality metrics. It is designed for use in bioinformatics pipelines, particularly for preparing sequences for indexing and comparison.

## Pipeline Stages

| Stage | Description | Example |
|-------|-------------|---------|
| A | Normalize whitespace + uppercase | `"acgt acgt"` → `"ACGTACGT"` |
| B | Detect unmerged reads | Flags sequences containing `UNMERGED` marker |
| C | Remove gaps | `"ACGT-ACGT..ACGT"` → `"ACGTACGTACGT"` |
| D | Trim to anchors | Removes non-anchor characters from ends |
| E | RNA to DNA conversion | `"ACGU"` → `"ACGT"` |
| F | Cap N-runs | `"ACGTNNNNNNNNNN"` → `"ACGTNNNNN"` |

## Output Fields

| Field | Type | Description |
|-------|------|-------------|
| `clean_sequence` | string | Final cleaned sequence |
| `sequence_length` | int | Length of cleaned sequence in bytes |
| `non_iupac_fraction` | float | Fraction of non-IUPAC characters |
| `non_acgtn_fraction` | float | Fraction of ambiguous IUPAC codes (not A/C/G/T/N) |
| `n_fraction` | float | Fraction of N characters |
| `n_nruns_capped` | int | Number of N-runs that were capped |
| `gc_content` | float | GC content (based on A/C/G/T only) |
| `unmerged_reads_detected` | bool | Whether UNMERGED marker was found |
| `ends_trimmed` | bool | Whether ends were trimmed |
| `gap_and_whitespace_removed` | bool | Whether gaps or whitespace were removed |
| `md5` | string | MD5 of final cleaned sequence |

## Configuration

The pipeline is configured via `config.yaml`:

```yaml
anchor_chars: "ACGTU"       # Valid anchor characters
anchor_minrun: 8            # Minimum consecutive anchors required
gap_regex: "[-\\.]"         # Characters to remove as gaps
marker_regex: "UNMERGED"    # Marker for unmerged reads detection
iupac_rna: "ACGTURYSWKMBDHVN"
iupac_dna: "ACGTRYSWKMBDHVN"
nrun_cap_from: 6            # Cap N-runs of this length or longer
nrun_cap_to: 5              # Cap them to this length
```

## Usage

### JavaScript

```javascript
const { processOneSequence, loadConfig } = require('./processSequence');

// Using default config (from config.yaml)
const result = processOneSequence("ACGT-ACGT  NNNNNNNNNN ACGT");

console.log(result.clean_sequence);  // "ACGTACGTNNNNNACGT"
console.log(result.sequence_length);    // 17
console.log(result.gc_content);      // 0.5

// With custom config
const config = loadConfig('/path/to/custom-config.yaml');
const result = processOneSequence(sequence, config, "seq-001");
```

### Java

```java
SequenceProcessor processor = new SequenceProcessor();

// Using default config (from config.yaml)
SequenceProcessor.Result result = processor.processOneSequence(
    "ACGT-ACGT  NNNNNNNNNN ACGT"
);

System.out.println(result.cleanSequence());  // "ACGTACGTNNNNNACGT"
System.out.println(result.cleanLength());    // 17
System.out.println(result.gcContent());      // 0.5

// With custom config
SequenceProcessor.Config config = SequenceProcessor.Config.loadFromYaml("custom-config.yaml");
SequenceProcessor processor = new SequenceProcessor(config);
```

## Running Tests

### JavaScript

```bash
# Run all tests
npm test

# Run unit tests only
npm run test:unit

# Run data-driven tests only
npm run test:data
```

### Java

```bash
# Compile
javac SequenceProcessor.java SequenceProcessorTest.java SequenceProcessorDataTest.java

# Run unit tests
java SequenceProcessorTest

# Run data-driven tests
java SequenceProcessorDataTest
```

## Test Data

- `testData/` - 9,457 test sequences across 6 files:
  - `test_sequences.json` - 110 curated test sequences
  - `n_fraction_gt_0_05.json` - High N fraction sequences
  - `n_nruns_capped_gt_1.json` - Multiple N-runs capped
  - `non_acgtn_fraction_gt_0_01.json` - Ambiguous IUPAC codes
  - `non_iupac_fraction_gt_0.json` - Non-IUPAC characters
  - `unmerged_reads_detected.json` - UNMERGED marker sequences

## Requirements

- **JavaScript**: Node.js >= 18.0.0
- **Java**: JDK 17+

## License

ISC
