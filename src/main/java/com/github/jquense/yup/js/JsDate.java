/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.time.LocalDate;

/**
 * A JavaScript {@code Date}: a time value in milliseconds since the epoch, or NaN. Local time is UTC, as the reference
 * command runs with {@code TZ=UTC}.
 */
public final class JsDate {
  private static final double MS_PER_DAY = 86400000.0;
  private static final double MAX_TIME = 8.64e15;
  private static final String[] WEEKDAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
  private static final String[] MONTHS = {
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };

  private final double time;

  /**
   * {@code new Date(time)} for a number: the time value after {@code TimeClip}.
   *
   * @param time milliseconds since the epoch
   */
  public JsDate(double time) {
    this.time = timeClip(time);
  }

  /**
   * {@code new Date(string)}: parsed as {@code Date.parse} parses it.
   *
   * @param text the date string
   * @return the date, invalid when the string is not a date V8 accepts
   */
  public static JsDate parse(String text) {
    return new JsDate(DateParser.parse(text));
  }

  /**
   * {@code Date.parse(text)}.
   *
   * @param text the date string
   * @return the time value, NaN when the string is not a date V8 accepts
   */
  public static double parseTime(String text) {
    return DateParser.parse(text);
  }

  /** @return the time value ({@code getTime()}), NaN for an invalid date */
  public double getTime() {
    return time;
  }

  /** @return whether the time value is NaN */
  public boolean isInvalid() {
    return Double.isNaN(time);
  }

  /**
   * {@code TimeClip}.
   *
   * @param time a time value
   * @return the integral time value, NaN when it is out of range
   */
  public static double timeClip(double time) {
    if (Double.isNaN(time) || Double.isInfinite(time) || Math.abs(time) > MAX_TIME) {
      return Double.NaN;
    }
    double truncated = time < 0 ? Math.ceil(time) : Math.floor(time);
    return truncated + 0.0;
  }

  /**
   * V8's {@code MakeDay}.
   *
   * @param year the year
   * @param month the month (0-based, may overflow)
   * @param date the day of the month (may overflow)
   * @return the day number, NaN when out of range
   */
  public static double makeDay(double year, double month, double date) {
    if (!(year >= -1000000 && year <= 1000000) || !(month >= -10000000 && month <= 10000000)
        || Double.isNaN(date) || Double.isInfinite(date)) {
      return Double.NaN;
    }
    int y = (int) year;
    int m = (int) month;
    y += m / 12;
    m %= 12;
    if (m < 0) {
      m += 12;
      y -= 1;
    }
    final int yearDelta = 399999;
    final int baseDay = 365 * (1970 + yearDelta) + (1970 + yearDelta) / 4 - (1970 + yearDelta) / 100
        + (1970 + yearDelta) / 400;
    int dayFromYear = 365 * (y + yearDelta) + (y + yearDelta) / 4 - (y + yearDelta) / 100 + (y + yearDelta) / 400
        - baseDay;
    boolean leap = y % 4 == 0 && (y % 100 != 0 || y % 400 == 0);
    int[] dayFromMonth = leap
        ? new int[] {0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335}
        : new int[] {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
    dayFromYear += dayFromMonth[m];
    return (double) (dayFromYear - 1) + toInteger(date);
  }

  /**
   * {@code MakeTime}.
   *
   * @param hour hours
   * @param minute minutes
   * @param second seconds
   * @param millisecond milliseconds
   * @return the time within the day in milliseconds, NaN when an argument is not finite
   */
  public static double makeTime(double hour, double minute, double second, double millisecond) {
    if (!isFinite(hour) || !isFinite(minute) || !isFinite(second) || !isFinite(millisecond)) {
      return Double.NaN;
    }
    return toInteger(hour) * 3600000.0 + toInteger(minute) * 60000.0 + toInteger(second) * 1000.0
        + toInteger(millisecond);
  }

  /**
   * {@code MakeDate}.
   *
   * @param day a day number
   * @param time a time within the day
   * @return the time value, NaN when an argument is not finite
   */
  public static double makeDate(double day, double time) {
    if (!isFinite(day) || !isFinite(time)) {
      return Double.NaN;
    }
    return day * MS_PER_DAY + time;
  }

  /**
   * {@code new Date(year, month, day, hours, minutes, seconds, ms)} in local time (UTC), and equally
   * {@code Date.UTC(...)}: a year from 0 to 99 means 1900 + year.
   *
   * @param parts year, month, then optionally day, hours, minutes, seconds and milliseconds
   * @return the time value
   */
  public static double fromParts(double... parts) {
    double year = parts[0];
    double month = parts.length > 1 ? parts[1] : 0;
    double day = parts.length > 2 ? parts[2] : 1;
    double hours = parts.length > 3 ? parts[3] : 0;
    double minutes = parts.length > 4 ? parts[4] : 0;
    double seconds = parts.length > 5 ? parts[5] : 0;
    double ms = parts.length > 6 ? parts[6] : 0;
    if (!Double.isNaN(year)) {
      double y = toInteger(year);
      if (y >= 0 && y <= 99) {
        year = 1900 + y;
      }
    }
    return timeClip(makeDate(makeDay(year, month, day), makeTime(hours, minutes, seconds, ms)));
  }

  private static boolean isFinite(double d) {
    return !Double.isNaN(d) && !Double.isInfinite(d);
  }

  private static double toInteger(double d) {
    if (Double.isNaN(d)) {
      return 0;
    }
    return d < 0 ? Math.ceil(d) : Math.floor(d);
  }

  private static long[] fields(double time) {
    long t = (long) time;
    long day = Math.floorDiv(t, 86400000L);
    long msInDay = Math.floorMod(t, 86400000L);
    LocalDate date = LocalDate.ofEpochDay(day);
    long weekday = Math.floorMod(day + 4, 7L);
    return new long[] {
      date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth(), msInDay / 3600000, msInDay / 60000 % 60,
      msInDay / 1000 % 60, msInDay % 1000, weekday
    };
  }

  /**
   * {@code Date.prototype.toISOString} for a valid date.
   *
   * @return {@code YYYY-MM-DDTHH:mm:ss.sssZ}, with a signed six-digit year outside 0..9999
   */
  public String toISOString() {
    if (isInvalid()) {
      throw JsError.rangeError("Invalid time value");
    }
    long[] f = fields(time);
    long year = f[0];
    String yearText;
    if (year >= 0 && year <= 9999) {
      yearText = String.format("%04d", year);
    } else {
      yearText = (year < 0 ? "-" : "+") + String.format("%06d", Math.abs(year));
    }
    return String.format("%s-%02d-%02dT%02d:%02d:%02d.%03dZ", yearText, f[1] + 1, f[2], f[3], f[4], f[5], f[6]);
  }

  /** @return {@code Date.prototype.toString}: {@code Thu Jan 01 1970 00:00:00 GMT+0000 (Coordinated Universal Time)} */
  @Override
  public String toString() {
    if (isInvalid()) {
      return "Invalid Date";
    }
    long[] f = fields(time);
    String year = f[0] < 0 ? String.format("%05d", f[0]) : String.format("%04d", f[0]);
    return String.format("%s %s %02d %s %02d:%02d:%02d GMT+0000 (Coordinated Universal Time)",
        WEEKDAYS[(int) f[7]], MONTHS[(int) f[1]], f[2], year, f[3], f[4], f[5]);
  }
}
