/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/**
 * {@code Date.parse} as V8 implements it: the ES5 date-time string format first, then V8's legacy parser for
 * everything else (month names, slashes, parenthesised comments, time zone names and offsets, two-digit years).
 */
final class DateParser {
  private static final int NONE = Integer.MAX_VALUE;
  private static final int MAX_SIGNIFICANT_DIGITS = 9;
  private static final int END_OF_INPUT = 0;

  // Keyword types.
  private static final int INVALID = 0;
  private static final int MONTH_NAME = 1;
  private static final int TIME_ZONE_NAME = 2;
  private static final int TIME_SEPARATOR = 3;
  private static final int AM_PM = 4;

  private static final String[] KEYWORDS = {
    "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec", "am\0", "pm\0", "ut\0",
    "utc", "z\0\0", "gmt", "cdt", "cst", "edt", "est", "mdt", "mst", "pdt", "pst", "t\0\0"
  };
  private static final int[] KEYWORD_TYPES = {
    MONTH_NAME, MONTH_NAME, MONTH_NAME, MONTH_NAME, MONTH_NAME, MONTH_NAME, MONTH_NAME, MONTH_NAME, MONTH_NAME,
    MONTH_NAME, MONTH_NAME, MONTH_NAME, AM_PM, AM_PM, TIME_ZONE_NAME, TIME_ZONE_NAME, TIME_ZONE_NAME, TIME_ZONE_NAME,
    TIME_ZONE_NAME, TIME_ZONE_NAME, TIME_ZONE_NAME, TIME_ZONE_NAME, TIME_ZONE_NAME, TIME_ZONE_NAME, TIME_ZONE_NAME,
    TIME_ZONE_NAME, TIME_SEPARATOR
  };
  private static final int[] KEYWORD_VALUES = {
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 0, 12, 0, 0, 0, 0, -5, -6, -4, -5, -6, -7, -7, -8, 0
  };

  private DateParser() {}

  /**
   * {@code Date.parse(text)} with UTC as the local time zone.
   *
   * @param text the date string
   * @return the time value, NaN when V8 rejects the string
   */
  static double parse(String text) {
    double[] out = new double[8];
    if (!new Run(text).parse(out)) {
      return Double.NaN;
    }
    double day = JsDate.makeDay(out[0], out[1], out[2]);
    double time = JsDate.makeTime(out[3], out[4], out[5], out[6]);
    double date = JsDate.makeDate(day, time);
    if (!Double.isNaN(out[7])) {
      date -= out[7] * 1000.0;
    }
    return JsDate.timeClip(date);
  }

  /** A token of the date string; a keyword token also has its keyword type. */
  private record Token(Kind kind, int value, int length, int keywordType) {
    Token(Kind kind, int value, int length) {
      this(kind, value, length, INVALID);
    }

    enum Kind {
      INVALID,
      UNKNOWN,
      NUMBER,
      SYMBOL,
      WHITE_SPACE,
      KEYWORD,
      END
    }

    boolean isNumber() {
      return kind == Kind.NUMBER;
    }

    boolean isSymbol(char c) {
      return kind == Kind.SYMBOL && value == c;
    }

    boolean isEnd() {
      return kind == Kind.END;
    }

    boolean isAsciiSign() {
      return kind == Kind.SYMBOL && (value == '-' || value == '+');
    }

    int asciiSign() {
      return 44 - value;
    }

    boolean isFixedLengthNumber(int n) {
      return kind == Kind.NUMBER && length == n;
    }
  }

  /** One parse: the reader, tokenizer and composers of V8's DateParser. */
  private static final class Run {
    private final String text;
    private int index;
    private int ch;
    private Token next;

    // DayComposer
    private final int[] dayComp = new int[3];
    private int dayIndex;
    private int namedMonth = NONE;
    private boolean isoDate;

    // TimeComposer
    private final int[] timeComp = new int[4];
    private int timeIndex;
    private int hourOffset = NONE;

