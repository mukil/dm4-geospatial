package systems.dmx.geospatial;

import java.util.List;
import javax.ws.rs.core.Response;
import systems.dmx.core.Topic;
import systems.dmx.geomaps.GeoCoordinate;

public interface GeospatialService {

    List<Topic> getTopicsWithinDistance(GeoCoordinate geoCoord, double maxDistanceInKm);

    Response setGeometryLayer(String absoluteFile);

    String getGeometryFeatureNameByCoordinate(String latlng);

    Object getGeometryFeatureValueByCoordinate(String latlng, String valueKey);

    boolean validWGS84Coordinates(GeoCoordinate pos);

}
