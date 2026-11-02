/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A JavaScript {@code RegExp} without the {@code u} and {@code v} flags: V8's pattern syntax and errors, the ECMAScript
 * matching semantics on UTF-16 code units, and the {@code lastIndex} behaviour of the {@code g} and {@code y} flags.
 */
public final class JsRegExp {
  private static final String FLAG_ORDER = "dgimsuvy";

  private final String pattern;
  private final String flags;
  private final boolean global;
  private final boolean ignoreCase;
  private final boolean multiline;
  private final boolean sticky;
  private final RegExpNode root;
  private final int captureCount;
  private int lastIndex;

  /**
   * {@code new RegExp(pattern, flags)}.
   *
   * @param pattern the pattern source
   * @param flags the flags
   * @throws JsError a {@code SyntaxError} for invalid flags or an invalid pattern
   */
  public JsRegExp(String pattern, String flags) {
    boolean[] seen = new boolean[FLAG_ORDER.length()];
    for (int i = 0; i < flags.length(); i++) {
      int index = FLAG_ORDER.indexOf(flags.charAt(i));
      if (index < 0 || seen[index]) {
        throw JsError.syntaxError("Invalid flags supplied to RegExp constructor '" + flags + "'");
      }
      seen[index] = true;
    }
    if (seen[FLAG_ORDER.indexOf('u')] || seen[FLAG_ORDER.indexOf('v')]) {
      throw JsError.syntaxError("the u and v flags are not supported");
    }
    StringBuilder canonical = new StringBuilder();
    for (int i = 0; i < FLAG_ORDER.length(); i++) {
      if (seen[i]) {
        canonical.append(FLAG_ORDER.charAt(i));
      }
    }
    this.pattern = pattern;
    this.flags = canonical.toString();
    this.global = this.flags.indexOf('g') >= 0;
    this.ignoreCase = this.flags.indexOf('i') >= 0;
    this.multiline = this.flags.indexOf('m') >= 0;
    this.sticky = this.flags.indexOf('y') >= 0;
    try {
      RegExpParser.Result result = RegExpParser.parse(pattern, this.flags.indexOf('s') >= 0);
      this.root = result.root();
      this.captureCount = result.captureCount();
    } catch (RegExpParser.Error e) {
      throw JsError.syntaxError(
          "Invalid regular expression: /" + pattern + "/" + this.flags + ": " + e.getMessage());
    }
  }

  /**
   * {@code new RegExp(pattern)} without flags.
   *
   * @param pattern the pattern source
   */
  public JsRegExp(String pattern) {
    this(pattern, "");
  }

  /**
   * {@code RegExpCreate(pattern, undefined)}: the pattern is {@code ""} for {@code undefined} and its string form
   * otherwise.
   *
   * @param pattern any value
   * @return the regular expression
   */
  public static JsRegExp create(Object pattern) {
    return new JsRegExp(pattern == Js.UNDEFINED ? "" : Js.toJsString(pattern), "");
  }

  /** A successful match: the start index and the captures (null for one that did not participate). */
  public record Match(int index, List<String> captures) {
    /**
     * @param group a capture index
     * @return the captured text, or null
     */
    public String group(int group) {
      return captures.get(group);
    }
  }

  /** @return the flags in their canonical order */
  public String flags() {
    return flags;
  }

  /** @return whether the {@code g} flag is set */
  public boolean isGlobal() {
    return global;
  }