    // TimeZoneComposer
    private int tzSign = NONE;
    private int tzHour = NONE;
    private int tzMinute = NONE;

    Run(String text) {
      this.text = text;
      nextChar();
      scanNext();
    }

    private void nextChar() {
      ch = index < text.length() ? text.charAt(index) : END_OF_INPUT;
      index++;
    }

    private int position() {
      return index;
    }

    private boolean isAsciiDigit() {
      return ch >= '0' && ch <= '9';
    }

    private boolean isWhiteSpaceChar() {
      return ch == '\t' || ch == '\u000B' || ch == '\f' || ch == ' ' || ch == ' ' || ch == '﻿'
          || Character.getType(ch) == Character.SPACE_SEPARATOR;
    }

    private boolean isWhiteSpaceOrLineTerminator() {
      return isWhiteSpaceChar() || ch == '\n' || ch == '\r' || ch == ' ' || ch == ' ';
    }

    private boolean skip(char c) {
      if (ch == c) {
        nextChar();
        return true;
      }
      return false;
    }

    private int readUnsignedNumeral() {
      int n = 0;
      int i = 0;
      while (ch == '0') {
        nextChar();
      }
      while (isAsciiDigit()) {
        if (i < MAX_SIGNIFICANT_DIGITS) {
          n = n * 10 + ch - '0';
        }
        i++;
        nextChar();
      }
      return n;
    }

    private Token scan() {
      int prePos = position();
      if (ch == END_OF_INPUT) {
        return new Token(Token.Kind.END, 0, 0);
      }
      if (isAsciiDigit()) {
        int n = readUnsignedNumeral();
        return new Token(Token.Kind.NUMBER, n, position() - prePos);
      }
      for (char symbol : new char[] {':', '-', '+', '.', ')'}) {
        if (skip(symbol)) {
          return new Token(Token.Kind.SYMBOL, symbol, 1);
        }
      }
      if (ch >= 'A' && !isWhiteSpaceChar()) {
        int[] prefix = new int[3];
        int length = 0;
        for (; ch >= 'A' && !isWhiteSpaceChar(); nextChar(), length++) {
          if (length < 3) {
            prefix[length] = ch | 0x20;
          }
        }
        int keyword = lookup(prefix, length);
        return keyword < 0
            ? new Token(Token.Kind.KEYWORD, 0, length, INVALID)
            : new Token(Token.Kind.KEYWORD, KEYWORD_VALUES[keyword], length, KEYWORD_TYPES[keyword]);
      }
      if (isWhiteSpaceOrLineTerminator()) {
        nextChar();
        return new Token(Token.Kind.WHITE_SPACE, 0, position() - prePos);
      }
      if (ch == '(') {
        int balance = 0;
        do {
          if (ch == ')') {
            --balance;
          } else if (ch == '(') {
            ++balance;
          }
          nextChar();
        } while (balance > 0 && ch != END_OF_INPUT);
        return new Token(Token.Kind.UNKNOWN, 0, 0);
      }
      nextChar();
      return new Token(Token.Kind.UNKNOWN, 0, 0);
    }

    private static int lookup(int[] prefix, int length) {
      for (int i = 0; i < KEYWORDS.length; i++) {
        String keyword = KEYWORDS[i];
        boolean match = true;
        for (int j = 0; j < 3; j++) {
          if (prefix[j] != keyword.charAt(j)) {
            match = false;
            break;
          }
        }
        if (match && (length <= 3 || KEYWORD_TYPES[i] == MONTH_NAME)) {
          return i;
        }
      }
      return -1;
    }

    // The tokenizer: `next` is the token after the one last returned.
    private void scanNext() {
      next = scan();
    }

    private Token nextToken() {
      Token result = next;
      scanNext();
      return result;
    }

    private Token peek() {
      return next;
    }

    private boolean skipSymbol(char symbol) {
      if (next.isSymbol(symbol)) {
        scanNext();
        return true;
      }
      return false;
    }

