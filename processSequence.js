const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

/**
 * Parse a simple YAML file (supports basic key: value pairs)
 * @param {string} yamlContent - YAML file content
 * @returns {object} - Parsed configuration object
 */
function parseSimpleYaml(yamlContent) {
  const config = {};
  const lines = yamlContent.split('\n');

  for (const line of lines) {
    // Skip comments and empty lines
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;

    // Parse key: value
    const colonIndex = line.indexOf(':');
    if (colonIndex === -1) continue;

    const key = line.slice(0, colonIndex).trim();
    let value = line.slice(colonIndex + 1).trim();

    // Remove inline comments
    const commentIndex = value.indexOf('#');
    if (commentIndex !== -1) {
      value = value.slice(0, commentIndex).trim();
    }

    // Remove quotes if present
    if ((value.startsWith('"') && value.endsWith('"')) ||
        (value.startsWith("'") && value.endsWith("'"))) {
      value = value.slice(1, -1);
    }

    // Convert to appropriate type
    if (value === 'true') {
      config[key] = true;
    } else if (value === 'false') {
      config[key] = false;
    } else if (value === 'null') {
      config[key] = null;
    } else if (!isNaN(value) && value !== '') {
      config[key] = Number(value);
    } else {
      config[key] = value;
    }
  }

  return config;
}

/**
 * Load configuration from YAML file
 * @param {string} [configPath] - Path to config file (defaults to config.yaml in same directory)
 * @returns {object} - Configuration object
 */
function loadConfig(configPath) {
  const filePath = configPath || path.join(__dirname, 'config.yaml');
  const content = fs.readFileSync(filePath, 'utf8');
  return parseSimpleYaml(content);
}

// Load default config from YAML file
let DEFAULT_CONFIG;
try {
  DEFAULT_CONFIG = loadConfig();
} catch (e) {
  // Fallback if config.yaml doesn't exist
  DEFAULT_CONFIG = {
    anchor_chars: "ACGTU",
    anchor_minrun: 8,
    anchor_strict: "ACGTU",
    gap_regex: "[-\\.]",
    natural_language_regex: "UNMERGED",
    iupac_rna: "ACGTURYSWKMBDHVN",
    iupac_dna: "ACGTRYSWKMBDHVN",
    nrun_cap_from: 6,
    nrun_cap_to: 5
  };
}

/**
 * Trims the sequence to the first anchor run of at least minRun consecutive anchor characters.
 * If no valid anchor is found, returns empty string (wipes the sequence).
 * @param {string} seq - The sequence to trim
 * @param {string} anchorChars - Characters considered valid anchors
 * @param {number} minRun - Minimum consecutive anchor chars required
 * @returns {{s: string, did: boolean}} - Trimmed sequence and whether trimming occurred
 */
function trimToFirstAnchorOrWipe(seq, anchorChars, minRun) {
  if (!seq) return { s: "", did: false };

  const anchorPattern = new RegExp(`[${anchorChars}]{${minRun},}`);
  const match = seq.match(anchorPattern);

  if (!match) {
    // No valid anchor found - wipe the sequence
    return { s: "", did: true };
  }

  const startIndex = match.index;
  const trimmed = seq.slice(startIndex);

  return {
    s: trimmed,
    did: startIndex > 0
  };
}

/**
 * Trims the sequence to the last anchor run of at least minRun consecutive anchor characters.
 * @param {string} seq - The sequence to trim
 * @param {string} anchorChars - Characters considered valid anchors
 * @param {number} minRun - Minimum consecutive anchor chars required
 * @returns {{s: string, did: boolean}} - Trimmed sequence and whether trimming occurred
 */
function trimToLastAnchor(seq, anchorChars, minRun) {
  if (!seq) return { s: "", did: false };

  const anchorPattern = new RegExp(`[${anchorChars}]{${minRun},}`, 'g');
  let lastMatch = null;
  let match;

  while ((match = anchorPattern.exec(seq)) !== null) {
    lastMatch = match;
  }

  if (!lastMatch) {
    return { s: seq, did: false };
  }

  const endIndex = lastMatch.index + lastMatch[0].length;
  const trimmed = seq.slice(0, endIndex);

  return {
    s: trimmed,
    did: endIndex < seq.length
  };
}

/**
 * Computes MD5 hash of a string
 * @param {string} str - Input string
 * @returns {string} - MD5 hash in hexadecimal
 */
function md5(str) {
  if (!str) return null;
  return crypto.createHash('md5').update(str).digest('hex');
}

/**
 * Counts regex matches in a string
 * @param {string} str - Input string
 * @param {string} pattern - Regex pattern
 * @returns {number} - Number of matches
 */
function countRegex(str, pattern) {
  if (!str) return 0;
  const regex = new RegExp(pattern, 'g');
  const matches = str.match(regex);
  return matches ? matches.length : 0;
}

