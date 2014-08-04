package de.deepamehta.plugins.geospatial;

import de.deepamehta.plugins.geomaps.model.GeoCoordinate;

import de.deepamehta.core.CompositeValue;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.service.event.PostUpdateTopicListener;

import org.neo4j.graphdb.GraphDatabaseService;

import org.neo4j.gis.spatial.SimplePointLayer;
import org.neo4j.gis.spatial.SpatialDatabaseService;

// ### import com.vividsolutions.jts.geom.Coordinate;
// ### import com.vividsolutions.jts.geom.Geometry;
// ### import com.vividsolutions.jts.geom.GeometryFactory;
// ### import com.vividsolutions.jts.geom.Point;

import java.util.logging.Logger;



public class GeospatialPlugin extends PluginActivator implements PostCreateTopicListener, PostUpdateTopicListener {

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
            logger.info("########## Default layer already exists (\"" + DEFAULT_LAYER_NAME + "\")");
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
        indexIfGeoCoordinate(topic);
    }

    @Override
    public void postUpdateTopic(Topic topic, TopicModel newModel, TopicModel oldModel, ClientState clientState,
                                                                                       Directives directives) {
        indexIfGeoCoordinate(topic);
    }


    // ------------------------------------------------------------------------------------------------- Private Methods

    private void indexIfGeoCoordinate(Topic topic) {
        if (topic.getTypeUri().equals("dm4.geomaps.geo_coordinate")) {
            logger.info("########## Geo Coordinate created/updated: " + topic);
            GeoCoordinate geoCoord = geoCoordinate(topic);
            logger.info("########## Indexing Geo Coordinate " + topic.getId() + " (long=" + geoCoord.lon +
                ", lat=" + geoCoord.lat + ")");
            layer.add(geoCoord.lon, geoCoord.lat);
        }
    }

    // ### TODO: move to geomaps service?
    private GeoCoordinate geoCoordinate(Topic geoCoordTopic) {
        CompositeValue comp = geoCoordTopic.getCompositeValue();
        return new GeoCoordinate(
            comp.getDouble("dm4.geomaps.longitude"),
            comp.getDouble("dm4.geomaps.latitude")
        );
    }
}
