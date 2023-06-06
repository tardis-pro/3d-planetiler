package com.onthegomap.planetiler.examples;

import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.Arrays;
import java.util.List;

class RoadwayLanes {
  Integer both = null;
  Integer forward = null;
  Integer backward = null;
}

enum RoadwayExtensionSide {
  LEFT,
  RIGHT,
  BOTH
}

public class StreetsUtils {
  private static ColorParser colorParser = new ColorParser();

  private static final List<String> memorialTypes = Arrays.asList(
    "war_memorial", "stele", "obelisk", "memorial", "stone"
  );

  public static boolean isMemorial(SourceFeature sourceFeature) {
    String memorialType = (String) sourceFeature.getTag("memorial");

    return sourceFeature.hasTag("historic", "memorial") && (
      memorialTypes.contains(memorialType) || memorialType == null
    );
  }

  public static boolean isFireHydrant(SourceFeature sourceFeature) {
    return sourceFeature.hasTag("emergency", "fire_hydrant") &&
      (sourceFeature.hasTag("fire_hydrant:type", "pillar") || sourceFeature.getTag("fire_hydrant:type") == null);
  }

  public static boolean isStatue(SourceFeature sourceFeature) {
    return sourceFeature.hasTag("historic", "memorial") && sourceFeature.hasTag("memorial", "statue") ||
      sourceFeature.hasTag("tourism", "artwork") && sourceFeature.hasTag("artwork_type", "statue");
  }

  public static boolean isSculpture(SourceFeature sourceFeature) {
    return sourceFeature.hasTag("tourism", "artwork") && sourceFeature.hasTag("artwork_type", "sculpture") ||
      sourceFeature.hasTag("historic", "memorial") && sourceFeature.hasTag("memorial", "sculpture");
  }

  public static boolean isWindTurbine(SourceFeature sourceFeature) {
    return sourceFeature.hasTag("power", "generator") && sourceFeature.hasTag("generator:source", "wind");
  }

  public static boolean isRailway(SourceFeature sourceFeature) {
    return sourceFeature.hasTag("railway",
      "rail",
      "light_rail",
      "subway",
      "disused",
      "narrow_gauge",
      "tram"
    );
  }

  public static boolean isWater(SourceFeature sourceFeature) {
    return sourceFeature.getSource().equals("water") ||
      sourceFeature.hasTag("natural", "water") ||
      (
        sourceFeature.hasTag("leisure", "swimming_pool") &&
        !sourceFeature.hasTag("location", "indoor", "roof")
      );
  }

  public static Boolean isRoadwayOneway(SourceFeature sourceFeature) {
    String oneway = (String) sourceFeature.getTag("oneway", "");

    if (oneway.equals("no")) {
      return false;
    }

    if (oneway.equals("yes") || sourceFeature.hasTag("junction", "roundabout")) {
      return true;
    }

    return null;
  }

  public static RoadwayLanes getRoadwayLanes(SourceFeature sourceFeature) {
    Integer lanes = parseUnsignedInt((String) sourceFeature.getTag("lanes"));
    Integer lanesForward = parseUnsignedInt((String) sourceFeature.getTag("lanes:forward"));
    Integer lanesBackward = parseUnsignedInt((String) sourceFeature.getTag("lanes:backward"));

    return new RoadwayLanes() {{
      both = lanes;
      forward = lanesForward;
      backward = lanesBackward;
    }};
  }

