package de.deepamehta.plugins.geospatial;

import de.deepamehta.plugins.geospatial.service.GeospatialService;
import de.deepamehta.plugins.geomaps.model.GeoCoordinate;
import de.deepamehta.plugins.geomaps.service.GeomapsService;
import de.deepamehta.core.RelatedTopic;
import de.deepamehta.core.Topic;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.ResultList;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.service.event.PostUpdateTopicListener;
import de.deepamehta.core.service.event.PreDeleteTopicListener;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;

import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.gis.spatial.EditableLayer;
import org.neo4j.gis.spatial.EditableLayerImpl;
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
                                                                                    PostUpdateTopicListener,
                                                                                    PreDeleteTopicListener {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String DEFAULT_LAYER_NAME = "dm4.geospatial.default_layer";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private EditableLayer layer;

    @Inject
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
            List<Topic> geoCoords = new ArrayList();    // the result
            //
            // logging
            int count = layer.getIndex().count();
            if (count == 0) {
                // Note: Neo4j Spatial throws a NullPointerException when searching an empty index
                logger.info("### Searching the geospatial index ABORTED -- index is empty");
                return geoCoords;
            } else {
                logger.info("### " + count + " entries in geospatial index");
            }
            //
            // search geospatial index
            Coordinate point = new Coordinate(geoCoord.lon, geoCoord.lat);
            GeoPipeline spatialRecords = GeoPipeline.startNearestNeighborLatLonSearch(layer, point, maxDistanceInKm);
                /* ### .sort("OrthodromicDistance") */
            //
            // build result
            for (GeoPipeFlow spatialRecord : spatialRecords) {
                // Note: long distance = spatialRecord.getProperty("OrthodromicDistance")
                long geoCoordId = spatialRecord.getRecord().getNodeId();
                geoCoords.add(dms.getTopic(geoCoordId));
            }
            return geoCoords;
        } catch (Exception e) {
            throw new RuntimeException("Searching the geospatial index failed", e);
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
        // IMPORTANT: deleting a Neo4j Spatial layer includes deleting the geometry nodes which are at the same time
        // our Geo Coordinate topics. This results in a corrupted DM database as associations with only 1 player remain.
        // --> Never delete a layer while Geo Coordinate topics exist!!!
        // spatialDB.deleteLayer(DEFAULT_LAYER_NAME, new NullListener());
        //
        boolean layerCreated = false;
        if (spatialDB.containsLayer(DEFAULT_LAYER_NAME)) {
            logger.info("### Default layer already exists (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = (EditableLayer) spatialDB.getLayer(DEFAULT_LAYER_NAME);
        } else {
            logger.info("### Creating default layer (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = (EditableLayer) spatialDB.createLayer(DEFAULT_LAYER_NAME, GeoCoordinateEncoder.class,
                                                                              EditableLayerImpl.class);
            layerCreated = true;
        }
        //
        ((GeoCoordinateEncoder) layer.getGeometryEncoder()).init(this, dms);
        //
        // initial indexing
        if (layerCreated) {
            indexAllGeoCoordinateTopics();
        }
    }



    // ********************************
    // *** Listener Implementations ***
    // ********************************



    @Override
    public void postCreateTopic(Topic topic) {
        if (topic.getTypeUri().equals("dm4.geomaps.geo_coordinate")) {
            logger.info("### Adding Geo Coordinate to geospatial index (" + topic + ")");
            addToIndex(topic);
        }
    }

    @Override
    public void postUpdateTopic(Topic topic, TopicModel newModel, TopicModel oldModel) {
        if (topic.getTypeUri().equals("dm4.geomaps.geo_coordinate")) {
            logger.info("### Updating Geo Coordinate " + topic.getId() + " in geospatial index");
            updateIndex(topic);
        }
    }

    // ---

    @Override
    public void preDeleteTopic(Topic topic) {
        if (topic.getTypeUri().equals("dm4.geomaps.geo_coordinate")) {
            logger.info("### Removing Geo Coordinate " + topic.getId() + " from geospatial index");
            removeFromIndex(topic);
        }
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    private void addToIndex(Topic geoCoord) {
        layer.add((Node) geoCoord.getDatabaseVendorObject());
    }

    private void updateIndex(Topic geoCoord) {
        layer.update(geoCoord.getId(), createPoint(geoCoord));
    }

    private void removeFromIndex(Topic geoCoord) {
        layer.removeFromIndex(geoCoord.getId());
    }

    // ---

    private void indexAllGeoCoordinateTopics() {
        DeepaMehtaTransaction tx = dms.beginTx();
        try {
            ResultList<RelatedTopic> geoCoords = dms.getTopics("dm4.geomaps.geo_coordinate", 0);
            logger.info("### Filling initial geospatial index with " + geoCoords.getSize() + " Geo Coordinates");
            for (Topic geoCoord : geoCoords) {
                addToIndex(geoCoord);
            }
            //
            tx.success();
        } catch (Exception e) {
            logger.warning("ROLLBACK!");
            throw new RuntimeException("Filling initial geospatial index failed", e);
        } finally {
            tx.finish();
        }
    }

    // ---

    // ### not used
    private Map nodeProperties(Node node) {
        Map props = new HashMap();
        for (String key : node.getPropertyKeys()) {
            props.put(key, node.getProperty(key));
        }
        return props;
    }
}
