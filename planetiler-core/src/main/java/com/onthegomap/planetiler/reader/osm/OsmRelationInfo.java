package com.onthegomap.planetiler.reader.osm;

import com.onthegomap.planetiler.util.MemoryEstimator;

/**
 * A user-defined class containing information about a relation that will be relevant during subsequent way processing.
 * This is stored in-memory by {@link OsmReader} so keep it as compact as possible.
 */
public interface OsmRelationInfo extends MemoryEstimator.HasEstimate {

  /** Returns the OSM ID of this relation. */
  long id();

  @Override
  default long estimateMemoryUsageBytes() {
    return 0;
  }

  default long vectorTileFeatureId(int multiplier) {
    return OsmElement.vectorTileFeatureId(multiplier, id(), OsmElement.Type.RELATION);
  }
}
