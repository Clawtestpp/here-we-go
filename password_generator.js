#!/usr/bin/env node
/**
 * Random password and passphrase generator.
 *
 * Usage:
 *   node password_generator.js [--length N] [--count N] [--no-symbols]
 *   node password_generator.js --passphrase [--words N] [--count N]
 */

const crypto = require("crypto");

const LOWER = "abcdefghijklmnopqrstuvwxyz";
const UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
const DIGITS = "0123456789";
const SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?";

const WORDS = [
  "apple", "breeze", "canyon", "dolphin", "ember", "falcon", "glacier",
  "harbor", "island", "jungle", "kettle", "lantern", "meadow", "nebula",
  "orchid", "pepper", "quartz", "river", "saddle", "thunder", "umbrella",
  "violet", "willow", "xylophone", "yonder", "zephyr",
];

function randomChar(chars) {
  return chars[crypto.randomInt(chars.length)];
}

function shuffle(items) {
  for (let i = items.length - 1; i > 0; i--) {
    const j = crypto.randomInt(i + 1);
    [items[i], items[j]] = [items[j], items[i]];
  }
  return items;
}

function generatePassword(length, useSymbols) {
  const sets = [LOWER, UPPER, DIGITS];
  if (useSymbols) sets.push(SYMBOLS);
  if (length < sets.length) {
    throw new Error(`Length must be at least ${sets.length}`);
  }

  // Guarantee at least one character from every set, then fill the rest.
  const all = sets.join("");
  const chars = sets.map(randomChar);
  while (chars.length < length) chars.push(randomChar(all));
  return shuffle(chars).join("");
}

function generatePassphrase(wordCount) {
  const words = Array.from({ length: wordCount }, () => randomChar(WORDS));
  return `${words.join("-")}-${crypto.randomInt(100)}`;
}

function parseArgs(argv) {
  const opts = { length: 16, count: 1, symbols: true, passphrase: false, words: 4 };
  for (let i = 0; i < argv.length; i++) {
    switch (argv[i]) {
      case "--length": opts.length = Number(argv[++i]); break;
      case "--count": opts.count = Number(argv[++i]); break;
      case "--words": opts.words = Number(argv[++i]); break;
      case "--no-symbols": opts.symbols = false; break;
      case "--passphrase": opts.passphrase = true; break;
      default: throw new Error(`Unknown option: ${argv[i]}`);
    }
  }
  return opts;
}

function main() {
  const opts = parseArgs(process.argv.slice(2));
  for (let i = 0; i < opts.count; i++) {
    console.log(opts.passphrase
      ? generatePassphrase(opts.words)
      : generatePassword(opts.length, opts.symbols));
  }
}

try {
  main();
} catch (err) {
  console.error(err.message);
  process.exit(1);
}
