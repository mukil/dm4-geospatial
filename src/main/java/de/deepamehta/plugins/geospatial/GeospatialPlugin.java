package de.deepamehta.plugins.geospatial;

import de.deepamehta.core.Topic;
import de.deepamehta.core.model.TopicModel;
import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.Inject;
import de.deepamehta.core.service.event.PostCreateTopicListener;
import de.deepamehta.core.service.event.PostUpdateTopicListener;
import de.deepamehta.core.service.event.PreDeleteTopicListener;
import de.deepamehta.core.storage.spi.DeepaMehtaTransaction;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.gis.spatial.EditableLayerImpl;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.gis.spatial.pipes.GeoPipeFlow;
import org.neo4j.gis.spatial.pipes.GeoPipeline;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;
import de.deepamehta.accesscontrol.AccessControlService;
import de.deepamehta.core.service.accesscontrol.Operation;
import de.deepamehta.geomaps.GeomapsService;
import de.deepamehta.geomaps.model.GeoCoordinate;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.neo4j.gis.spatial.EditableLayer;
import org.neo4j.gis.spatial.SpatialDatabaseRecord;
import org.neo4j.graphdb.NotFoundException;



@Path("/geospatial")
@Consumes("application/json")
@Produces("application/json")
public class GeospatialPlugin extends PluginActivator implements GeospatialService, PointFactory,
                                                                                    PostCreateTopicListener,
                                                                                    PostUpdateTopicListener,
                                                                                    PreDeleteTopicListener {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String DEFAULT_LAYER_NAME      = "dm4.geospatial.default_layer";
    private static final String GEO_NODE_PROPERTY_ID    = "dm4.geospatial.geometry_node_id";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private EditableLayer layer;
    @Inject private AccessControlService acService;

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
            String username = acService.getUsername();
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
                long geoCoordId = ( (Number) spatialRecord.getRecord().getProperty("topic_id")).longValue();
                if (dm4.getAccessControl().hasPermission(username, Operation.READ, geoCoordId)) {
                    geoCoords.add(dm4.getTopic(geoCoordId));
                } else {
                    logger.fine("Skipped to load geo coordinate topic, cause user (\""+username+"\") has no READ"
                        + " permission for topci id");
                }
            }
            logger.info("Found " + geoCoords.size() + " items nearby the given parameters.");
            return geoCoords;
        } catch (Exception e) {
            throw new RuntimeException("Searching the geospatial index failed", e);
        }
    }



    // ***********************************
    // *** PointFactory Implementation ***
    // ***********************************



    @Override
    public Point createPointByCoordinates(double lon, double lat) {
        return layer.getGeometryFactory().createPoint(new Coordinate(lon, lat));
    }


    // ****************************
    // *** Hook Implementations ***
    // ****************************



    @Override
    public void init() {
        GraphDatabaseService neo4j = (GraphDatabaseService) dm4.getDatabaseVendorObject();
        SpatialDatabaseService spatialDB = new SpatialDatabaseService(neo4j);
        //
        // IMPORTANT: deleting a Neo4j Spatial layer includes deleting the geometry nodes which are at the same time
        // our Geo Coordinate topics. This results in a corrupted DM database as associations with only 1 player remain.
        // --> Never delete a layer while Geo Coordinate topics exist!!!
        // spatialDB.deleteLayer(DEFAULT_LAYER_NAME, new NullListener());
        // UPDATE: As of 0.2.2 this should not play a role because the geometry nodes are not Geo Coordinate
        // topics anymore but are extra neo4j nodes. ### Test and confirm this. Deleting spatial index will not corrupt
        // the DeepaMehta database anymore.
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
        ((GeoCoordinateEncoder) layer.getGeometryEncoder()).init(this, dm4);
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
            logger.info("### Adding Geo Coordinate to geospatial index (" + topic.getId() + ")");
            addToIndex(topic.loadChildTopics());
        }
    }

    @Override
    public void postUpdateTopic(Topic topic, TopicModel newModel, TopicModel oldModel) {
        if (topic.getTypeUri().equals("dm4.geomaps.geo_coordinate")) {
            logger.info("### Updating Geo Coordinate " + topic.getId() + " in geospatial index");
            updateIndex(topic.loadChildTopics());
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
        // Old: layer.add((Node) geoCoord.getDatabaseVendorObject()); // this triggers a decodeGeom before encoding
        geoCoord.loadChildTopics();
        double longitude = geoCoord.getChildTopics().getDouble("dm4.geomaps.longitude");
        double latitude = geoCoord.getChildTopics().getDouble("dm4.geomaps.latitude");
        // Note 1: we store the reference to the geo coordinate topic (id) in the geometry node to assbmel the
        // resulting list of topics after a spatial query
        String propertyKeys[] = { "topic_id" };
        Object values[] = { geoCoord.getId() };
        // New: We directly (and just) trigger encodeGeometryShape (no decodeGeom is called in our AGE before initial 
        //      encoding) via not using layer.add(Node) but layer.add(Geometry).
        SpatialDatabaseRecord sr = layer.add(createPointByCoordinates(longitude, latitude), propertyKeys, values);
        // Note 2: we store a reference to the geometry node in a dm4-node property to easify alteration of index
        geoCoord.setProperty(GEO_NODE_PROPERTY_ID, sr.getGeomNode().getId(), true);
    }

    private void updateIndex(Topic geoCoord) {
        // get updated values
        geoCoord.loadChildTopics();
        double longitude = geoCoord.getChildTopics().getDouble("dm4.geomaps.longitude");
        double latitude = geoCoord.getChildTopics().getDouble("dm4.geomaps.latitude");
        // update indexed geometry node
        try {
            long nodeId = ( (Number) geoCoord.getProperty(GEO_NODE_PROPERTY_ID)).longValue();
            layer.update(nodeId, createPointByCoordinates(longitude, latitude));
        } catch (NotFoundException nfe) {
            logger.severe("### Geo Coordinate (id="+geoCoord.getId()+") has no geometry node id set/indexed as property"
                + " - Spatial index layer can not be updated");
        }
    }

    private void removeFromIndex(Topic geoCoord) {
        try {
            long nodeId = ( (Number) geoCoord.getProperty(GEO_NODE_PROPERTY_ID)).longValue();
            layer.removeFromIndex(nodeId);
        } catch (NotFoundException nfe) {
            logger.severe("### Geo Coordinate (id="+geoCoord.getId()+") has no geometry node id set/indexed as property"
                + "- Node can not be removed from spatial index layer");
        }
    }

    // ---

    private void indexAllGeoCoordinateTopics() {
        DeepaMehtaTransaction tx = dm4.beginTx();
        try {
            List<Topic> geoCoords = dm4.getTopicsByType("dm4.geomaps.geo_coordinate");
            logger.info("### Filling initial geospatial index with " + geoCoords.size() + " Geo Coordinates");
            for (Topic geoCoord : geoCoords) {
                addToIndex(geoCoord.loadChildTopics());
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

}
