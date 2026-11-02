/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a regular expression pattern the way V8 does without the {@code u} and {@code v} flags (with the web
 * compatibility syntax of Annex B), reporting the same errors in the same order.
 */
final class RegExpParser {
  private static final int END = -1;
  private static final int MAX_CAPTURES = 1 << 16;
  private static final int[] DIGITS = {'0', '9'};
  private static final int[] WORD = {'0', '9', 'A', 'Z', '_', '_', 'a', 'z'};
  private static final int[] SPACE = {
    0x09, 0x0D, 0x20, 0x20, 0xA0, 0xA0, 0x1680, 0x1680, 0x2000, 0x200A, 0x2028, 0x2029, 0x202F, 0x202F, 0x205F,
    0x205F, 0x3000, 0x3000, 0xFEFF, 0xFEFF
  };
  private static final int[] LINE_TERMINATORS = {0x0A, 0x0A, 0x0D, 0x0D, 0x2028, 0x2029};

  private final String src;
  private final boolean dotAll;
  private int pos;
  private int capturesStarted;
  private int captureCount;
  private boolean scanned;
  private boolean hasNamedCaptures;
  private final Map<String, Integer> names = new HashMap<>();
  private final List<RegExpNode.BackReference> namedReferences = new ArrayList<>();
  private final Deque<Group> open = new ArrayDeque<>();

  /** An open group. */
  private record Group(Kind kind, int captureIndex, String name, boolean behind) {}

  private enum Kind {
    CAPTURE,
    GROUPING,
    POSITIVE_LOOKAROUND,
    NEGATIVE_LOOKAROUND
  }

  /** A parse error, the message V8 reports after the pattern and flags. */
  static final class Error extends Exception {
    private static final long serialVersionUID = 1L;

    Error(String message) {
      super(message, null, false, false);
    }
  }

  private RegExpParser(String src, boolean dotAll) {
    this.src = src;
    this.dotAll = dotAll;
  }

  /** The parsed pattern. */
  record Result(RegExpNode root, int captureCount) {}

  static Result parse(String src, boolean dotAll) throws Error {
    RegExpParser parser = new RegExpParser(src, dotAll);
    RegExpNode root = parser.parseDisjunction(null);
    for (RegExpNode.BackReference reference : parser.namedReferences) {
      Integer index = parser.names.get(reference.name());
      if (index == null) {
        throw new Error("Invalid named capture referenced");
      }
      reference.resolve(index);
    }
    return new Result(root, parser.capturesStarted);
  }

  private int current() {
    return pos < src.length() ? src.charAt(pos) : END;
  }

  private int next() {
    return pos + 1 < src.length() ? src.charAt(pos + 1) : END;
  }

  private void advance() {
    if (pos < src.length()) {
      pos++;
    }
  }

  private void advance(int n) {
    for (int i = 0; i < n; i++) {
      advance();
    }
  }

  private static boolean isDecimalDigit(int c) {
    return c >= '0' && c <= '9';
  }

