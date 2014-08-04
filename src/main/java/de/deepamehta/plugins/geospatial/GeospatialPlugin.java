package de.deepamehta.plugins.geospatial;

import de.deepamehta.plugins.geomaps.model.GeoCoordinate;

import de.deepamehta.core.CompositeValue;
import de.deepamehta.core.Topic;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.event.PostCreateTopicListener;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;

import org.neo4j.gis.spatial.AbstractGeometryEncoder;
import org.neo4j.gis.spatial.EditableLayerImpl;
import org.neo4j.gis.spatial.Layer;
import org.neo4j.gis.spatial.SimplePointLayer;
import org.neo4j.gis.spatial.SpatialDatabaseService;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import java.util.logging.Logger;



public class GeospatialPlugin extends PluginActivator implements PostCreateTopicListener {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String DEFAULT_LAYER_NAME = "dm4.geospatial.layer";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    SimplePointLayer layer;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ****************************
    // *** Hook Implementations ***
    // ****************************



    @Override
    public void init() {
        GraphDatabaseService neo4j = (GraphDatabaseService) dms.getDatabaseVendorObject();
        SpatialDatabaseService spatialDB = new SpatialDatabaseService(neo4j);
        //
        //spatialDB.deleteLayer(DEFAULT_LAYER_NAME, null);
        //
        if (spatialDB.containsLayer(DEFAULT_LAYER_NAME)) {
            logger.info("########## Default layer exists already (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = (SimplePointLayer) spatialDB.getLayer(DEFAULT_LAYER_NAME);
        } else {
            logger.info("########## Creating default layer (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = spatialDB.createSimplePointLayer(DEFAULT_LAYER_NAME);
        }
    }



    // ********************************
    // *** Listener Implementations ***
    // ********************************



    @Override
    public void postCreateTopic(Topic topic, ClientState clientState, Directives directives) {
        if (topic.getTypeUri().equals("dm4.geomaps.geo_coordinate")) {
            logger.info("########## Geo Coordinate created: " + topic);
            GeoCoordinate geoCoord = geoCoordinate(topic);
            logger.info("########## Indexing Geo Coordinate " + topic.getId() + " (long=" + geoCoord.lon +
                ", lat=" + geoCoord.lat + ")");
            layer.add(geoCoord.lon, geoCoord.lat);
        }
    }



    // ### not used
    public class GeoCoordinateEncoder extends AbstractGeometryEncoder {

        @Override
        public void encodeGeometryShape(Geometry geometry, PropertyContainer container) {    // abstract in AGE
            // ### TODO
            logger.info("########## TODO! ##########");
        }

        @Override
        public Geometry decodeGeometry(PropertyContainer container) {
            long geoCoordId = ((Node) container).getId();
            CompositeValue geoCoord = dms.getTopic(geoCoordId, true).getCompositeValue();  // fetchComposite=true
            Point point = new GeometryFactory().createPoint(new Coordinate(
                geoCoord.getDouble("dm4.geomaps.longitude"),
                geoCoord.getDouble("dm4.geomaps.latitude")
            ));
            logger.info("########## Indexing Geo Coordinate " + geoCoordId + " (long=" + point.getX() +
                ", lat=" + point.getY() + ")");
            return point;
        }
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