/**
 * Process a single DNA/RNA sequence through the cleaning pipeline
 * @param {string} seq - Raw sequence input
 * @param {object} config - Configuration object (uses DEFAULT_CONFIG if not provided)
 * @param {string} [seqId] - Optional sequence identifier
 * @returns {object} - Cleaned sequence with metrics
 */
function processOneSequence(seq, config = DEFAULT_CONFIG, seqId = null) {
  const raw = seq || "";

  // Stage A: normalize whitespace + uppercase
  // example: "acgtac gta  cgt" -> "ACGTACGTACGT"
  const rawHasWs = /\s/.test(raw);
  const s0 = raw.replace(/\s+/g, "");
  const s1 = s0.toUpperCase();

  // Stage B: detect natural language
  // example: "ACGTUNMERGEDACGT" -> naturalLanguageDetected = true
  const naturalLanguageRegex = new RegExp(config.natural_language_regex);
  const naturalLanguageDetected = naturalLanguageRegex.test(s1);

  // Stage C: remove gaps
  // example: "ACGT-ACGT..ACGT" -> "ACGTACGTACGT"
  const gapRegex = new RegExp(config.gap_regex, 'g');
  const hasGaps = new RegExp(config.gap_regex).test(s1);
  const s2 = s1.replace(gapRegex, "");
  const gapsOrWhitespaceRemoved = rawHasWs || hasGaps;

  // Stage D: U -> T (RNA->DNA)
  // example: "ACGTUACGTU" -> "ACGTTACGTT"
  const s3 = s2.replace(/U/g, "T");

  // Stage E: trim to anchors (front & back)
  // example: with anchor_chars="ACGTU" and anchor_minrun=8:
  // "THISISMYGBIFSEQUENCEACGTACGTACGTNNNNNENDOFSEQUENCE" -> "ACGTACGTACGT"
  const tFirst = trimToFirstAnchorOrWipe(s3, config.anchor_chars, config.anchor_minrun);
  const s4 = tFirst.s;
  const tLast = trimToLastAnchor(s4, config.anchor_chars, config.anchor_minrun);
  const s5 = tLast.s;
  const endsTrimmed = tFirst.did || tLast.did;

  // Stage F: cap N-runs (apply N-run cap to s5 -> s6)
  // example: with nrun_cap_from=6 and nrun_cap_to=5:
  // "ACGTACGTNNNNNNNNNNNNNNACGTACGTNNNNNNNNNACGTACGT" -> "ACGTACGTNNNNNACGTACGTNNNNNACGTACGT"
  const capPattern = `N{${config.nrun_cap_from},}`;
  const nNrunsCapped = countRegex(s5, capPattern);
  const capToStr = "N".repeat(config.nrun_cap_to);
  const s6 = s5.replace(new RegExp(capPattern, 'g'), capToStr);

  // Additional metrics (on s6)
  const sequenceLength = s6.length;
  const nCount = (s6.match(/N/g) || []).length;
  const nFraction = sequenceLength > 0 ? nCount / sequenceLength : null;

  // Compute ambiguous/non-IUPAC counts AFTER capping (on s6)
  const nonAcgtnCount = countRegex(s6, "[^ACGTN]");
  const nonIupacCount = countRegex(s6, `[^${config.iupac_dna}]`);
  const nonACGTNFraction = sequenceLength > 0 ? nonAcgtnCount / sequenceLength : null;
  const nonIupacFraction = sequenceLength > 0 ? nonIupacCount / sequenceLength : null;

  // GC content (A/C/G/T only in denominator)
  const gc = countRegex(s6, "[GC]");
  const acgt = countRegex(s6, "[ACGT]");
  const gcContent = acgt > 0 ? gc / acgt : null;

  // MD5 of the final cleaned sequence
  const nucleotideSequenceID = md5(s6);

  // Output object
  const invalid = (nonIupacFraction !== null && nonIupacFraction > 0) || naturalLanguageDetected;
  return {
    seq_id: seqId,
    raw_sequence: raw,
    sequence: invalid ? null : s6,
    sequence_length: sequenceLength,
    non_iupac_fraction: nonIupacFraction,
    non_acgtn_fraction: nonACGTNFraction,
    n_fraction: nFraction,
    n_nruns_capped: nNrunsCapped,
    gc_content: gcContent,
    natural_language_detected: naturalLanguageDetected,
    ends_trimmed: endsTrimmed,
    gaps_or_whitespace_removed: gapsOrWhitespaceRemoved,
    nucleotide_sequence_id: invalid ? null : nucleotideSequenceID,
    invalid
  };
}

module.exports = {
  processOneSequence,
  trimToFirstAnchorOrWipe,
  trimToLastAnchor,
  loadConfig,
  DEFAULT_CONFIG
};