  // ParseDisjunction: the alternatives up to the end of the pattern or the ')' that closes `group`.
  private RegExpNode parseDisjunction(Group group) throws Error {
    List<RegExpNode> alternatives = new ArrayList<>();
    List<RegExpNode> terms = new ArrayList<>();
    while (true) {
      int c = current();
      int capturesBefore = capturesStarted;
      RegExpNode atom;
      switch (c) {
        case END -> {
          if (group != null) {
            throw new Error("Unterminated group");
          }
          alternatives.add(sequence(terms));
          return alternation(alternatives);
        }
        case ')' -> {
          if (group == null) {
            throw new Error("Unmatched ')'");
          }
          advance();
          alternatives.add(sequence(terms));
          return alternation(alternatives);
        }
        case '|' -> {
          advance();
          alternatives.add(sequence(terms));
          terms = new ArrayList<>();
          continue;
        }
        case '*', '+', '?' -> throw new Error("Nothing to repeat");
        case '^' -> {
          advance();
          terms.add(new RegExpNode.Assertion(RegExpNode.Assertion.Kind.START));
          continue;
        }
        case '$' -> {
          advance();
          terms.add(new RegExpNode.Assertion(RegExpNode.Assertion.Kind.END));
          continue;
        }
        case '.' -> {
          advance();
          atom = dotAll ? new RegExpNode.CharClass(new int[0], true) : new RegExpNode.CharClass(LINE_TERMINATORS, true);
        }
        case '(' -> atom = parseGroup();
        case '[' -> atom = parseClass();
        case '\\' -> {
          int n = next();
          switch (n) {
            case END -> throw new Error("\\ at end of pattern");
            case 'b', 'B' -> {
              advance(2);
              terms.add(new RegExpNode.Assertion(
                  n == 'b' ? RegExpNode.Assertion.Kind.BOUNDARY : RegExpNode.Assertion.Kind.NON_BOUNDARY));
              continue;
            }
            case 'd', 'D', 's', 'S', 'w', 'W' -> {
              advance(2);
              RangeSet set = new RangeSet();
              addClassEscape(set, n);
              atom = new RegExpNode.CharClass(set.toArray(), false);
            }
            case 'k' -> {
              if (hasNamedCaptures()) {
                advance(2);
                atom = parseNamedBackReference();
              } else {
                atom = new RegExpNode.Char(parseCharacterEscape(false));
              }
            }
            default -> {
              if (n >= '1' && n <= '9') {
                int index = parseBackReferenceIndex();
                if (index > 0) {
                  atom = insideCapture(index) ? null : new RegExpNode.BackReference(index, null);
                } else if (n == '8' || n == '9') {
                  atom = new RegExpNode.Char((char) n);
                  advance(2);
                } else {
                  advance();
                  atom = new RegExpNode.Char((char) parseOctalLiteral());
                }
              } else if (n == '0') {
                advance();
                atom = new RegExpNode.Char((char) parseOctalLiteral());
              } else {
                atom = new RegExpNode.Char(parseCharacterEscape(false));
              }
            }
          }
        }
        case '{' -> {
          int save = pos;
          if (parseInterval() != null) {
            throw new Error("Nothing to repeat");
          }
          pos = save;
          atom = new RegExpNode.Char('{');
          advance();
        }
        default -> {
          atom = new RegExpNode.Char((char) c);
          advance();
        }
      }
      // An atom of null is an empty match (a back reference inside its own group).
      boolean emptyAtom = atom == null;
      int min;
      int max;
      int q = current();
      if (q == '*') {
        min = 0;
        max = RegExpNode.INFINITY;
        advance();
      } else if (q == '+') {
        min = 1;
        max = RegExpNode.INFINITY;
        advance();
      } else if (q == '?') {
        min = 0;
        max = 1;
        advance();
      } else if (q == '{') {
        int save = pos;
        int[] interval = parseInterval();
        if (interval == null) {
          pos = save;
          addAtom(terms, atom);
          continue;
        }
        min = interval[0];
        max = interval[1];
        if (max < min) {
          throw new Error("numbers out of order in {} quantifier");
        }
      } else {
        addAtom(terms, atom);
        continue;
      }
      boolean greedy = true;
      if (current() == '?') {
        greedy = false;
        advance();
      }
      if (emptyAtom) {
        continue;
      }
      if (atom instanceof RegExpNode.Lookaround look && look.behind()) {
        throw new Error("Invalid quantifier");
      }
      if (zeroWidth(atom)) {
        if (min > 0) {
          terms.add(atom);
        }
        continue;
      }
      int first = capturesBefore + 1;
      int count = capturesStarted - capturesBefore;
      terms.add(new RegExpNode.Quantifier(atom, min, max, greedy, first, count));
    }
  }

  private static void addAtom(List<RegExpNode> terms, RegExpNode atom) {
    if (atom != null) {
      terms.add(atom);
    }
  }

