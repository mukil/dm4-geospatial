package de.deepamehta.plugins.geospatial;

import de.deepamehta.geomaps.model.GeoCoordinate;

import de.deepamehta.core.Topic;

import java.util.List;
import javax.ws.rs.core.Response;



public interface GeospatialService {

    List<Topic> getTopicsWithinDistance(GeoCoordinate geoCoord, double maxDistanceInKm);

    Response doIndexGeometryLayer(String absoluteFile);

    String getGeometryFeatureNameByCoordinate(String latlng);

    Object getGeometryFeatureValueByCoordinate(String latlng, String valueKey);

}
