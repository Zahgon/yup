/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.util.Arrays;
import java.util.List;

/**
 * A backtracking matcher with the semantics of the ECMAScript pattern algorithm on UTF-16 code units: continuations,
 * captures reset on each repetition, empty repetitions rejected once the minimum is met, lookbehinds matched
 * right to left.
 */
final class RegExpMatcher {
  private static final char[][] EQUIVALENTS = new char[0x10000][];

  static {
    char[][] byCanonical = new char[0x10000][];
    int[] counts = new int[0x10000];
    for (int c = 0; c < 0x10000; c++) {
      counts[UnicodeCase.canonicalize((char) c)]++;
    }
    for (int c = 0; c < 0x10000; c++) {
      char canonical = UnicodeCase.canonicalize((char) c);
      if (byCanonical[canonical] == null) {
        byCanonical[canonical] = new char[counts[canonical]];
        counts[canonical] = 0;
      }
      byCanonical[canonical][counts[canonical]++] = (char) c;
    }
    for (int c = 0; c < 0x10000; c++) {
      EQUIVALENTS[c] = byCanonical[UnicodeCase.canonicalize((char) c)];
    }
  }

  @FunctionalInterface
  private interface Continuation {
    boolean run(int position);
  }

  private final String input;
  private final int length;
  private final boolean ignoreCase;
  private final boolean multiline;
  private final int[] captures;

  RegExpMatcher(String input, boolean ignoreCase, boolean multiline, int captureCount) {
    this.input = input;
    this.length = input.length();
    this.ignoreCase = ignoreCase;
    this.multiline = multiline;
    this.captures = new int[2 * (captureCount + 1)];
  }

  /**
   * Tries to match at one start position.
   *
   * @return the captures (pairs of start and end, -1 for undefined; pair 0 the whole match), or null
   */
  int[] matchAt(RegExpNode root, int start) {
    Arrays.fill(captures, -1);
    int[] end = {-1};
    boolean found = match(root, start, true, position -> {
      end[0] = position;
      return true;
    });
    if (!found) {
      return null;
    }
    int[] result = captures.clone();
    result[0] = start;
    result[1] = end[0];
    return result;
  }

  private boolean match(RegExpNode node, int pos, boolean forward, Continuation next) {
    return switch (node) {
      case RegExpNode.Empty empty -> next.run(pos);
      case RegExpNode.Char c -> {
        if (forward) {
          yield pos < length && same(input.charAt(pos), c.value()) && next.run(pos + 1);
        }
        yield pos > 0 && same(input.charAt(pos - 1), c.value()) && next.run(pos - 1);
      }
      case RegExpNode.CharClass c -> {
        if (forward) {
          yield pos < length && inClass(c, input.charAt(pos)) && next.run(pos + 1);
        }
        yield pos > 0 && inClass(c, input.charAt(pos - 1)) && next.run(pos - 1);
      }
      case RegExpNode.Sequence s -> sequence(s.terms(), forward ? 0 : s.terms().size() - 1, pos, forward, next);
      case RegExpNode.Alternation a -> {
        for (RegExpNode alternative : a.alternatives()) {
          if (match(alternative, pos, forward, next)) {
            yield true;
          }
        }
        yield false;
      }
      case RegExpNode.Assertion a -> assertion(a.kind(), pos) && next.run(pos);
      case RegExpNode.Capture c -> capture(c, pos, forward, next);
      case RegExpNode.BackReference b -> backReference(b.index(), pos, forward, next);
      case RegExpNode.Lookaround l -> lookaround(l, pos, next);
      case RegExpNode.Quantifier q -> repeat(q, q.min(), q.max(), pos, forward, next);
    };
  }

  private boolean sequence(List<RegExpNode> terms, int index, int pos, boolean forward, Continuation next) {
    if (index < 0 || index >= terms.size()) {
      return next.run(pos);
    }
    int following = forward ? index + 1 : index - 1;
    return match(terms.get(index), pos, forward, p -> sequence(terms, following, p, forward, next));
  }

  private boolean capture(RegExpNode.Capture capture, int pos, boolean forward, Continuation next) {
    int slot = 2 * capture.index();
    return match(capture.body(), pos, forward, end -> {
      int savedStart = captures[slot];
      int savedEnd = captures[slot + 1];
      captures[slot] = forward ? pos : end;
      captures[slot + 1] = forward ? end : pos;
      if (next.run(end)) {
        return true;
      }
      captures[slot] = savedStart;
      captures[slot + 1] = savedEnd;
      return false;
    });
  }