  private static RegExpNode sequence(List<RegExpNode> terms) {
    if (terms.isEmpty()) {
      return new RegExpNode.Empty();
    }
    return terms.size() == 1 ? terms.get(0) : new RegExpNode.Sequence(List.copyOf(terms));
  }

  private static RegExpNode alternation(List<RegExpNode> alternatives) {
    return alternatives.size() == 1 ? alternatives.get(0) : new RegExpNode.Alternation(List.copyOf(alternatives));
  }

  // Whether a node can only ever match the empty string (V8's max_match() == 0).
  private static boolean zeroWidth(RegExpNode node) {
    return switch (node) {
      case RegExpNode.Empty e -> true;
      case RegExpNode.Assertion a -> true;
      case RegExpNode.Lookaround l -> true;
      case RegExpNode.Sequence s -> s.terms().stream().allMatch(RegExpParser::zeroWidth);
      case RegExpNode.Alternation a -> a.alternatives().stream().allMatch(RegExpParser::zeroWidth);
      case RegExpNode.Capture c -> zeroWidth(c.body());
      case RegExpNode.Quantifier q -> q.max() == 0 || zeroWidth(q.body());
      default -> false;
    };
  }

  // ParseIntervalQuantifier at a '{': {min, max}, or null (position unspecified) when it is not a quantifier.
  private int[] parseInterval() {
    advance();
    if (!isDecimalDigit(current())) {
      return null;
    }
    int min = 0;
    while (isDecimalDigit(current())) {
      int digit = current() - '0';
      if (min > (RegExpNode.INFINITY - digit) / 10) {
        while (isDecimalDigit(current())) {
          advance();
        }
        min = RegExpNode.INFINITY;
        break;
      }
      min = 10 * min + digit;
      advance();
    }
    int max;
    if (current() == '}') {
      max = min;
      advance();
    } else if (current() == ',') {
      advance();
      if (current() == '}') {
        max = RegExpNode.INFINITY;
        advance();
      } else {
        max = 0;
        while (isDecimalDigit(current())) {
          int digit = current() - '0';
          if (max > (RegExpNode.INFINITY - digit) / 10) {
            while (isDecimalDigit(current())) {
              advance();
            }
            max = RegExpNode.INFINITY;
            break;
          }
          max = 10 * max + digit;
          advance();
        }
        if (current() != '}') {
          return null;
        }
        advance();
      }
    } else {
      return null;
    }
    return new int[] {min, max};
  }

  private RegExpNode parseGroup() throws Error {
    advance();
    Kind kind = Kind.CAPTURE;
    boolean behind = false;
    boolean named = false;
    if (current() == '?') {
      switch (next()) {
        case ':' -> {
          advance(2);
          kind = Kind.GROUPING;
        }
        case '=' -> {
          advance(2);
          kind = Kind.POSITIVE_LOOKAROUND;
        }
        case '!' -> {
          advance(2);
          kind = Kind.NEGATIVE_LOOKAROUND;
        }
        case '<' -> {
          advance();
          if (next() == '=') {
            advance(2);
            kind = Kind.POSITIVE_LOOKAROUND;
            behind = true;
          } else if (next() == '!') {
            advance(2);
            kind = Kind.NEGATIVE_LOOKAROUND;
            behind = true;
          } else {
            named = true;
            hasNamedCaptures = true;
            advance();
          }
        }
        default -> throw new Error("Invalid group");
      }
    }
    int captureIndex = capturesStarted;
    String name = null;
    if (kind == Kind.CAPTURE) {
      if (capturesStarted >= MAX_CAPTURES) {
        throw new Error("Too many captures");
      }
      capturesStarted++;
      captureIndex = capturesStarted;
      if (named) {
        name = parseCaptureGroupName();
      }
    }
    Group group = new Group(kind, captureIndex, name, behind);
    open.push(group);
    RegExpNode body = parseDisjunction(group);
    open.pop();
    return switch (kind) {
      case CAPTURE -> {
        if (name != null) {
          if (names.containsKey(name)) {
            throw new Error("Duplicate capture group name");
          }
          names.put(name, captureIndex);
        }
        yield new RegExpNode.Capture(captureIndex, body);
      }
      case GROUPING -> body;
      default -> new RegExpNode.Lookaround(behind, kind == Kind.NEGATIVE_LOOKAROUND, body, captureIndex + 1,
          capturesStarted - captureIndex);
    };
  }