    private boolean peekIsKeywordType(int type) {
      return next.kind() == Token.Kind.KEYWORD && next.keywordType() == type;
    }

    private boolean peekIsKeywordZ() {
      return next.kind() == Token.Kind.KEYWORD && next.keywordType() == TIME_ZONE_NAME && next.length() == 1
          && next.value() == 0;
    }

    // DayComposer
    private boolean dayAdd(int n) {
      if (dayIndex < 3) {
        dayComp[dayIndex++] = n;
        return true;
      }
      return false;
    }

    // TimeComposer
    private boolean timeAdd(int n) {
      if (timeIndex < 4) {
        timeComp[timeIndex++] = n;
        return true;
      }
      return false;
    }

    private boolean timeAddFinal(int n) {
      if (!timeAdd(n)) {
        return false;
      }
      while (timeIndex < 4) {
        timeComp[timeIndex++] = 0;
      }
      return true;
    }

    private boolean timeIsExpecting(int n) {
      return (timeIndex == 1 && isMinute(n)) || (timeIndex == 2 && isSecond(n))
          || (timeIndex == 3 && isMillisecond(n));
    }

    // TimeZoneComposer
    private void tzSet(int offsetInHours) {
      tzSign = offsetInHours < 0 ? -1 : 1;
      tzHour = offsetInHours * tzSign;
      tzMinute = 0;
    }

    private void tzSetSign(int sign) {
      tzSign = sign < 0 ? -1 : 1;
    }

    private boolean tzIsExpecting(int n) {
      return tzHour != NONE && tzMinute == NONE && isMinute(n);
    }

    private boolean tzIsUtc() {
      return tzHour == 0 && tzMinute == 0;
    }

    private static boolean between(int x, int low, int high) {
      return x >= low && x <= high;
    }

    private static boolean isMonth(int x) {
      return between(x, 1, 12);
    }

    private static boolean isDay(int x) {
      return between(x, 1, 31);
    }

    private static boolean isHour(int x) {
      return between(x, 0, 23);
    }

    private static boolean isMinute(int x) {
      return between(x, 0, 59);
    }

    private static boolean isSecond(int x) {
      return between(x, 0, 59);
    }

    private static boolean isHour12(int x) {
      return between(x, 0, 12);
    }

    private static boolean isMillisecond(int x) {
      return between(x, 0, 999);
    }

    private static int readMilliseconds(Token token) {
      int number = token.value();
      int length = token.length();
      if (length < 3) {
        if (length == 1) {
          number *= 100;
        } else if (length == 2) {
          number *= 10;
        }
      } else if (length > 3) {
        if (length > MAX_SIGNIFICANT_DIGITS) {
          length = MAX_SIGNIFICANT_DIGITS;
        }
        int factor = 1;
        do {
          factor *= 10;
          length--;
        } while (length > 3);
        number /= factor;
      }
      return number;
    }

