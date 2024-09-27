package com.onthegomap.planetiler.util;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Utilities to parse values from strings.
 */
public class Parse {

  private static final Pattern INT_SUBSTRING_PATTERN = Pattern.compile("^(-?\\d+)(\\D|$)");
  private static final Pattern TO_ROUND_INT_SUBSTRING_PATTERN = Pattern.compile("^(-?[\\d.]+)(\\D|$)");
  // See https://wiki.openstreetmap.org/wiki/Map_features/Units
  private static final Pattern DISTANCE =
    Pattern.compile(
      "(?<value>-?[\\d.]+)\\s*((?<mi>mi)|(?<m>m|$)|(?<km>km|kilom)|(?<ft>ft|')|(?<in>in|\")|(?<nmi>nmi|international nautical mile|nautical))",
      Pattern.CASE_INSENSITIVE);
  private static final Pattern NUMBER_WITH_UNIT =
    Pattern.compile(
      "(?<value>-?[\\d.]+)\\s*(?<unit>[^.\\d]*)",
      Pattern.CASE_INSENSITIVE);
  // Ignore warnings about not removing thread local values since planetiler uses dedicated worker threads that release
  // values when a task is finished and are not re-used.
  @SuppressWarnings("java:S5164")
  private static final ThreadLocal<NumberFormat> PARSER =
    ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.ROOT));

  private Parse() {}

  /** Returns {@code tag} as a string or null if null. */
  public static String parseStringOrNull(Object tag) {
    return tag == null ? null : tag.toString();
  }

  /** Returns {@code tag} as a long or null if invalid. */
  public static Long parseLongOrNull(Object tag) {
    return tag == null ? null : tag instanceof Number number ? Long.valueOf(number.longValue()) :
      parseLongOrNull(tag.toString());
  }

  /** Returns {@code tag} as a long or null if invalid. */
  public static Long parseLongOrNull(String tag) {
    try {
      return tag == null ? null : Long.parseLong(tag);
    } catch (NumberFormatException e) {
      return retryParseNumber(tag, Number::longValue, null);
    }
  }

  private static <T> T retryParseNumber(Object obj, Function<Number, T> getter, T backup) {
    // more expensive parser in case simple valueOf parse fails
    try {
      return getter.apply(PARSER.get().parse(obj.toString()));
    } catch (ParseException e) {
      return backup;
    }
  }

  /** Returns {@code tag} as a long or 0 if invalid. */
  public static long parseLong(Object tag) {
    try {
      return tag == null ? 0 : tag instanceof Number number ? number.longValue() : Long.parseLong(tag.toString());
    } catch (NumberFormatException e) {
      return retryParseNumber(tag, Number::longValue, 0L);
    }
  }

  /** Returns {@code tag} as an integer or null if invalid, ignoring any non-numeric suffix. */
  public static Integer parseIntSubstring(String tag) {
    if (tag == null) {
      return null;
    }
    try {
      var matcher = INT_SUBSTRING_PATTERN.matcher(tag);
      return matcher.find() ? Integer.parseInt(matcher.group(1)) : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Returns {@code tag} as a number rounded to the nearest integer or null if invalid. */
  public static Integer parseRoundInt(Object tag) {
    if (tag == null) {
      return null;
    }
    try {
      var matcher = TO_ROUND_INT_SUBSTRING_PATTERN.matcher(tag.toString());
      return matcher.find() ? Math.round(Float.parseFloat(matcher.group(1))) : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Returns {@code tag} as an integer or null if invalid. */
  public static Integer parseIntOrNull(Object tag) {
    return tag == null ? null : tag instanceof Number number ? Integer.valueOf(number.intValue()) :
      parseIntOrNull(tag.toString());
  }

  /** Returns {@code tag} as an integer or null if invalid. */
  public static Integer parseIntOrNull(String tag) {
    try {
      return tag == null ? null : Integer.parseInt(tag);
    } catch (NumberFormatException e) {
      return retryParseNumber(tag, Number::intValue, null);
    }
  }

  /** Returns {@code tag} parsed as a boolean. */
  public static boolean bool(Object tag) {
    return Imposm3Parsers.bool(tag);
  }

  /** Returns {@code tag} parsed as an integer where 1 is true, 0 is false. */
  public static int boolInt(Object tag) {
    return Imposm3Parsers.boolInt(tag);
  }

  /** Returns {@code tag} parsed as an integer where 1 is true, 0 is false. */
  public static int direction(Object input) {
    return Imposm3Parsers.direction(input);
  }

  /**
   * Returns {@code tag} for an OSM road based on the tags that are present. Bridges are above roads appear above
   * tunnels and major roads appear above minor.
   */
  public static int wayzorder(Map<String, Object> tags) {
    return Imposm3Parsers.wayzorder(tags);
  }

  /** Returns {@code tag} as a double or null if invalid. */
  public static Double parseDoubleOrNull(Object tag) {
    return tag == null ? null : tag instanceof Number number ? Double.valueOf(number.doubleValue()) :
      parseDoubleOrNull(tag.toString());
  }

  /** Returns {@code tag} as a double or null if invalid. */
  public static Double parseDoubleOrNull(String tag) {
    try {
      return tag == null ? null : Double.parseDouble(tag);
    } catch (NumberFormatException e) {
      return retryParseNumber(tag, Number::doubleValue, null);
    }
  }

  /** Parses a value for {@code -Xmx/-Xms} JVM option like "100" or "500k" or "15g" */
  public static long jvmMemoryStringToBytes(String value) {
    try {
      value = value.strip();
      char lastChar = value.charAt(value.length() - 1);
      if (Character.isDigit(lastChar)) {
        return Long.parseLong(value);
      } else {
        long base = Long.parseLong(value.substring(0, value.length() - 1));
        lastChar = Character.toLowerCase(lastChar);
        return switch (lastChar) {
          case 'k' -> base * 1024L;
          case 'm' -> base * 1024L * 1024L;
          case 'g' -> base * 1024L * 1024L * 1024L;
          default -> throw new NumberFormatException();
        };
      }
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Unable to parse size: " + value);
    }
  }

  /**
   * Parses {@code tag} as a measure of distance with unit, converted to a round number of meters or {@code null} if
   * invalid.
   * <p>
   * See <a href="https://wiki.openstreetmap.org/wiki/Map_features/Units">Map features/Units</a> for the list of
   * supported units.
   */
  public static Double meters(Object tag) {
    if (tag != null) {
      if (tag instanceof Number num) {
        return num.doubleValue();
      }
      var str = tag.toString();
      var matcher = DISTANCE.matcher(str);
      if (matcher.find()) {
        try {
          double value = Double.parseDouble(matcher.group("value"));
          if (matcher.group("m") != null) {
            // value *= 1;
          } else if (matcher.group("km") != null) {
            value *= 1000d;
          } else if (matcher.group("mi") != null) {
            value *= 1609.344;
          } else if (matcher.group("nmi") != null) {
            value *= 1852d;
          } else if (matcher.group("ft") != null) {
            value *= 12 * 0.0254;
            // handle 15'3"
            if (matcher.find() && matcher.group("in") != null) {
              value += Double.parseDouble(matcher.group("value")) * 0.0254;
            }
          } else if (matcher.group("in") != null) {
            value *= 0.0254;
          } else {
            return null;
          }
          return value;
        } catch (NumberFormatException e) {
          return null;
        }
      }
    }
    return null;
  }

  /**
   * Parses a string containing bandwidth, with units of kbps, mb/s, kib/s, etc.
   * <p>
   * Returned value is in units of bytes per second, or 0 if no limit specified.
   */
  public static double bandwidth(Object tag) {
    if (tag != null) {
      if (tag instanceof Number num) {
        return num.doubleValue();
      }
      var str = tag.toString();
      var matcher = NUMBER_WITH_UNIT.matcher(str);
      if (matcher.find()) {
        try {
          double value = Double.parseDouble(matcher.group("value"));
          String unit = matcher.group("unit").toLowerCase(Locale.ROOT).replaceAll("\\s", "");
          double multiplier = switch (unit) {
            case "b/s", "" -> 1;
            case "kb/s" -> 1_000;
            case "mb/s" -> 1_000_000;
            case "gb/s" -> 1_000_000_000;
            case "bps" -> 1d / 8;
            case "kbps" -> 1_000d / 8;
            case "mbps" -> 1_000_000d / 8;
            case "gbps" -> 1_000_000_000d / 8;
            case "kib/s" -> 1 << 10;
            case "mib/s" -> 1 << 20;
            case "gib/s" -> 1 << 30;
            default -> throw new IllegalArgumentException("Unable to parse bandwidth: " + tag);
          };
          double result = value * multiplier;
          if (result < 0) {
            throw new IllegalArgumentException("Unable to parse bandwidth: " + tag);
          }
          return result;
        } catch (NumberFormatException e) {
          throw new IllegalArgumentException("Unable to parse bandwidth: " + tag);
        }
      }
    }
    return 0;
  }
}
