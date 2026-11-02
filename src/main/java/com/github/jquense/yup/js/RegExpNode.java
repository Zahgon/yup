/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.util.List;

/** A node of a parsed JavaScript regular expression (without the {@code u} and {@code v} flags). */
sealed interface RegExpNode {
  /** The repetition count that stands for infinity. */
  int INFINITY = Integer.MAX_VALUE;

  /** Matches the empty string. */
  record Empty() implements RegExpNode {}

  /** The first alternative that lets the rest of the pattern match. */
  record Alternation(List<RegExpNode> alternatives) implements RegExpNode {}

  /** Terms matched one after another. */
  record Sequence(List<RegExpNode> terms) implements RegExpNode {}

  /** One code unit. */
  record Char(char value) implements RegExpNode {}

  /** A character class: sorted, merged inclusive ranges of code units, possibly negated. */
  record CharClass(int[] ranges, boolean negated) implements RegExpNode {}

  /** {@code ^}, {@code $}, {@code \b} or {@code \B}. */
  record Assertion(Kind kind) implements RegExpNode {
    /** The kind of assertion. */
    enum Kind {
      START,
      END,
      BOUNDARY,
      NON_BOUNDARY
    }
  }

  /** A lookahead or lookbehind, with the captures it contains. */
  record Lookaround(boolean behind, boolean negative, RegExpNode body, int firstCapture, int captureCount)
      implements RegExpNode {}

  /** A capturing group. */
  record Capture(int index, RegExpNode body) implements RegExpNode {}

  /** A quantified atom, with the captures it contains. */
  record Quantifier(RegExpNode body, int min, int max, boolean greedy, int firstCapture, int captureCount)
      implements RegExpNode {}

  /** A back reference; a named one gets its index once the whole pattern is parsed. */
  final class BackReference implements RegExpNode {
    private int index;
    private final String name;

    BackReference(int index, String name) {
      this.index = index;
      this.name = name;
    }

    int index() {
      return index;
    }

    String name() {
      return name;
    }

    void resolve(int resolved) {
      index = resolved;
    }
  }
}
