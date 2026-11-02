/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense (github.com/jquense/yup)
 *
 * Java port of yup (https://github.com/jquense/yup). A modified version of
 * "Date.parse with progressive enhancement for ISO 8601" <https://github.com/csnover/js-iso8601>,
 * NON-CONFORMANT EDITION, (c) 2011 Colin Snover <http://zetafleet.com>, released under the MIT license.
 */
package com.github.jquense.yup.util;

import com.github.jquense.yup.js.Js;
import com.github.jquense.yup.js.JsDate;
import com.github.jquense.yup.js.JsRegExp;

/** The ISO 8601 date parsing behind {@code date()} casts and {@code string().datetime()}. */
public final class ParseIsoDate {
  private static final JsRegExp ISO_REG = new JsRegExp("^(\\d{4}|[+-]\\d{6})(?:-?(\\d{2})(?:-?(\\d{2}))?)?(?:[ T]?"
      + "(\\d{2}):?(\\d{2})(?::?(\\d{2})(?:[,.](\\d{1,}))?)?(?:(Z)|([+-])(\\d{2})(?::?(\\d{2}))?)?)?$");

  private ParseIsoDate() {}

  /**
   * The parts of an ISO date string.
   *
   * @param year the year
   * @param month the month, 0-based
   * @param day the day of the month
   * @param hour the hours
   * @param minute the minutes
   * @param second the seconds
   * @param millisecond the milliseconds (the first three fraction digits read as a number)
   * @param precision the number of fraction digits, or {@code undefined}
   * @param z {@code "Z"} or {@code undefined}
   * @param plusMinus the offset sign or {@code undefined}
   * @param hourOffset the offset hours
   * @param minuteOffset the offset minutes
   */
  public record DateStruct(double year, double month, double day, double hour, double minute, double second,
      double millisecond, Object precision, Object z, Object plusMinus, double hourOffset, double minuteOffset) {}

  /**
   * {@code parseIsoDate(date)}: the time value of an ISO date, local time when there is no zone; anything else goes
   * to {@code Date.parse}.
   *
   * @param date any value, converted to a string
   * @return the time value, NaN when it is not a date
   */
  public static double parseIsoDate(Object date) {
    DateStruct struct = parseDateStruct(date);
    if (struct == null) {
      return JsDate.parseTime(Js.toJsString(date));
    }
    if (struct.z() == Js.UNDEFINED && struct.plusMinus() == Js.UNDEFINED) {
      return JsDate.fromParts(struct.year(), struct.month(), struct.day(), struct.hour(), struct.minute(),
          struct.second(), struct.millisecond());
    }
    double totalMinutesOffset = 0;
    if (!"Z".equals(struct.z()) && struct.plusMinus() != Js.UNDEFINED) {
      totalMinutesOffset = struct.hourOffset() * 60 + struct.minuteOffset();
      if ("+".equals(struct.plusMinus())) {
        totalMinutesOffset = 0 - totalMinutesOffset;
      }
    }
    return JsDate.fromParts(struct.year(), struct.month(), struct.day(), struct.hour(),
        struct.minute() + totalMinutesOffset, struct.second(), struct.millisecond());
  }

  /**
   * {@code parseDateStruct(date)}.
   *
   * @param date any value, converted to a string
   * @return its parts, or null when it does not match the ISO pattern
   */
  public static DateStruct parseDateStruct(Object date) {
    JsRegExp.Match match = ISO_REG.exec(Js.toJsString(date));
    if (match == null) {
      return null;
    }
    String fraction = match.group(7);
    return new DateStruct(
        toNumber(match.group(1), 0),
        toNumber(match.group(2), 1) - 1,
        toNumber(match.group(3), 1),
        toNumber(match.group(4), 0),
        toNumber(match.group(5), 0),
        toNumber(match.group(6), 0),
        fraction != null && !fraction.isEmpty() ? toNumber(fraction.substring(0, Math.min(3, fraction.length())), 0) : 0,
        fraction == null ? Js.UNDEFINED : (Object) (double) fraction.length(),
        match.group(8) == null || match.group(8).isEmpty() ? Js.UNDEFINED : match.group(8),
        match.group(9) == null || match.group(9).isEmpty() ? Js.UNDEFINED : match.group(9),
        toNumber(match.group(10), 0),
        toNumber(match.group(11), 0));
  }

  // Number(str) || defaultValue.
  private static double toNumber(String s, double defaultValue) {
    double n = s == null ? Double.NaN : Js.toNumber(s);
    return Js.truthy(n) ? n : defaultValue;
  }
}