  public static String getFenceType(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("fence_type"));
  }

  public static String getWallType(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("wall"));
  }

  public static String getRailwayType(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("railway"));
  }

  public static String getWaterwayType(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("waterway"));
  }

  public static Double getHeight(SourceFeature sourceFeature) {
    Double height = parseDouble((String) sourceFeature.getTag("height"));
    Double estHeight = parseDouble((String) sourceFeature.getTag("est_height"));

    if (height != null) {
      return height;
    }

    return estHeight;
  }

  public static Double getMinHeight(SourceFeature sourceFeature) {
    return parseDouble((String) sourceFeature.getTag("min_height"));
  }

  public static Double getRoofHeight(SourceFeature sourceFeature) {
    return parseDouble((String) sourceFeature.getTag("roof:height"));
  }

  public static Integer getRoofLevels(SourceFeature sourceFeature) {
    return parseUnsignedInt((String) sourceFeature.getTag("roof:levels"));
  }

  public static String getRoofMaterial(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("roof:material"));
  }

  public static String getRoofShape(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("roof:shape"));
  }

  public static String getBuildingMaterial(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("building:material"));
  }

  public static Integer getBuildingLevels(SourceFeature sourceFeature) {
    return parseUnsignedInt((String) sourceFeature.getTag("building:levels"));
  }

  public static Integer getBuildingColor(SourceFeature sourceFeature) {
    String color = getFirstTagValue((String) sourceFeature.getTag("building:colour"));
    return colorParser.parseColor(color);
  }

  public static Integer getRoofColor(SourceFeature sourceFeature) {
    String color = getFirstTagValue((String) sourceFeature.getTag("roof:colour"));
    return colorParser.parseColor(color);
  }

  public static String getRoofOrientation(SourceFeature sourceFeature) {
    String orientation = (String) sourceFeature.getTag("roof:orientation", "");

    if (orientation.equals("along") || orientation.equals("across")) {
      return orientation;
    }

    return null;
  }

  public static Double getWidth(SourceFeature sourceFeature) {
    return parseDouble((String) sourceFeature.getTag("width"));
  }

  public static Double getDirection(SourceFeature sourceFeature) {
    return DirectionParser.parse((String) sourceFeature.getTag("direction"));
  }

  public static Double getRoofDirection(SourceFeature sourceFeature) {
    return DirectionParser.parse((String) sourceFeature.getTag("roof:direction"));
  }

  public static Double getAngle(SourceFeature sourceFeature) {
    return parseDouble((String) sourceFeature.getTag("angle"));
  }

  public static String getLeafType(SourceFeature sourceFeature) {
    String leafType = (String) sourceFeature.getTag("leaf_type");

    return getFirstTagValue(leafType);
  }

  public static String getGenus(SourceFeature sourceFeature) {
    String genusValue = getFirstTagValue((String) sourceFeature.getTag("genus"));
    String genusEngValue = getFirstTagValue((String) sourceFeature.getTag("genus:en"));

    return genusValue != null ? genusValue : genusEngValue;
  }

  public static String getSurface(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("surface"));
  }

  public static Boolean getLaneMarkings(SourceFeature sourceFeature) {
    String value = (String) sourceFeature.getTag("lane_markings", "");

    if (value.equals("yes")) {
      return true;
    }

    if (value.equals("no")) {
      return false;
    }

    return null;
  }

  public static String getCrop(SourceFeature sourceFeature) {
    return getFirstTagValue((String) sourceFeature.getTag("crop"));
  }

  public static Double parseDouble(String value) {
    if (value == null) return null;

    try {
      return Double.parseDouble(value);
    } catch (Exception ex) {
      return null;
    }
  }

  public static Integer parseUnsignedInt(String value) {
    if (value == null) return null;

    try {
      return Math.max(0, (int) Double.parseDouble(value));
    } catch (Exception ex) {
      return null;
    }
  }

  public static String getFirstTagValue(String value) {
    if (value == null) {
      return null;
    }

    String[] values = value.split(";");

    if (values.length == 0) {
      return null;
    }

    return values[0];
  }

  public static RoadwayExtensionSide getSidewalkSide(SourceFeature sourceFeature) {
    String sidewalkValue = (String)sourceFeature.getTag("sidewalk");
    String sidewalkBothValue = (String)sourceFeature.getTag("sidewalk:both");
    String sidewalkLeftValue = (String)sourceFeature.getTag("sidewalk:left");
    String sidewalkRightValue = (String)sourceFeature.getTag("sidewalk:right");

    sidewalkValue = sidewalkValue == null ? "" : sidewalkValue;
    sidewalkBothValue = sidewalkBothValue == null ? "" : sidewalkBothValue;
    sidewalkLeftValue = sidewalkLeftValue == null ? "" : sidewalkLeftValue;
    sidewalkRightValue = sidewalkRightValue == null ? "" : sidewalkRightValue;

    boolean isBoth = sidewalkBothValue.equals("yes") || sidewalkValue.equals("both");
    boolean isLeft = isBoth || sidewalkLeftValue.equals("yes") || sidewalkValue.equals("left");
    boolean isRight = isBoth || sidewalkRightValue.equals("yes") || sidewalkValue.equals("right");

    if (isLeft && isRight) {
      return RoadwayExtensionSide.BOTH;
    }

    if (isLeft) {
      return RoadwayExtensionSide.LEFT;
    }

    if (isRight) {
      return RoadwayExtensionSide.RIGHT;
    }

    return null;
  }

  public static RoadwayExtensionSide getCyclewaySide(SourceFeature sourceFeature) {
    String cyclewayBothValue = (String)sourceFeature.getTag("cycleway:both");
    String cyclewayLeftValue = (String)sourceFeature.getTag("cycleway:left");
    String cyclewayRightValue = (String)sourceFeature.getTag("cycleway:right");

    cyclewayBothValue = cyclewayBothValue == null ? "" : cyclewayBothValue;
    cyclewayLeftValue = cyclewayLeftValue == null ? "" : cyclewayLeftValue;
    cyclewayRightValue = cyclewayRightValue == null ? "" : cyclewayRightValue;

    boolean isBoth = cyclewayBothValue.equals("lane");
    boolean isLeft = isBoth || cyclewayLeftValue.equals("lane");
    boolean isRight = isBoth || cyclewayRightValue.equals("lane");

    if (isLeft && isRight) {
      return RoadwayExtensionSide.BOTH;
    }

    if (isLeft) {
      return RoadwayExtensionSide.LEFT;
    }

    if (isRight) {
      return RoadwayExtensionSide.RIGHT;
    }

    return null;
  }

  public static Integer convertRoadwayExtensionSideToInteger(RoadwayExtensionSide side) {
    if (side == null) {
      return null;
    }

    switch (side) {
      case LEFT:
        return 0;
      case RIGHT:
        return 1;
      case BOTH:
        return 2;
    }

    return null;
  }

  public static boolean isUnderground(SourceFeature sourceFeature) {
    String layerTag = (String)sourceFeature.getTag("layer");

    if (layerTag != null) {
      float layer = Float.parseFloat(layerTag);

      if (layer < 0) {
        return true;
      }
    }

    String tunnelValue = (String)sourceFeature.getTag("tunnel", "no");
    boolean isInTunnel = !tunnelValue.equals("no");

    return sourceFeature.hasTag("location", "underground") ||
      isInTunnel ||
      sourceFeature.hasTag("parking", "underground");
  }

  private static final List<String> buildingsWithoutWindows = Arrays.asList(
    "garage", "garages", "greenhouse", "storage_tank", "bunker", "silo", "stadium",
    "ship", "castle", "service", "digester", "water_tower", "shed", "ger", "barn",
    "slurry_tank", "container", "carport"
  );

  public static Boolean getBuildingWindows(SourceFeature sourceFeature) {
    String windowValue = (String)sourceFeature.getTag("window", "");
    String windowsValue = (String)sourceFeature.getTag("windows", "");

    if (windowValue.equals("no") || windowsValue.equals("no")) {
      return false;
    }

    if (windowValue.equals("yes") || windowsValue.equals("yes")) {
      return true;
    }

    if (
      sourceFeature.hasTag("bridge:support") ||
        sourceFeature.hasTag("man_made", "storage_tank") ||
        sourceFeature.hasTag("man_made", "chimney") ||
        sourceFeature.hasTag("man_made", "stele") ||
        sourceFeature.hasTag("advertising", "billboard") ||
        sourceFeature.hasTag("historic", "city_gate")
    ) {
      return false;
    }

    return null;
  }
}
