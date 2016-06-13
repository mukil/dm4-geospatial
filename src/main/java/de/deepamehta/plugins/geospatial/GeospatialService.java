package de.deepamehta.plugins.geospatial;

import de.deepamehta.geomaps.model.GeoCoordinate;

import de.deepamehta.core.Topic;

import java.util.List;



public interface GeospatialService {

    List<Topic> getTopicsWithinDistance(GeoCoordinate geoCoord, double maxDistanceInKm);
}