  private boolean insideCapture(int index) {
    for (Group group : open) {
      if (group.kind() == Kind.CAPTURE && group.captureIndex() == index) {
        return true;
      }
    }
    return false;
  }

  private boolean insideCapture(String name) {
    for (Group group : open) {
      if (group.kind() == Kind.CAPTURE && name.equals(group.name())) {
        return true;
      }
    }
    return false;
  }

  // The current code point, reading a surrogate pair as one (group names are read that way).
  private int currentCodePoint() {
    if (pos >= src.length()) {
      return END;
    }
    return src.codePointAt(pos);
  }

  private void advanceCodePoint() {
    if (pos < src.length()) {
      pos += Character.charCount(src.codePointAt(pos));
    }
  }

  private String parseCaptureGroupName() throws Error {
    StringBuilder name = new StringBuilder();
    boolean atStart = true;
    while (true) {
      int c = currentCodePoint();
      advanceCodePoint();
      if (c == '\\' && current() == 'u') {
        advance();
        c = parseNameUnicodeEscape();
        if (c < 0) {
          throw new Error("Invalid Unicode escape");
        }
      }
      if (c == '\\') {
        throw new Error("Invalid capture group name");
      }
      if (atStart) {
        if (!isIdentifierStart(c)) {
          throw new Error("Invalid capture group name");
        }
        name.appendCodePoint(c);
      } else if (c == '>') {
        break;
      } else if (isIdentifierPart(c)) {
        name.appendCodePoint(c);
      } else {
        throw new Error("Invalid capture group name");
      }
      atStart = false;
    }
    return name.toString();
  }

  // A \\u escape in a group name (after the 'u'): \\uXXXX, a surrogate pair of two of them, or \\u{...}; -1 if invalid.
  private int parseNameUnicodeEscape() {
    if (current() == '{') {
      int save = pos;
      advance();
      long value = 0;
      int digits = 0;
      while (hexValue(current()) >= 0) {
        value = value * 16 + hexValue(current());
        digits++;
        advance();
        if (value > 0x10FFFF) {
          pos = save;
          return -1;
        }
      }
      if (digits == 0 || current() != '}') {
        pos = save;
        return -1;
      }
      advance();
      return (int) value;
    }
    int lead = parseHex(4);
    if (lead < 0) {
      return -1;
    }
    if (Character.isHighSurrogate((char) lead) && current() == '\\' && next() == 'u') {
      int save = pos;
      advance(2);
      int trail = parseHex(4);
      if (trail >= 0 && Character.isLowSurrogate((char) trail)) {
        return Character.toCodePoint((char) lead, (char) trail);
      }
      pos = save;
    }
    return lead;
  }

  private static boolean isIdentifierStart(int c) {
    return c == '$' || c == '_' || (c >= 0 && Character.isUnicodeIdentifierStart(c));
  }

  private static boolean isIdentifierPart(int c) {
    if (c == '$' || c == '_' || c == 0x200C || c == 0x200D) {
      return true;
    }
    return c >= 0 && Character.isUnicodeIdentifierPart(c) && !Character.isIdentifierIgnorable(c);
  }

  private RegExpNode parseNamedBackReference() throws Error {
    if (current() != '<') {
      throw new Error("Invalid named reference");
    }
    advance();
    String name = parseCaptureGroupName();
    if (insideCapture(name)) {
      return null;
    }
    RegExpNode.BackReference reference = new RegExpNode.BackReference(0, name);
    namedReferences.add(reference);
    return reference;
  }

