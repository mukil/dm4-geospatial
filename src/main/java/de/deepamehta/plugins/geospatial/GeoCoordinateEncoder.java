package de.deepamehta.plugins.geospatial;

import de.deepamehta.core.CompositeValue;
import de.deepamehta.core.Topic;
import de.deepamehta.core.service.DeepaMehtaService;
import de.deepamehta.plugins.geomaps.model.GeoCoordinate;

import org.neo4j.gis.spatial.AbstractGeometryEncoder;
import org.neo4j.gis.spatial.Layer;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

import java.util.logging.Logger;



public class GeoCoordinateEncoder extends AbstractGeometryEncoder {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private Layer layer;

    private DeepaMehtaService dms;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public void encodeGeometryShape(Geometry geometry, PropertyContainer container) {    // abstract in AGE
        // empty
    }

    @Override
    public Geometry decodeGeometry(PropertyContainer container) {
        long geoCoordId = ((Node) container).getId();
        GeoCoordinate geoCoord = geoCoordinate(dms.getTopic(geoCoordId, true));  // fetchComposite=true
        logger.info("########## Decoding Geo Coordinate " + geoCoordId + " (lon=" + geoCoord.lon +
            ", lat=" + geoCoord.lat + ")");
        return layer.getGeometryFactory().createPoint(new Coordinate(geoCoord.lon, geoCoord.lat));
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    void init(Layer layer, DeepaMehtaService dms) {
        this.layer = layer;
        this.dms = dms;
    }

    // ------------------------------------------------------------------------------------------------- Private Methods

    // ### TODO: move to geomaps service?
    private GeoCoordinate geoCoordinate(Topic geoCoordTopic) {
        CompositeValue comp = geoCoordTopic.getCompositeValue();
        return new GeoCoordinate(
            comp.getDouble("dm4.geomaps.longitude"),
            comp.getDouble("dm4.geomaps.latitude")
        );
    }
}
