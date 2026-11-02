/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.cli;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsError;
import com.github.jquense.yup.js.JsException;
import com.github.jquense.yup.js.JsObject;
import com.github.jquense.yup.js.JsPromise;
import com.github.jquense.yup.js.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The {@code yup} command: reads JSON-lines requests on standard input and writes one JSON response line per request.
 * Exit status 0 when every request succeeded, 1 when one failed, 2 (with the usage text) for any argument.
 */
public final class Main {
  private static final String USAGE = "usage: yup < requests.jsonl\n"
      + "\n"
      + "Reads one JSON request per line on standard input and writes one JSON response per line.\n"
      + "Requests: {\"op\":\"cast\"|\"validate\"|\"validateSync\"|\"isValid\"|\"isValidSync\",\"schema\":S,\"value\":V,"
      + "\"options\":O},\n"
      + "          {\"op\":\"validateAt\"|\"validateSyncAt\",\"schema\":S,\"path\":P,\"value\":V,\"options\":O},\n"
      + "          {\"op\":\"isType\",\"schema\":S,\"value\":V}, {\"op\":\"describe\"|\"resolve\"|\"getDefault\","
      + "\"schema\":S,\"options\":O},\n"
      + "          {\"op\":\"default\"|\"meta\",\"schema\":S}, {\"op\":\"reach\"|\"getIn\",\"schema\":S,\"path\":P,"
      + "\"value\":V,\"context\":C},\n"
      + "          {\"op\":\"printValue\",\"value\":V,\"quoteStrings\":Q}, {\"op\":\"isSchema\"|\"isError\",\"value\":V},\n"
      + "          {\"op\":\"validationError\",\"errors\":X,\"value\":V,\"path\":P,\"type\":T,\"disableStack\":D},\n"
      + "          {\"op\":\"formatError\",\"message\":M,\"params\":A}, {\"op\":\"standardValidate\",\"schema\":S,"
      + "\"value\":V},\n"
      + "          {\"op\":\"locale\"}, {\"op\":\"ref\",\"key\":K,\"map\":F,\"value\":V,\"parent\":A,\"context\":C}\n"
      + "with optional \"functions\", \"defs\", \"locale\" and \"methods\".\n";

  // Deep schemas and backtracking regular expressions recurse deeply.
  private static final long STACK_SIZE = 1L << 30;

  private Main() {}

  /**
   * Runs the command.
   *
   * @param args the command-line arguments (there must be none)
   */
  public static void main(String[] args) {
    if (args.length > 0) {
      PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
      err.print(USAGE);
      err.flush();
      System.exit(2);
      return;
    }
    int[] status = {0};
    Throwable[] failure = {null};
    Thread worker = new Thread(null, () -> {
      try {
        status[0] = run(System.in, System.out);
      } catch (IOException | RuntimeException | Error e) {
        failure[0] = e;
      }
    }, "yup", STACK_SIZE);
    worker.start();
    try {
      worker.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      failure[0] = e;
    }
    if (failure[0] != null) {
      failure[0].printStackTrace();
      System.exit(3);
    }
    System.exit(status[0]);
  }

  /**
   * Answers every request of the input.
   *
   * @param in the requests, UTF-8 JSON lines
   * @param out where the responses go
   * @return the exit status: 0 when every request succeeded, 1 otherwise
   * @throws IOException when reading or writing fails
   */
  static int run(InputStream in, OutputStream out) throws IOException {
    String input = decodeUtf8(in.readAllBytes());
    boolean failed = false;
    List<String> output = new ArrayList<>();
    for (String line : input.split("\n", -1)) {
      if (line.isEmpty()) {
        continue;
      }
      JsObject response;
      try {
        response = Requests.respond(line);
      } catch (JsPromise.NeverSettled e) {
        // Node runs out of work while the request is pending and exits before writing anything (status 0).
        return 0;
      } catch (RuntimeException e) {
        failed = true;
        response = errorResponse(e);
      } catch (StackOverflowError e) {
        failed = true;
        response = errorResponse(JsError.rangeError("Maximum call stack size exceeded"));
      }
      output.add(Js.toJsString(JsonWriter.stringify(response)));
    }
    StringBuilder text = new StringBuilder();
    for (String line : output) {
      text.append(line).append('\n');
    }
    out.write(text.toString().getBytes(StandardCharsets.UTF_8));
    out.flush();
    return failed ? 1 : 0;
  }

  // UTF-8 decoding as the WHATWG Encoding standard (and so Node's Buffer#toString) does it: every maximal invalid
  // subsequence becomes one U+FFFD. The JDK's decoder differs for encoded surrogates (ED A0 80 is one U+FFFD there).
  static String decodeUtf8(byte[] bytes) {
    StringBuilder text = new StringBuilder(bytes.length);
    int needed = 0;
    int seen = 0;
    int codePoint = 0;
    int lower = 0x80;
    int upper = 0xBF;
    for (int i = 0; i < bytes.length; i++) {
      int b = bytes[i] & 0xFF;
      if (needed == 0) {
        if (b <= 0x7F) {
          text.append((char) b);
        } else if (b >= 0xC2 && b <= 0xDF) {
          needed = 1;
          codePoint = b & 0x1F;
        } else if (b >= 0xE0 && b <= 0xEF) {
          lower = b == 0xE0 ? 0xA0 : 0x80;
          upper = b == 0xED ? 0x9F : 0xBF;
          needed = 2;
          codePoint = b & 0xF;
        } else if (b >= 0xF0 && b <= 0xF4) {
          lower = b == 0xF0 ? 0x90 : 0x80;
          upper = b == 0xF4 ? 0x8F : 0xBF;
          needed = 3;
          codePoint = b & 0x7;
        } else {
          text.append('�');
        }
        continue;
      }
      if (b < lower || b > upper) {
        needed = 0;
        seen = 0;
        lower = 0x80;
        upper = 0xBF;
        text.append('�');
        i--;
        continue;
      }
      lower = 0x80;
      upper = 0xBF;
      codePoint = (codePoint << 6) | (b & 0x3F);
      if (++seen == needed) {
        text.appendCodePoint(codePoint);
        needed = 0;
        seen = 0;
      }
    }
    if (needed != 0) {
      text.append('�');
    }
    return text.toString();
  }

  // {"error":{"name":NAME,"message":MESSAGE}} for a thrown value.
  private static JsObject errorResponse(RuntimeException exception) {
    Object thrown = JsException.valueOf(exception);
    JsObject error = new JsObject();
    if (thrown instanceof JsError || thrown instanceof JsException || !(thrown instanceof RuntimeException)) {
      error.put("name", Js.get(thrown, "name"));
      error.put("message", Js.get(thrown, "message"));
    } else {
      error.put("name", "InternalError");
      error.put("message", exception.toString());
    }
    return JsObject.of("error", error);
  }
}
