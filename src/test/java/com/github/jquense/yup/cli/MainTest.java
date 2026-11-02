/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import static com.github.jquense.yup.Support.check;
import static com.github.jquense.yup.Support.eq;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** The yup command's JSON-lines protocol. */
public class MainTest {
  private static String[] run(String input, int expectedStatus) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    int status = Main.run(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)), out);
    eq(expectedStatus, status);
    String text = out.toString(StandardCharsets.UTF_8);
    check(text.isEmpty() || text.endsWith("\n"), "every response line to end with a newline");
    return text.isEmpty() ? new String[0] : text.split("\n", -1);
  }

  /** One response line per request, in order; empty lines are skipped. */
  public void testResponses() throws IOException {
    String[] lines = run("{\"op\":\"isValid\",\"schema\":[\"string\",[\"min\",2]],\"value\":\"ab\"}\n\n"
        + "{\"op\":\"cast\",\"schema\":[\"date\"],\"value\":\"Jan 5\"}\n"
        + "{\"op\":\"cast\",\"schema\":[\"number\"],\"value\":\" 1 2 \"}\n", 0);
    eq("{\"result\":true}", lines[0]);
    eq("{\"value\":{\"$date\":\"2001-01-05T00:00:00.000Z\"}}", lines[1]);
    eq("{\"value\":12}", lines[2]);
  }

  /** Validation errors are answers; other failures are error responses that set exit status 1. */
  public void testErrors() throws IOException {
    String[] lines = run("{\"op\":\"validate\",\"schema\":[\"number\",[\"min\",3]],\"value\":\"1\"}\nnope\n"
        + "{\"op\":\"nope\"}\n{\"op\":\"cast\",\"schema\":[\"string\",[\"email\",1]],\"value\":null}\n", 1);
    check(lines[0].startsWith("{\"invalid\":{\"name\":\"ValidationError\",\"message\":"
        + "\"this must be greater than or equal to 3\""), "a validation error answer");
    eq("{\"error\":{\"name\":\"SyntaxError\",\"message\":\"Unexpected token 'o', \\\"nope\\\" is not valid JSON\"}}",
        lines[1]);
    eq("{\"error\":{\"name\":\"RequestError\",\"message\":\"unknown op: \\\"nope\\\"\"}}", lines[2]);
    check(lines[3].startsWith("{\"error\":{\"name\":\"TypeError\""), "a cast error");
  }

  /** Values round-trip through the tagged encoding. */
  public void testEncoding() throws IOException {
    String[] lines = run("{\"op\":\"cast\",\"schema\":[\"mixed\"],\"value\":[{\"$undefined\":1},{\"$number\":\"-0\"},"
        + "{\"$hole\":1},{\"$object\":{\"$k\":1}},{\"$regex\":\"a/b\",\"flags\":\"yg\"},{\"$bigint\":\"-5\"}]}\n", 0);
    eq("{\"value\":[{\"$undefined\":true},{\"$number\":\"-0\"},{\"$hole\":true},{\"$object\":{\"$k\":1}},"
        + "{\"$regex\":\"a\\\\/b\",\"flags\":\"gy\"},{\"$bigint\":\"-5\"}]}", lines[0]);
  }

  /** Input is decoded as Node decodes it: each maximal invalid UTF-8 subsequence becomes one U+FFFD. */
  public void testUtf8Decoding() {
    eq("a�b", Main.decodeUtf8(new byte[] {'a', (byte) 0xFF, 'b'}));
    eq("a�b", Main.decodeUtf8(new byte[] {'a', (byte) 0xE2, (byte) 0x82, 'b'}));
    eq("a���b", Main.decodeUtf8(new byte[] {'a', (byte) 0xED, (byte) 0xA0, (byte) 0x80, 'b'}));
    eq("a��b", Main.decodeUtf8(new byte[] {'a', (byte) 0xC0, (byte) 0xAF, 'b'}));
    eq("😀�", Main.decodeUtf8(new byte[] {(byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80,
        (byte) 0xF0, (byte) 0x9F}));
    eq("﻿é", Main.decodeUtf8("﻿é".getBytes(StandardCharsets.UTF_8)));
  }

  /** setLocale and addMethod last for one request only. */
  public void testRequestScopedState() throws IOException {
    String[] lines = run("{\"op\":\"validate\",\"schema\":[\"string\",[\"email\"]],\"value\":\"x\","
        + "\"locale\":{\"string\":{\"email\":\"bad\"}}}\n"
        + "{\"op\":\"validate\",\"schema\":[\"string\",[\"email\"]],\"value\":\"x\"}\n"
        + "{\"op\":\"describe\",\"schema\":[\"string\",[\"tag\"]],\"methods\":[{\"type\":\"string\",\"name\":\"tag\","
        + "\"fn\":{\"$fn\":\"method\",\"calls\":[[\"label\",\"T\"]]}}]}\n"
        + "{\"op\":\"describe\",\"schema\":[\"string\",[\"tag\"]]}\n", 1);
    check(lines[0].contains("\"errors\":[\"bad\"]"), "the custom message");
    check(lines[1].contains("\"errors\":[\"this must be a valid email\"]"), "the default message again");
    check(lines[2].contains("\"label\":\"T\""), "the installed method");
    eq("{\"error\":{\"name\":\"RequestError\",\"message\":\"string schema has no method \\\"tag\\\"\"}}", lines[3]);
  }
}