    // ParseES5DateTime: the token the legacy parser continues with; an INVALID token rejects the string.
    private Token parseEs5DateTime() {
      if (peek().isAsciiSign()) {
        Token signToken = nextToken();
        if (!peek().isFixedLengthNumber(6)) {
          return signToken;
        }
        int sign = signToken.asciiSign();
        int year = nextToken().value();
        if (sign < 0 && year == 0) {
          return signToken;
        }
        dayAdd(sign * year);
      } else if (peek().isFixedLengthNumber(4)) {
        dayAdd(nextToken().value());
      } else {
        return nextToken();
      }
      if (skipSymbol('-')) {
        if (!peek().isFixedLengthNumber(2) || !isMonth(peek().value())) {
          return nextToken();
        }
        dayAdd(nextToken().value());
        if (skipSymbol('-')) {
          if (!peek().isFixedLengthNumber(2) || !isDay(peek().value())) {
            return nextToken();
          }
          dayAdd(nextToken().value());
        }
      }
      if (!peekIsKeywordType(TIME_SEPARATOR)) {
        if (!peek().isEnd()) {
          return nextToken();
        }
      } else {
        nextToken();
        Token invalid = new Token(Token.Kind.INVALID, 0, 0);
        if (!peek().isFixedLengthNumber(2) || !between(peek().value(), 0, 24)) {
          return invalid;
        }
        boolean hourIs24 = peek().value() == 24;
        timeAdd(nextToken().value());
        if (!skipSymbol(':')) {
          return invalid;
        }
        if (!peek().isFixedLengthNumber(2) || !isMinute(peek().value()) || (hourIs24 && peek().value() > 0)) {
          return invalid;
        }
        timeAdd(nextToken().value());
        if (skipSymbol(':')) {
          if (!peek().isFixedLengthNumber(2) || !isSecond(peek().value()) || (hourIs24 && peek().value() > 0)) {
            return invalid;
          }
          timeAdd(nextToken().value());
          if (skipSymbol('.')) {
            if (!peek().isNumber() || (hourIs24 && peek().value() > 0)) {
              return invalid;
            }
            timeAdd(readMilliseconds(nextToken()));
          }
        }
        if (peekIsKeywordZ()) {
          nextToken();
          tzSet(0);
        } else if (peek().isSymbol('+') || peek().isSymbol('-')) {
          tzSetSign(nextToken().value() == '+' ? 1 : -1);
          if (peek().isFixedLengthNumber(4)) {
            int hourMinute = nextToken().value();
            int hour = hourMinute / 100;
            int minute = hourMinute % 100;
            if (!isHour(hour) || !isMinute(minute)) {
              return invalid;
            }
            tzHour = hour;
            tzMinute = minute;
          } else {
            if (!peek().isFixedLengthNumber(2) || !isHour(peek().value())) {
              return invalid;
            }
            tzHour = nextToken().value();
            if (!skipSymbol(':')) {
              return invalid;
            }
            if (!peek().isFixedLengthNumber(2) || !isMinute(peek().value())) {
              return invalid;
            }
            tzMinute = nextToken().value();
          }
        }
        if (!peek().isEnd()) {
          return invalid;
        }
      }
      if (tzHour == NONE && timeIndex == 0) {
        tzSet(0);
      }
      isoDate = true;
      return new Token(Token.Kind.END, 0, 0);
    }

    boolean parse(double[] out) {
      Token token = parseEs5DateTime();
      if (token.kind() == Token.Kind.INVALID) {
        return false;
      }
      boolean hasReadNumber = dayIndex > 0;
      for (; !token.isEnd(); token = nextToken()) {
        if (token.isNumber()) {
          hasReadNumber = true;
          int n = token.value();
          if (skipSymbol(':')) {
            if (skipSymbol(':')) {
              if (timeIndex != 0) {
                return false;
              }
              timeAdd(n);
              timeAdd(0);
            } else {
              if (!timeAdd(n)) {
                return false;
              }
              if (peek().isSymbol('.')) {
                nextToken();
              }
            }
          } else if (skipSymbol('.') && timeIsExpecting(n)) {
            timeAdd(n);
            if (!peek().isNumber()) {
              return false;
            }
            int ms = readMilliseconds(nextToken());
            if (ms < 0) {
              return false;
            }
            timeAddFinal(ms);
          } else if (tzIsExpecting(n)) {
            tzMinute = n;
          } else if (timeIsExpecting(n)) {
            timeAddFinal(n);
            Token peek = peek();
            if (!peek.isEnd() && peek.kind() != Token.Kind.WHITE_SPACE && !peekIsKeywordZ() && !peek.isAsciiSign()) {
              return false;
            }
          } else {
            if (!dayAdd(n)) {
              return false;
            }
            skipSymbol('-');
          }
        } else if (token.kind() == Token.Kind.KEYWORD) {
          if (token.keywordType() == AM_PM && timeIndex != 0) {
            hourOffset = token.value();
          } else if (token.keywordType() == MONTH_NAME) {
            namedMonth = token.value();
            skipSymbol('-');
          } else if (token.keywordType() == TIME_ZONE_NAME && hasReadNumber) {
            tzSet(token.value());
          } else {
            if (hasReadNumber) {
              return false;
            }
            if (peek().isNumber()) {
              return false;
            }
          }
        } else if (token.isAsciiSign() && (tzIsUtc() || timeIndex != 0)) {
          tzSetSign(token.asciiSign());
          int n = 0;
          int length = 0;
          if (peek().isNumber()) {
            Token number = nextToken();
            length = number.length();
            n = number.value();
          }
          hasReadNumber = true;
          if (peek().isSymbol(':')) {
            tzHour = n;
            tzMinute = NONE;
          } else if (length == 2 || length == 1) {
            tzHour = n;
            tzMinute = 0;
          } else if (length == 4 || length == 3) {
            tzHour = n / 100;
            tzMinute = n % 100;
          } else {
            return false;
          }
        } else if ((token.isAsciiSign() || token.isSymbol(')')) && hasReadNumber) {
          return false;
        }
      }
      return writeDay(out) && writeTime(out) && writeTimeZone(out);
    }