  private boolean backReference(int index, int pos, boolean forward, Continuation next) {
    int start = captures[2 * index];
    int end = captures[2 * index + 1];
    if (start < 0 || end < 0) {
      return next.run(pos);
    }
    int n = end - start;
    int from = forward ? pos : pos - n;
    if (from < 0 || from + n > length) {
      return false;
    }
    for (int i = 0; i < n; i++) {
      if (!same(input.charAt(start + i), input.charAt(from + i))) {
        return false;
      }
    }
    return next.run(forward ? pos + n : pos - n);
  }

  private boolean lookaround(RegExpNode.Lookaround look, int pos, Continuation next) {
    int[] saved = save(look.firstCapture(), look.captureCount());
    boolean found = match(look.body(), pos, !look.behind(), p -> true);
    if (look.negative()) {
      restore(look.firstCapture(), saved);
      return !found && next.run(pos);
    }
    if (!found) {
      return false;
    }
    if (next.run(pos)) {
      return true;
    }
    restore(look.firstCapture(), saved);
    return false;
  }

  private boolean repeat(RegExpNode.Quantifier q, int min, int max, int pos, boolean forward, Continuation next) {
    if (max == 0) {
      return next.run(pos);
    }
    Continuation again = y -> {
      if (min == 0 && y == pos) {
        return false;
      }
      int min2 = min == 0 ? 0 : min - 1;
      int max2 = max == RegExpNode.INFINITY ? RegExpNode.INFINITY : max - 1;
      return repeat(q, min2, max2, y, forward, next);
    };
    int[] saved = save(q.firstCapture(), q.captureCount());
    if (min != 0) {
      clear(q.firstCapture(), q.captureCount());
      if (match(q.body(), pos, forward, again)) {
        return true;
      }
      restore(q.firstCapture(), saved);
      return false;
    }
    if (!q.greedy()) {
      if (next.run(pos)) {
        return true;
      }
      clear(q.firstCapture(), q.captureCount());
      if (match(q.body(), pos, forward, again)) {
        return true;
      }
      restore(q.firstCapture(), saved);
      return false;
    }
    clear(q.firstCapture(), q.captureCount());
    if (match(q.body(), pos, forward, again)) {
      return true;
    }
    restore(q.firstCapture(), saved);
    return next.run(pos);
  }

  private int[] save(int first, int count) {
    return Arrays.copyOfRange(captures, 2 * first, 2 * (first + count));
  }

  private void restore(int first, int[] saved) {
    System.arraycopy(saved, 0, captures, 2 * first, saved.length);
  }

  private void clear(int first, int count) {
    Arrays.fill(captures, 2 * first, 2 * (first + count), -1);
  }

  private boolean same(char a, char b) {
    return a == b || (ignoreCase && UnicodeCase.canonicalize(a) == UnicodeCase.canonicalize(b));
  }

  private boolean inClass(RegExpNode.CharClass c, char ch) {
    boolean found;
    if (ignoreCase) {
      found = false;
      for (char equivalent : EQUIVALENTS[ch]) {
        if (contains(c.ranges(), equivalent)) {
          found = true;
          break;
        }
      }
    } else {
      found = contains(c.ranges(), ch);
    }
    return found != c.negated();
  }

  private static boolean contains(int[] ranges, char c) {
    int low = 0;
    int high = ranges.length / 2 - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (c < ranges[2 * mid]) {
        high = mid - 1;
      } else if (c > ranges[2 * mid + 1]) {
        low = mid + 1;
      } else {
        return true;
      }
    }
    return false;
  }

  private boolean assertion(RegExpNode.Assertion.Kind kind, int pos) {
    return switch (kind) {
      case START -> pos == 0 || (multiline && isLineTerminator(input.charAt(pos - 1)));
      case END -> pos == length || (multiline && isLineTerminator(input.charAt(pos)));
      case BOUNDARY -> isWord(pos - 1) != isWord(pos);
      case NON_BOUNDARY -> isWord(pos - 1) == isWord(pos);
    };
  }

  private boolean isWord(int index) {
    if (index < 0 || index >= length) {
      return false;
    }
    char c = input.charAt(index);
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
  }

  private static boolean isLineTerminator(char c) {
    return c == '\n' || c == '\r' || c == ' ' || c == ' ';
  }
}
