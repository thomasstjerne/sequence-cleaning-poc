const assert = require('assert');
const {
  processOneSequence,
  trimToFirstAnchorOrWipe,
  trimToLastAnchor,
  DEFAULT_CONFIG
} = require('./processSequence');

/**
 * Simple test runner
 */
let passed = 0;
let failed = 0;

function test(name, fn) {
  try {
    fn();
    console.log(`✓ ${name}`);
    passed++;
  } catch (err) {
    console.log(`✗ ${name}`);
    console.log(`  ${err.message}`);
    failed++;
  }
}

function assertClose(actual, expected, tolerance = 0.0001) {
  if (Math.abs(actual - expected) > tolerance) {
    throw new Error(`Expected ${expected}, got ${actual}`);
  }
}

// =============================================================================
// trimToFirstAnchorOrWipe tests
// =============================================================================

console.log('\n--- trimToFirstAnchorOrWipe ---');

test('finds anchor at start (no trim needed)', () => {
  const result = trimToFirstAnchorOrWipe('ACGTACGTACGT', 'ACGTU', 8);
  assert.strictEqual(result.s, 'ACGTACGTACGT');
  assert.strictEqual(result.did, false);
});

test('trims junk before anchor', () => {
  const result = trimToFirstAnchorOrWipe('XXXXXACGTACGTACGT', 'ACGTU', 8);
  assert.strictEqual(result.s, 'ACGTACGTACGT');
  assert.strictEqual(result.did, true);
});

test('wipes sequence if no valid anchor found', () => {
  const result = trimToFirstAnchorOrWipe('ACGTXXXXXACGT', 'ACGTU', 8);
  assert.strictEqual(result.s, '');
  assert.strictEqual(result.did, true);
});

test('handles empty string', () => {
  const result = trimToFirstAnchorOrWipe('', 'ACGTU', 8);
  assert.strictEqual(result.s, '');
  assert.strictEqual(result.did, false);
});

test('handles null/undefined', () => {
  const result = trimToFirstAnchorOrWipe(null, 'ACGTU', 8);
  assert.strictEqual(result.s, '');
  assert.strictEqual(result.did, false);
});

// =============================================================================
// trimToLastAnchor tests
// =============================================================================

console.log('\n--- trimToLastAnchor ---');

test('finds anchor at end (no trim needed)', () => {
  const result = trimToLastAnchor('ACGTACGTACGT', 'ACGTU', 8);
  assert.strictEqual(result.s, 'ACGTACGTACGT');
  assert.strictEqual(result.did, false);
});

test('trims junk after anchor', () => {
  const result = trimToLastAnchor('ACGTACGTACGTXXXXX', 'ACGTU', 8);
  assert.strictEqual(result.s, 'ACGTACGTACGT');
  assert.strictEqual(result.did, true);
});

test('keeps sequence if no anchor found', () => {
  const result = trimToLastAnchor('ACGTXXX', 'ACGTU', 8);
  assert.strictEqual(result.s, 'ACGTXXX');
  assert.strictEqual(result.did, false);
});

test('handles empty string', () => {
  const result = trimToLastAnchor('', 'ACGTU', 8);
  assert.strictEqual(result.s, '');
  assert.strictEqual(result.did, false);
});

// =============================================================================
// Stage A: Whitespace normalization + uppercase
// =============================================================================

console.log('\n--- Stage A: Whitespace + Uppercase ---');

test('converts lowercase to uppercase', () => {
  const result = processOneSequence('acgtacgtacgt');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
});

test('removes whitespace', () => {
  const result = processOneSequence('acgt acgt  acgt');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
  assert.strictEqual(result.gaps_or_whitespace_removed, true);
});

test('removes tabs and newlines', () => {
  const result = processOneSequence('acgt\tacgt\nacgt');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
});

// =============================================================================
// Stage B: Unmerged reads detection
// =============================================================================

console.log('\n--- Stage B: Natural Language Detection ---');

test('detects UNMERGED marker', () => {
  const result = processOneSequence('ACGTACGTUNMERGEDACGTACGT');
  assert.strictEqual(result.natural_language_detected, true);
});

