package de.deepamehta.plugins.geospatial;

import com.vividsolutions.jts.geom.Coordinate;
import de.deepamehta.core.service.DeepaMehtaService;

import org.neo4j.gis.spatial.AbstractGeometryEncoder;
import org.neo4j.graphdb.PropertyContainer;

import com.vividsolutions.jts.geom.Geometry;

import java.util.logging.Logger;



public class GeoCoordinateEncoder extends AbstractGeometryEncoder {

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private final String LON_PROPERTY = "longitude";
    private final String LAT_PROPERTY = "latitude";

    private PointFactory pointFactory;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods

    @Override
    public void encodeGeometryShape(Geometry geometry, PropertyContainer container) {    // abstract in AGE
        // 3. This will encode our data into new (geometry) nodes (e.g. according to the OSM Example)
        //    and allows the decoding to happen much faster (no topic-lookup anymore).
        container.setProperty("gtype", GTYPE_POINT);
        Coordinate[] coords = geometry.getCoordinates();
        container.setProperty(LON_PROPERTY, coords[0].x);
        container.setProperty(LAT_PROPERTY, coords[0].y);
        logger.info("### Encoding Geo Coordinate (lon=" + coords[0].x + ", lat=" + coords[0].y +"), "
                + "Topic ID: " + container.getProperty("topic_id"));
    }

    @Override
    public Geometry decodeGeometry(PropertyContainer container) {
        // This method is called twice by a neo4j-spatial layer,
        //   1.) when populating the index (but just when using "layer.add(Node)")
        //   2.) during querywhen populating results from our index
        // Note: (1) using the DeepaMehtaService during decoding of every single item/node/topic
        //       was very expensive (a couple fo seconds).
        // This implementation handles both much faster (1/8-12th) and its results should be as consistent.
        double xLon = ( (Number) container.getProperty(LON_PROPERTY)).doubleValue();
        double yLat = ( (Number) container.getProperty(LAT_PROPERTY)).doubleValue();
        long geoCoordId = ( (Number) container.getProperty("topic_id")).longValue();
        logger.info("### Decoding Geo Coordinate (lon=" + xLon+ ", lat=" + yLat +"), "
                + "Topic ID: " + geoCoordId);
        return pointFactory.createPointByCoordinates(xLon, yLat);
    }

    // ----------------------------------------------------------------------------------------- Package Private Methods

    void init(PointFactory pointFactory, DeepaMehtaService dms) {
        this.pointFactory = pointFactory;
    }
}