  // ParseBackReferenceIndex at '\\' followed by 1-9: the index, or 0 (position restored) when it is not a back reference.
  private int parseBackReferenceIndex() {
    int start = pos;
    int value = next() - '0';
    advance(2);
    while (isDecimalDigit(current())) {
      value = 10 * value + (current() - '0');
      if (value > MAX_CAPTURES) {
        pos = start;
        return 0;
      }
      advance();
    }
    if (value > capturesStarted) {
      scanForCaptures();
      if (value > captureCount) {
        pos = start;
        return 0;
      }
    }
    return value;
  }

  private boolean hasNamedCaptures() {
    if (hasNamedCaptures || scanned) {
      return hasNamedCaptures;
    }
    scanForCaptures();
    return hasNamedCaptures;
  }

  // ScanForCaptures: counts the capturing groups from here to the end (and notes named groups).
  private void scanForCaptures() {
    if (scanned) {
      return;
    }
    int saved = pos;
    int count = capturesStarted;
    int c;
    while ((c = current()) != END) {
      advance();
      switch (c) {
        case '\\' -> advance();
        case '[' -> {
          int d;
          while ((d = current()) != END) {
            advance();
            if (d == '\\') {
              advance();
            } else if (d == ']') {
              break;
            }
          }
        }
        case '(' -> {
          if (current() == '?') {
            advance();
            if (current() != '<') {
              break;
            }
            advance();
            if (current() == '=' || current() == '!') {
              break;
            }
            hasNamedCaptures = true;
          }
          count++;
        }
        default -> {}
      }
    }
    captureCount = count;
    scanned = true;
    pos = saved;
  }

  private int parseOctalLiteral() {
    int value = current() - '0';
    advance();
    if (current() >= '0' && current() <= '7') {
      value = value * 8 + current() - '0';
      advance();
      if (value < 32 && current() >= '0' && current() <= '7') {
        value = value * 8 + current() - '0';
        advance();
      }
    }
    return value;
  }

  private static int hexValue(int c) {
    if (c >= '0' && c <= '9') {
      return c - '0';
    }
    if (c >= 'a' && c <= 'f') {
      return c - 'a' + 10;
    }
    if (c >= 'A' && c <= 'F') {
      return c - 'A' + 10;
    }
    return -1;
  }

  // Exactly `length` hex digits, or -1 with the position restored.
  private int parseHex(int length) {
    int start = pos;
    int value = 0;
    for (int i = 0; i < length; i++) {
      int digit = hexValue(current());
      if (digit < 0) {
        pos = start;
        return -1;
      }
      value = value * 16 + digit;
      advance();
    }
    return value;
  }

  // ParseCharacterEscape at a '\\' (not a class escape, back reference or named reference).
  private char parseCharacterEscape(boolean inClass) throws Error {
    advance();
    int c = current();
    switch (c) {
      case 'f' -> {
        advance();
        return '\f';
      }
      case 'n' -> {
        advance();
        return '\n';
      }
      case 'r' -> {
        advance();
        return '\r';
      }
      case 't' -> {
        advance();
        return '\t';
      }
      case 'v' -> {
        advance();
        return '\u000B';
      }
      case 'c' -> {
        int control = next();
        int letter = control & ~('A' ^ 'a');
        if (letter >= 'A' && letter <= 'Z') {
          advance(2);
          return (char) (control & 0x1F);
        }
        if (inClass && ((control >= '0' && control <= '9') || control == '_')) {
          advance(2);
          return (char) (control & 0x1F);
        }
        return '\\';
      }
      case '0' -> {
        if (!isDecimalDigit(next())) {
          advance();
          return 0;
        }
        return (char) parseOctalLiteral();
      }
      case '1', '2', '3', '4', '5', '6', '7' -> {
        return (char) parseOctalLiteral();
      }
      case 'x' -> {
        advance();
        int value = parseHex(2);
        return value >= 0 ? (char) value : 'x';
      }
      case 'u' -> {
        advance();
        int value = parseHex(4);
        return value >= 0 ? (char) value : 'u';
      }
      case 'k' -> {
        if (inClass && hasNamedCaptures()) {
          throw new Error("Invalid escape");
        }
        advance();
        return 'k';
      }
      default -> {
        advance();
        return (char) c;
      }
    }
  }