  /** @return the {@code source} property: the pattern with unescaped slashes and line terminators escaped */
  public String source() {
    if (pattern.isEmpty()) {
      return "(?:)";
    }
    StringBuilder out = new StringBuilder();
    boolean inClass = false;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '\\') {
        if (i + 1 < pattern.length() && isLineTerminator(pattern.charAt(i + 1))) {
          continue;
        }
        out.append(c);
        i++;
        if (i == pattern.length()) {
          break;
        }
        out.append(pattern.charAt(i));
        continue;
      }
      switch (c) {
        case '/' -> out.append(inClass ? "/" : "\\/");
        case '[' -> {
          inClass = true;
          out.append(c);
        }
        case ']' -> {
          inClass = false;
          out.append(c);
        }
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case ' ' -> out.append("\\u2028");
        case ' ' -> out.append("\\u2029");
        default -> out.append(c);
      }
    }
    return out.toString();
  }

  private static boolean isLineTerminator(char c) {
    return c == '\n' || c == '\r' || c == ' ' || c == ' ';
  }

  /** @return {@code RegExp.prototype.toString}: {@code /source/flags} */
  @Override
  public String toString() {
    return "/" + source() + "/" + flags;
  }

  /** @return {@code new RegExp(this)}: the same pattern and flags with {@code lastIndex} 0 */
  public JsRegExp copy() {
    return new JsRegExp(pattern, flags);
  }

  /**
   * {@code RegExpBuiltinExec}: honours and updates {@code lastIndex} for the {@code g} and {@code y} flags.
   *
   * @param s the input
   * @return the match, or null
   */
  public Match exec(String s) {
    int start = global || sticky ? lastIndex : 0;
    if (start > s.length()) {
      if (global || sticky) {
        lastIndex = 0;
      }
      return null;
    }
    RegExpMatcher matcher = new RegExpMatcher(s, ignoreCase, multiline, captureCount);
    for (int i = start; i <= s.length(); i++) {
      int[] captures = matcher.matchAt(root, i);
      if (captures != null) {
        if (global || sticky) {
          lastIndex = captures[1];
        }
        List<String> groups = new ArrayList<>(captureCount + 1);
        for (int g = 0; g <= captureCount; g++) {
          int from = captures[2 * g];
          int to = captures[2 * g + 1];
          groups.add(from < 0 || to < 0 ? null : s.substring(from, to));
        }
        return new Match(captures[0], groups);
      }
      if (sticky) {
        break;
      }
    }
    if (global || sticky) {
      lastIndex = 0;
    }
    return null;
  }

  /**
   * {@code RegExp.prototype.test}.
   *
   * @param value any value, converted with {@code ToString}
   * @return whether it matches
   */
  public boolean test(Object value) {
    return exec(Js.toJsString(value)) != null;
  }

  /**
   * {@code String.prototype.search}: the first match ignoring {@code lastIndex} (which it restores).
   *
   * @param s the input
   * @return the index of the first match, or -1
   */
  public int search(String s) {
    int previous = lastIndex;
    lastIndex = 0;
    Match match = exec(s);
    lastIndex = previous;
    return match == null ? -1 : match.index();
  }

  /**
   * {@code String.prototype.match} with the {@code g} flag: every match, an empty one advancing by one code unit.
   *
   * @param s the input
   * @return the matched strings, or null when there is none
   */
  public List<String> matchAll(String s) {
    lastIndex = 0;
    List<String> found = new ArrayList<>();
    while (true) {
      Match match = exec(s);
      if (match == null) {
        return found.isEmpty() ? null : found;
      }
      String text = match.group(0);
      found.add(text);
      if (text.isEmpty()) {
        lastIndex = lastIndex + 1;
      }
    }
  }

  /**
   * {@code String.prototype.replace} with a replacer function: every match with the {@code g} flag, the first
   * otherwise.
   *
   * @param s the input
   * @param replacer the replacement of each match
   * @return the new string
   */
  public String replace(String s, Function<Match, String> replacer) {
    List<Match> matches = new ArrayList<>();
    if (global) {
      lastIndex = 0;
      while (true) {
        Match match = exec(s);
        if (match == null) {
          break;
        }
        matches.add(match);
        if (match.group(0).isEmpty()) {
          lastIndex = lastIndex + 1;
        }
      }
    } else {
      Match match = exec(s);
      if (match != null) {
        matches.add(match);
      }
    }
    StringBuilder out = new StringBuilder();
    int position = 0;
    for (Match match : matches) {
      if (match.index() < position) {
        continue;
      }
      out.append(s, position, match.index());
      out.append(replacer.apply(match));
      position = match.index() + match.group(0).length();
    }
    out.append(s.substring(position));
    return out.toString();
  }
}
