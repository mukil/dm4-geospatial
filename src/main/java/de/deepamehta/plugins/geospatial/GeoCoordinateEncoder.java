package de.deepamehta.plugins.geospatial;

import de.deepamehta.core.service.DeepaMehtaService;

import org.neo4j.gis.spatial.AbstractGeometryEncoder;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import java.util.logging.Logger;



public class GeoCoordinateEncoder extends AbstractGeometryEncoder {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private PointFactory pointFactory;

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
        Point point = pointFactory.createPoint(dms.getTopic(geoCoordId, true));     // fetchComposite=true
        logger.info("########## Decoding Geo Coordinate " + geoCoordId + " (lon=" + point.getX() + ", lat=" +
            point.getY() + ")");
        return point;
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    void init(PointFactory pointFactory, DeepaMehtaService dms) {
        this.pointFactory = pointFactory;
        this.dms = dms;
    }
}