    private boolean writeDay(double[] out) {
      if (dayIndex < 1) {
        return false;
      }
      while (dayIndex < 3) {
        dayComp[dayIndex++] = 1;
      }
      int year = 0;
      int month;
      int day;
      if (namedMonth == NONE) {
        if (isoDate || (dayIndex == 3 && !isDay(dayComp[0]))) {
          year = dayComp[0];
          month = dayComp[1];
          day = dayComp[2];
        } else {
          month = dayComp[0];
          day = dayComp[1];
          if (dayIndex == 3) {
            year = dayComp[2];
          }
        }
      } else {
        month = namedMonth;
        if (dayIndex == 1) {
          day = dayComp[0];
        } else if (!isDay(dayComp[0])) {
          year = dayComp[0];
          day = dayComp[1];
        } else {
          day = dayComp[0];
          year = dayComp[1];
        }
      }
      if (!isoDate) {
        if (between(year, 0, 49)) {
          year += 2000;
        } else if (between(year, 50, 99)) {
          year += 1900;
        }
      }
      if (!isMonth(month) || !isDay(day)) {
        return false;
      }
      out[0] = year;
      out[1] = month - 1;
      out[2] = day;
      return true;
    }

    private boolean writeTime(double[] out) {
      while (timeIndex < 4) {
        timeComp[timeIndex++] = 0;
      }
      int hour = timeComp[0];
      int minute = timeComp[1];
      int second = timeComp[2];
      int millisecond = timeComp[3];
      if (hourOffset != NONE) {
        if (!isHour12(hour)) {
          return false;
        }
        hour %= 12;
        hour += hourOffset;
      }
      if (!isHour(hour) || !isMinute(minute) || !isSecond(second) || !isMillisecond(millisecond)) {
        if (hour != 24 || minute != 0 || second != 0 || millisecond != 0) {
          return false;
        }
      }
      out[3] = hour;
      out[4] = minute;
      out[5] = second;
      out[6] = millisecond;
      return true;
    }

    private boolean writeTimeZone(double[] out) {
      if (tzSign != NONE) {
        if (tzHour == NONE) {
          tzHour = 0;
        }
        if (tzMinute == NONE) {
          tzMinute = 0;
        }
        long total = (tzHour & 0xFFFFFFFFL) * 3600L + (tzMinute & 0xFFFFFFFFL) * 60L;
        total &= 0xFFFFFFFFL;
        if (total > 2147483647L) {
          return false;
        }
        out[7] = tzSign < 0 ? -total : total;
      } else {
        out[7] = Double.NaN;
      }
      return true;
    }
  }
}
