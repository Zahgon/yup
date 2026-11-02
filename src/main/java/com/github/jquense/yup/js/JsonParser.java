/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

/**
 * {@code JSON.parse} with V8's {@code SyntaxError} messages: objects become {@link JsObject}s, arrays {@link JsArray}s,
 * numbers {@link Double}s.
 */
public final class JsonParser {
  private static final int MAX_CONTEXT = 10;
  private static final int MIN_LENGTH_FOR_CONTEXT = MAX_CONTEXT * 2 + 1;

  private enum Token {
    STRING,
    NUMBER,
    LBRACE,
    RBRACE,
    LBRACK,
    RBRACK,
    TRUE,
    FALSE,
    NULL,
    COLON,
    COMMA,
    WHITESPACE,
    ILLEGAL,
    EOS
  }

  private final String source;
  private int cursor;

  private JsonParser(String source) {
    this.source = source;
  }

  /**
   * Parses JSON text.
   *
   * @param text the text
   * @return the value
   * @throws JsError a {@code SyntaxError} with V8's message
   */
  public static Object parse(String text) {
    JsonParser parser = new JsonParser(text);
    Object value = parser.parseValue();
    parser.skipWhitespace();
    if (parser.peek() != Token.EOS) {
      throw parser.error(parser.peek(), "Unexpected non-whitespace character after JSON");
    }
    return value;
  }

  private static Token tokenOf(int c) {
    if (c < 0) {
      return Token.EOS;
    }
    return switch (c) {
      case '"' -> Token.STRING;
      case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> Token.NUMBER;
      case '{' -> Token.LBRACE;
      case '}' -> Token.RBRACE;
      case '[' -> Token.LBRACK;
      case ']' -> Token.RBRACK;
      case 't' -> Token.TRUE;
      case 'f' -> Token.FALSE;
      case 'n' -> Token.NULL;
      case ':' -> Token.COLON;
      case ',' -> Token.COMMA;
      case ' ', '\t', '\r', '\n' -> Token.WHITESPACE;
      default -> Token.ILLEGAL;
    };
  }

  private int current() {
    return cursor < source.length() ? source.charAt(cursor) : -1;
  }

  private Token peek() {
    return tokenOf(current());
  }

  private void skipWhitespace() {
    while (peek() == Token.WHITESPACE) {
      cursor++;
    }
  }

  private void expect(Token token, String message) {
    skipWhitespace();
    if (peek() != token) {
      throw error(peek(), message);
    }
    cursor++;
  }

  private boolean check(Token token) {
    skipWhitespace();
    if (peek() == token) {
      cursor++;
      return true;
    }
    return false;
  }

  private Object parseValue() {
    skipWhitespace();
    switch (peek()) {
      case STRING -> {
        cursor++;
        return scanString();
      }
      case NUMBER -> {
        return parseNumber();
      }
      case LBRACE -> {
        cursor++;
        JsObject object = new JsObject();
        if (check(Token.RBRACE)) {
          return object;
        }
        expect(Token.STRING, "Expected property name or '}'");
        while (true) {
          String key = scanString();
          expect(Token.COLON, "Expected ':' after property name");
          object.put(key, parseValue());
          if (check(Token.COMMA)) {
            expect(Token.STRING, "Expected double-quoted property name");
            continue;
          }
          expect(Token.RBRACE, "Expected ',' or '}' after property value");
          return object;
        }
      }
      case LBRACK -> {
        cursor++;
        JsArray array = new JsArray();
        if (check(Token.RBRACK)) {
          return array;
        }
        while (true) {
          array.push(parseValue());
          if (check(Token.COMMA)) {
            continue;
          }
          expect(Token.RBRACK, "Expected ',' or ']' after array element");
          return array;
        }
      }
      case TRUE -> {
        scanLiteral("true");
        return Boolean.TRUE;
      }
      case FALSE -> {
        scanLiteral("false");
        return Boolean.FALSE;
      }
      case NULL -> {
        scanLiteral("null");
        return null;
      }
      default -> throw unexpectedCharacter();
    }
  }

  private void scanLiteral(String literal) {
    int remaining = source.length() - cursor;
    if (remaining >= literal.length() && source.startsWith(literal, cursor)) {
      cursor += literal.length();
      return;
    }
    cursor++;
    for (int i = 1; i < Math.min(literal.length(), remaining); i++) {
      if (literal.charAt(i) != source.charAt(cursor)) {
        throw unexpectedCharacter();
      }
      cursor++;
    }
    throw error(Token.EOS, null);
  }

  private JsError unexpectedCharacter() {
    int c = current();
    Token token = c < 0 ? Token.EOS : c <= 0xFF ? tokenOf(c) : Token.ILLEGAL;
    return error(token, null);
  }

