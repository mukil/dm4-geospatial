package de.deepamehta.plugins.geospatial.service;

import de.deepamehta.plugins.geomaps.model.GeoCoordinate;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.PluginService;

import java.util.List;



public interface GeospatialService extends PluginService {

    List<Topic> getTopicsWithinDistance(GeoCoordinate geoCoord, double maxDistanceInKm);
}