test('no detection when marker absent', () => {
  const result = processOneSequence('ACGTACGTACGTACGT');
  assert.strictEqual(result.natural_language_detected, false);
});

// =============================================================================
// Stage C: Gap removal
// =============================================================================

console.log('\n--- Stage C: Gap Removal ---');

test('removes hyphens', () => {
  const result = processOneSequence('ACGT-ACGT-ACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
  assert.strictEqual(result.gaps_or_whitespace_removed, true);
});

test('removes dots', () => {
  const result = processOneSequence('ACGT..ACGT..ACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
});

test('removes mixed gaps', () => {
  const result = processOneSequence('ACGT--.ACGT');
  assert.strictEqual(result.sequence, 'ACGTACGT');
});

// =============================================================================
// Stage D: U to T conversion
// =============================================================================

console.log('\n--- Stage D: U to T Conversion ---');

test('converts U to T', () => {
  const result = processOneSequence('ACGUACGUACGU');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
});

test('converts mixed U and T', () => {
  const result = processOneSequence('ACGUACGTACGU');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
});

// =============================================================================
// Stage E: Question mark to N
// =============================================================================

console.log('\n--- Stage E: Question Mark to N ---');

test('converts ? to N', () => {
  const result = processOneSequence('ACGTACGT?ACGTACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTNACGTACGT');
});

// =============================================================================
// Stage F: Anchor trimming
// =============================================================================

console.log('\n--- Stage F: Anchor Trimming ---');

test('trims non-anchor prefix', () => {
  const result = processOneSequence('XXXXACGTACGTACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
  assert.strictEqual(result.ends_trimmed, true);
});

test('trims non-anchor suffix', () => {
  const result = processOneSequence('ACGTACGTACGTXXXX');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
  assert.strictEqual(result.ends_trimmed, true);
});

test('trims both ends', () => {
  const result = processOneSequence('XXXXACGTACGTACGTXXXX');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
  assert.strictEqual(result.ends_trimmed, true);
});

test('no trim when sequence is all anchors', () => {
  const result = processOneSequence('ACGTACGTACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
  assert.strictEqual(result.ends_trimmed, false);
});

test('wipes sequence with no valid anchor run', () => {
  const result = processOneSequence('ACGTXXXXACGT');
  assert.strictEqual(result.sequence, '');
  assert.strictEqual(result.ends_trimmed, true);
});

// =============================================================================
// Stage G: N-run capping
// =============================================================================

console.log('\n--- Stage G: N-run Capping ---');

test('caps long N-runs to 5', () => {
  const result = processOneSequence('ACGTACGTNNNNNNNNNNACGTACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTNNNNNACGTACGT');
  assert.strictEqual(result.n_nruns_capped, 1);
});

test('caps multiple N-runs', () => {
  const result = processOneSequence('ACGTACGTNNNNNNACGTNNNNNNNNACGTACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTNNNNNACGTNNNNNACGTACGT');
  assert.strictEqual(result.n_nruns_capped, 2);
});

test('does not cap short N-runs', () => {
  const result = processOneSequence('ACGTACGTNNNNNACGTACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTNNNNNACGTACGT');
  assert.strictEqual(result.n_nruns_capped, 0);
});

// =============================================================================
// Metrics
// =============================================================================

console.log('\n--- Metrics ---');

test('calculates sequence_length', () => {
  const result = processOneSequence('ACGTACGTACGT');
  assert.strictEqual(result.sequence_length, 12);
});

test('calculates n_fraction', () => {
  const result = processOneSequence('ACGTACGTNNNNACGTACGT'); // 4 N out of 20
  assertClose(result.n_fraction, 4 / 20);
});

test('calculates gc_content', () => {
  const result = processOneSequence('AACCGGTT'); // 4 GC out of 8 ACGT
  assertClose(result.gc_content, 0.5);
});

test('calculates gc_content with N', () => {
  const result = processOneSequence('AACCGGTTNN'); // 4 GC out of 8 ACGT (N excluded)
  assertClose(result.gc_content, 0.5);
});

test('calculates non_acgtn_fraction', () => {
  // 8 anchor chars at start, 4 ambiguous (RYRY), 8 anchor chars at end = 20 total
  const result = processOneSequence('ACGTACGTRYRYACGTACGT');
  assertClose(result.non_acgtn_fraction, 4 / 20);
});

test('non_iupac_fraction is 0 for valid IUPAC', () => {
  const result = processOneSequence('ACGTACGTACGTRYSWKMBDHVN');
  assert.strictEqual(result.non_iupac_fraction, 0);
});

test('handles empty sequence metrics', () => {
  const result = processOneSequence('XXXX'); // wipes to empty
  assert.strictEqual(result.sequence_length, 0);
  assert.strictEqual(result.n_fraction, null);
  assert.strictEqual(result.gc_content, null);
});

// =============================================================================
// MD5 hashes
// =============================================================================

console.log('\n--- MD5 Hashes ---');

test('generates nucleotide_sequence_id', () => {
  const result = processOneSequence('ACGTACGTACGT');
  // Verify it's a valid 32-character hex MD5 hash
  assert.ok(result.nucleotide_sequence_id);
  assert.strictEqual(result.nucleotide_sequence_id.length, 32);
  assert.ok(/^[a-f0-9]{32}$/.test(result.nucleotide_sequence_id));
});

test('nucleotide_sequence_id is null for empty sequence', () => {
  const result = processOneSequence('');
  assert.strictEqual(result.nucleotide_sequence_id, null);
});

// =============================================================================
// Full pipeline examples from pseudocode
// =============================================================================

console.log('\n--- Full Pipeline Examples ---');

test('example: whitespace normalization', () => {
  const result = processOneSequence('acgtac gta  cgt');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
});

test('example: gap removal', () => {
  const result = processOneSequence('ACGT-ACGT..ACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTACGT');
});

test('example: anchor trimming', () => {
  const result = processOneSequence('THISISMYGBIFSEQUENCEACGTACGTACGTNNNNNENDOFSEQUENCE');
  // Should trim to first anchor run of 8+ ACGTU chars
  assert.ok(result.sequence.startsWith('ACGTACGT'));
  assert.strictEqual(result.ends_trimmed, true);
});

test('example: U to T conversion', () => {
  const result = processOneSequence('ACGTUACGTU');
  assert.strictEqual(result.sequence, 'ACGTTACGTT');
});

test('example: N-run capping', () => {
  const result = processOneSequence('ACGTACGTNNNNNNNNNNNNNNACGTACGTNNNNNNNNNACGTACGT');
  assert.strictEqual(result.sequence, 'ACGTACGTNNNNNACGTACGTNNNNNACGTACGT');
});

// =============================================================================
// Edge cases
// =============================================================================

console.log('\n--- Edge Cases ---');

test('handles null input', () => {
  const result = processOneSequence(null);
  assert.strictEqual(result.raw_sequence, '');
  assert.strictEqual(result.sequence, '');
});

test('handles undefined input', () => {
  const result = processOneSequence(undefined);
  assert.strictEqual(result.raw_sequence, '');
  assert.strictEqual(result.sequence, '');
});

test('preserves seq_id', () => {
  const result = processOneSequence('ACGTACGTACGT', DEFAULT_CONFIG, 'my-sequence-001');
  assert.strictEqual(result.seq_id, 'my-sequence-001');
});

test('custom config: different nrun_cap', () => {
  const customConfig = { ...DEFAULT_CONFIG, nrun_cap_from: 3, nrun_cap_to: 2 };
  const result = processOneSequence('ACGTACGTNNNACGTACGT', customConfig);
  assert.strictEqual(result.sequence, 'ACGTACGTNNACGTACGT');
});

test('custom config: different anchor_minrun', () => {
  const customConfig = { ...DEFAULT_CONFIG, anchor_minrun: 4 };
  const result = processOneSequence('XXXACGTXXX', customConfig);
  assert.strictEqual(result.sequence, 'ACGT');
});

// =============================================================================
// Summary
// =============================================================================

console.log('\n========================================');
console.log(`Tests: ${passed + failed} | Passed: ${passed} | Failed: ${failed}`);
console.log('========================================\n');

process.exit(failed > 0 ? 1 : 0);