  private Object parseNumber() {
    int start = cursor;
    boolean negative = false;
    if (current() == '-') {
      negative = true;
      cursor++;
    }
    if (current() == '0') {
      cursor++;
      int c = current();
      if (c >= '0' && c <= '9') {
        throw error(Token.NUMBER, null);
      }
      if (!isNumberPart(c) && !negative) {
        return 0.0;
      }
    } else {
      int digitsStart = cursor;
      while (current() >= '0' && current() <= '9') {
        cursor++;
      }
      if (digitsStart == cursor) {
        throw error(Token.ILLEGAL, "No number after minus sign");
      }
    }
    if (current() == '.') {
      cursor++;
      if (!(current() >= '0' && current() <= '9')) {
        throw error(Token.ILLEGAL, "Unterminated fractional number");
      }
      while (current() >= '0' && current() <= '9') {
        cursor++;
      }
    }
    if (current() == 'e' || current() == 'E') {
      cursor++;
      if (current() == '-' || current() == '+') {
        cursor++;
      }
      if (!(current() >= '0' && current() <= '9')) {
        throw error(Token.ILLEGAL, "Exponent part is missing a number");
      }
      while (current() >= '0' && current() <= '9') {
        cursor++;
      }
    }
    return Double.parseDouble(source.substring(start, cursor));
  }

  private static boolean isNumberPart(int c) {
    return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
  }

  // Scans a string after its opening quote.
  private String scanString() {
    StringBuilder out = new StringBuilder();
    while (true) {
      if (cursor >= source.length()) {
        throw error(Token.ILLEGAL, "Unterminated string");
      }
      char c = source.charAt(cursor);
      if (c == '"') {
        cursor++;
        return out.toString();
      }
      if (c < 0x20) {
        throw error(Token.ILLEGAL, "Bad control character in string literal");
      }
      if (c != '\\') {
        out.append(c);
        cursor++;
        continue;
      }
      cursor++;
      int e = current();
      switch (e) {
        case '"' -> out.append('"');
        case '\\' -> out.append('\\');
        case '/' -> out.append('/');
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'u' -> {
          int value = 0;
          for (int i = 0; i < 4; i++) {
            cursor++;
            int digit = Character.digit(current() < 0 ? -1 : current(), 16);
            if (current() < 0 || current() > 'f' || digit < 0) {
              throw error(Token.ILLEGAL, "Bad Unicode escape");
            }
            value = value * 16 + digit;
          }
          out.append((char) value);
        }
        default -> throw error(Token.ILLEGAL, "Bad escaped character");
      }
      cursor++;
    }
  }

  // ReportUnexpectedToken: an explicit message, or the one V8 looks up for the token.
  private JsError error(Token token, String message) {
    int pos = cursor;
    String where = message != null && message.endsWith("after JSON") ? " at position " : " in JSON at position ";
    String located = where + pos + " (line " + line() + " column " + column() + ")";
    if (message != null) {
      return JsError.syntaxError(message + located);
    }
    switch (token) {
      case EOS -> {
        return JsError.syntaxError("Unexpected end of JSON input");
      }
      case NUMBER -> {
        return JsError.syntaxError("Unexpected number" + located);
      }
      case STRING -> {
        return JsError.syntaxError("Unexpected string" + located);
      }
      default -> {
        if (isSpecialString()) {
          return JsError.syntaxError("\"" + source + "\" is not valid JSON");
        }
        String character = String.valueOf(source.charAt(pos));
        int length = source.length();
        if (length < MIN_LENGTH_FOR_CONTEXT) {
          return JsError.syntaxError("Unexpected token '" + character + "', \"" + source + "\" is not valid JSON");
        }
        if (pos < MAX_CONTEXT) {
          return JsError.syntaxError("Unexpected token '" + character + "', \"" + source.substring(0, pos + MAX_CONTEXT)
              + "\"... is not valid JSON");
        }
        if (pos < length - MAX_CONTEXT) {
          return JsError.syntaxError("Unexpected token '" + character + "', ...\""
              + source.substring(pos - MAX_CONTEXT, pos + MAX_CONTEXT) + "\"... is not valid JSON");
        }
        return JsError.syntaxError("Unexpected token '" + character + "', ...\"" + source.substring(pos - MAX_CONTEXT)
            + "\" is not valid JSON");
      }
    }
  }

  private boolean isSpecialString() {
    return source.equals("[object Object]") || source.equals("undefined") || source.equals("Infinity")
        || source.equals("NaN");
  }

  // The line and column of the cursor, counting \r\n, \r and \n as line breaks.
  private int line() {
    return location()[0];
  }

  private int column() {
    return location()[1];
  }

  private int[] location() {
    int line = 1;
    int lastLineBreak = 0;
    int i = 0;
    for (; i < cursor; i++) {
      char c = source.charAt(i);
      if (c == '\r' && i < cursor - 1 && source.charAt(i + 1) == '\n') {
        i++;
        c = '\n';
      }
      if (c == '\r' || c == '\n') {
        line++;
        lastLineBreak = i + 1;
      }
    }
    return new int[] {line, 1 + i - lastLineBreak};
  }
}
