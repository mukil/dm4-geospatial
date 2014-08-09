package de.deepamehta.plugins.geospatial;

import de.deepamehta.plugins.geospatial.service.GeospatialService;
import de.deepamehta.plugins.geomaps.model.GeoCoordinate;
import de.deepamehta.plugins.geomaps.service.GeomapsService;

import de.deepamehta.core.Topic;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.PluginService;
import de.deepamehta.core.service.annotation.ConsumesService;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.service.event.PostUpdateTopicListener;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.collections.rtree.NullListener;

import org.neo4j.gis.spatial.EditableLayer;
import org.neo4j.gis.spatial.EditableLayerImpl;
import org.neo4j.gis.spatial.SpatialDatabaseRecord;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.gis.spatial.pipes.GeoPipeFlow;
import org.neo4j.gis.spatial.pipes.GeoPipeline;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;



@Path("/geospatial")
@Consumes("application/json")
@Produces("application/json")
public class GeospatialPlugin extends PluginActivator implements GeospatialService, PointFactory,
                                                                                    PostCreateTopicListener,
                                                                                    PostUpdateTopicListener {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String DEFAULT_LAYER_NAME = "dm4.geospatial.default_layer";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private EditableLayer layer;

    private GeomapsService geomapsService;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ****************************************
    // *** GeospatialService Implementation ***
    // ****************************************



    @GET
    @Path("/{geo_coord}/distance/{distance}")
    @Override
    public List<Topic> getTopicsWithinDistance(@PathParam("geo_coord") GeoCoordinate geoCoord,
                                               @PathParam("distance") double maxDistanceInKm) {
        try {
            // query geospatial index
            Coordinate point = new Coordinate(geoCoord.lon, geoCoord.lat);
            GeoPipeline spatialRecords = GeoPipeline.startNearestNeighborLatLonSearch(layer, point, maxDistanceInKm);
                /* ### .sort("OrthodromicDistance") */
            //
            // build result
            List<Topic> geoCoords = new ArrayList();
            for (GeoPipeFlow spatialRecord : spatialRecords) {
                // Note: long distance = spatialRecord.getProperty("OrthodromicDistance")
                long geoCoordId = spatialRecord.getRecord().getNodeId();
                geoCoords.add(dms.getTopic(geoCoordId, true));  // fetchComposite=true
            }
            return geoCoords;
        } catch (Exception e) {
            throw new RuntimeException("Quering the geospatial index failed", e);
        }
    }



    // ***********************************
    // *** PointFactory Implementation ***
    // ***********************************



    @Override
    public Point createPoint(Topic geoCoordTopic) {
        GeoCoordinate geoCoord = geomapsService.geoCoordinate(geoCoordTopic);
        return layer.getGeometryFactory().createPoint(new Coordinate(geoCoord.lon, geoCoord.lat));
    }



    // ****************************
    // *** Hook Implementations ***
    // ****************************



    @Override
    public void init() {
        GraphDatabaseService neo4j = (GraphDatabaseService) dms.getDatabaseVendorObject();
        SpatialDatabaseService spatialDB = new SpatialDatabaseService(neo4j);
        //
        // spatialDB.deleteLayer(DEFAULT_LAYER_NAME, new NullListener());
        //
        if (spatialDB.containsLayer(DEFAULT_LAYER_NAME)) {
            logger.info("########## Default layer already exists (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = (EditableLayer) spatialDB.getLayer(DEFAULT_LAYER_NAME);
        } else {
            logger.info("########## Creating default layer (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = (EditableLayer) spatialDB.createLayer(DEFAULT_LAYER_NAME, GeoCoordinateEncoder.class,
                                                                              EditableLayerImpl.class);
        }
        //
        ((GeoCoordinateEncoder) layer.getGeometryEncoder()).init(this, dms);
    }

    // ---

    @Override
    @ConsumesService(GeomapsService.class)
    public void serviceArrived(PluginService service) {
        geomapsService = (GeomapsService) service;
    }

    @Override
    public void serviceGone(PluginService service) {
        geomapsService = null;
    }



    // ********************************
    // *** Listener Implementations ***
    // ********************************



    @Override
    public void postCreateTopic(Topic topic, ClientState clientState, Directives directives) {
        if (topic.getTypeUri().equals("dm4.geomaps.geo_coordinate")) {
            logger.info("########## A Geo Coordinate topic was created: " + topic);
            SpatialDatabaseRecord record = layer.add((Node) topic.getDatabaseVendorObject());
            logger.info("########## Geo Coordinate added to geospatial index (record ID=" + record.getId() + ".." +
                record.getNodeId() + ".." + record.getGeomNode().getId() + ")");
        }
    }

    @Override
    public void postUpdateTopic(Topic topic, TopicModel newModel, TopicModel oldModel, ClientState clientState,
                                                                                       Directives directives) {
        if (topic.getTypeUri().equals("dm4.geomaps.geo_coordinate")) {
            logger.info("########## A Geo Coordinate topic was updated: " + topic);
            layer.update(topic.getId(), createPoint(topic));
            logger.info("########## Geo Coordinate " + topic.getId() + " updated in geospatial index");
        }
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    // ### not used
    private Map nodeProperties(Node node) {
        Map props = new HashMap();
        for (String key : node.getPropertyKeys()) {
            props.put(key, node.getProperty(key));
        }
        return props;
    }
}
