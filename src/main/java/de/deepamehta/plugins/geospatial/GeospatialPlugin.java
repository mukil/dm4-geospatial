package de.deepamehta.plugins.geospatial;

import de.deepamehta.plugins.geospatial.service.GeospatialService;
import de.deepamehta.plugins.geomaps.model.GeoCoordinate;

import de.deepamehta.core.Topic;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.ClientState;
import de.deepamehta.core.service.Directives;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.service.event.PostUpdateTopicListener;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.collections.rtree.NullListener;

import org.neo4j.gis.spatial.EditableLayerImpl;
import org.neo4j.gis.spatial.Layer;
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
public class GeospatialPlugin extends PluginActivator implements GeospatialService, PostCreateTopicListener,
                                                                                    PostUpdateTopicListener {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String DEFAULT_LAYER_NAME = "dm4.geospatial.default_layer";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    Layer layer;

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
            layer = spatialDB.getLayer(DEFAULT_LAYER_NAME);
        } else {
            logger.info("########## Creating default layer (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = spatialDB.createLayer(DEFAULT_LAYER_NAME, GeoCoordinateEncoder.class, EditableLayerImpl.class);
        }
        //
        ((GeoCoordinateEncoder) layer.getGeometryEncoder()).init(layer, dms);
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
            logger.info("########## Adding created/updated Geo Coordinate to geospatial index: " + topic);
            SpatialDatabaseRecord record = layer.add((Node) topic.getDatabaseVendorObject());
            logger.info("########## Geo Coordinate added to geospatial index (record ID=" + record.getId() + ".." +
                record.getNodeId() + ".." + record.getGeomNode().getId() + ")");
        }
    }

    // ### not used
    private Map nodeProperties(Node node) {
        Map props = new HashMap();
        for (String key : node.getPropertyKeys()) {
            props.put(key, node.getProperty(key));
        }
        return props;
    }
}