  private RegExpNode parseClass() throws Error {
    advance();
    boolean negated = false;
    if (current() == '^') {
      negated = true;
      advance();
    }
    RangeSet set = new RangeSet();
    while (current() != END && current() != ']') {
      int first = parseClassAtom(set);
      if (current() == '-') {
        advance();
        if (current() == END) {
          break;
        }
        if (current() == ']') {
          if (first >= 0) {
            set.add(first, first);
          }
          set.add('-', '-');
          break;
        }
        int second = parseClassAtom(set);
        if (first < 0 || second < 0) {
          if (first >= 0) {
            set.add(first, first);
          }
          set.add('-', '-');
          if (second >= 0) {
            set.add(second, second);
          }
          continue;
        }
        if (first > second) {
          throw new Error("Range out of order in character class");
        }
        set.add(first, second);
      } else if (first >= 0) {
        set.add(first, first);
      }
    }
    if (current() == END) {
      throw new Error("Unterminated character class");
    }
    advance();
    return new RegExpNode.CharClass(set.toArray(), negated);
  }

  // One class atom: its code unit, or -1 for a class escape (whose ranges are added to the set).
  private int parseClassAtom(RangeSet set) throws Error {
    if (current() != '\\') {
      int c = current();
      advance();
      return c;
    }
    int n = next();
    switch (n) {
      case 'b' -> {
        advance(2);
        return '\b';
      }
      case END -> throw new Error("\\ at end of pattern");
      case 'd', 'D', 's', 'S', 'w', 'W' -> {
        advance(2);
        addClassEscape(set, n);
        return -1;
      }
      default -> {
        return parseCharacterEscape(true);
      }
    }
  }

  private static void addClassEscape(RangeSet set, int escape) {
    int[] ranges = switch (escape) {
      case 'd', 'D' -> DIGITS;
      case 's', 'S' -> SPACE;
      default -> WORD;
    };
    if (Character.isUpperCase(escape)) {
      ranges = RangeSet.complement(ranges);
    }
    for (int i = 0; i < ranges.length; i += 2) {
      set.add(ranges[i], ranges[i + 1]);
    }
  }

  /** A set of code units as sorted, merged inclusive ranges. */
  static final class RangeSet {
    private final List<int[]> ranges = new ArrayList<>();

    void add(int low, int high) {
      ranges.add(new int[] {low, high});
    }

    int[] toArray() {
      ranges.sort((a, b) -> Integer.compare(a[0], b[0]));
      List<int[]> merged = new ArrayList<>();
      for (int[] range : ranges) {
        if (!merged.isEmpty() && range[0] <= merged.get(merged.size() - 1)[1] + 1) {
          int[] last = merged.get(merged.size() - 1);
          last[1] = Math.max(last[1], range[1]);
        } else {
          merged.add(new int[] {range[0], range[1]});
        }
      }
      int[] out = new int[merged.size() * 2];
      for (int i = 0; i < merged.size(); i++) {
        out[2 * i] = merged.get(i)[0];
        out[2 * i + 1] = merged.get(i)[1];
      }
      return out;
    }

    static int[] complement(int[] ranges) {
      List<Integer> out = new ArrayList<>();
      int next = 0;
      for (int i = 0; i < ranges.length; i += 2) {
        if (ranges[i] > next) {
          out.add(next);
          out.add(ranges[i] - 1);
        }
        next = ranges[i + 1] + 1;
      }
      if (next <= 0xFFFF) {
        out.add(next);
        out.add(0xFFFF);
      }
      return out.stream().mapToInt(Integer::intValue).toArray();
    }
  }
}
