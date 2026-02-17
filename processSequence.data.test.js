const assert = require('assert');
const { processOneSequence, DEFAULT_CONFIG } = require('./processSequence');
const testData = require('./testData/test_sequences.json');

/**
 * Data-driven tests using test_sequences.json
 */

let passed = 0;
let failed = 0;
const failures = [];

function test(name, fn) {
  try {
    fn();
    passed++;
  } catch (err) {
    console.log(`✗ ${name}`);
    console.log(`  ${err.message}`);
    failures.push({ name, error: err.message });
    failed++;
  }
}

function assertEqual(actual, expected, field) {
  if (actual !== expected) {
    throw new Error(`${field}: expected ${expected}, got ${actual}`);
  }
}

function assertClose(actual, expected, field, tolerance = 0.0001) {
  if (actual === null && expected === null) return;
  if (actual === null || expected === null) {
    throw new Error(`${field}: expected ${expected}, got ${actual}`);
  }
  if (Math.abs(actual - expected) > tolerance) {
    throw new Error(`${field}: expected ${expected}, got ${actual}`);
  }
}

console.log(`Running data-driven tests with ${testData.sequences.length} sequences...\n`);

// Group tests by category for better output
const byCategory = {};
testData.sequences.forEach(seq => {
  const cat = seq.category || 'uncategorized';
  if (!byCategory[cat]) byCategory[cat] = [];
  byCategory[cat].push(seq);
});

for (const [category, sequences] of Object.entries(byCategory)) {
  console.log(`--- ${category} (${sequences.length} sequences) ---`);

  for (const testCase of sequences) {
    const { seq_id, raw_sequence, expected } = testCase;

    test(`${seq_id}`, () => {
      const result = processOneSequence(raw_sequence, DEFAULT_CONFIG, seq_id);

      // Check all expected fields
      assertEqual(result.sequence, expected.sequence, 'sequence');
      assertEqual(result.sequence_length, expected.sequence_length, 'sequence_length');
      assertEqual(result.n_nruns_capped, expected.n_nruns_capped, 'n_nruns_capped');
      assertEqual(result.unmerged_reads_detected, expected.unmerged_reads_detected, 'unmerged_reads_detected');
      assertEqual(result.ends_trimmed, expected.ends_trimmed, 'ends_trimmed');
      assertEqual(result.gap_and_whitespace_removed, expected.gap_and_whitespace_removed, 'gap_and_whitespace_removed');

      // Numeric comparisons with tolerance
      assertClose(result.non_iupac_fraction, expected.non_iupac_fraction, 'non_iupac_fraction');
      assertClose(result.non_acgtn_fraction, expected.non_acgtn_fraction, 'non_acgtn_fraction');
      assertClose(result.n_fraction, expected.n_fraction, 'n_fraction');
      assertClose(result.gc_content, expected.gc_content, 'gc_content');
    });
  }

  console.log(`  ✓ ${sequences.length} passed\n`);
}

// Summary
console.log('========================================');
console.log(`Tests: ${passed + failed} | Passed: ${passed} | Failed: ${failed}`);
console.log('========================================\n');

if (failures.length > 0) {
  console.log('Failures:');
  failures.forEach(f => console.log(`  - ${f.name}: ${f.error}`));
}

process.exit(failed > 0 ? 1 : 0);
