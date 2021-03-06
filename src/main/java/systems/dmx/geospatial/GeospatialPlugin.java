package systems.dmx.geospatial;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.gis.spatial.EditableLayerImpl;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.gis.spatial.pipes.GeoPipeFlow;
import org.neo4j.gis.spatial.pipes.GeoPipeline;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import java.io.IOException;
import java.nio.charset.Charset;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.Consumes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.ws.rs.core.Response;
import org.neo4j.gis.spatial.EditableLayer;
import org.neo4j.gis.spatial.Layer;
import org.neo4j.gis.spatial.ShapefileImporter;
import org.neo4j.gis.spatial.SpatialDatabaseRecord;
import org.neo4j.graphdb.NotFoundException;
import systems.dmx.accesscontrol.AccessControlService;
import systems.dmx.core.Topic;
import systems.dmx.core.model.TopicModel;
import systems.dmx.core.osgi.PluginActivator;
import systems.dmx.core.service.Inject;
import systems.dmx.core.service.Transactional;
import systems.dmx.core.service.accesscontrol.Operation;
import systems.dmx.core.service.event.PostCreateTopic;
import systems.dmx.core.service.event.PostUpdateTopic;
import systems.dmx.core.service.event.PreDeleteTopic;
import systems.dmx.core.storage.spi.DMXTransaction;
import systems.dmx.geomaps.GeoCoordinate;



@Path("/geospatial")
@Consumes("application/json")
@Produces("application/json")
public class GeospatialPlugin extends PluginActivator implements GeospatialService, PointFactory,
                                                                                    PostCreateTopic,
                                                                                    PostUpdateTopic,
                                                                                    PreDeleteTopic {

    // ------------------------------------------------------------------------------------------------------- Constants

    public static final String DEFAULT_POINT_LAYER_NAME     = "dmx.geospatial.default_layer";
    public static final String DEFAULT_GEOMETRY_LAYER_NAME  = "dmx.geospatial.default_geometry_layer";
    public static final String GEO_NODE_PROPERTY_ID         = "dmx.geospatial.geometry_node_id";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private EditableLayer pointLayer;
    private EditableLayer geometryLayer;

    @Inject private AccessControlService acService;

    private Logger logger = Logger.getLogger(getClass().getName());

    // -------------------------------------------------------------------------------------------------- Public Methods



    // ****************************************
    // *** GeospatialService Implementation ***
    // ****************************************


    /** 
     * Fetches all Geo Coordinate topics within a "given distance" (using neo4j-spatial).
     * @param geoCoord  Coordinate pair
     * @param maxDistanceInKm   The maximum distance (e.g 1.3) in kilometers between the given coordinate and a POI.
     * @return List Topics will be topics of type "dmx.geomaps.geo_coordinate"
     */
    @GET
    @Path("/{geo_coord}/distance/{distance}")
    @Override
    public List<Topic> getTopicsWithinDistance(@PathParam("geo_coord") GeoCoordinate geoCoord,
                                               @PathParam("distance") double maxDistanceInKm) {
        try {
            String username = acService.getUsername();
            List<Topic> geoCoords = new ArrayList();    // the result
            // logging
            int count = pointLayer.getIndex().count();
            if (count == 0) {
                // Note: Neo4j Spatial throws a NullPointerException when searching an empty index
                logger.info("### Searching the geospatial index ABORTED -- index is empty");
                return geoCoords;
            } else {
                logger.fine("### " + count + " entries in geospatial index");
            }
            // search geospatial index
            Coordinate point = new Coordinate(geoCoord.lon, geoCoord.lat);
            GeoPipeline spatialRecords = GeoPipeline.
                    startNearestNeighborLatLonSearch(pointLayer, point, maxDistanceInKm);
            // build result
            for (GeoPipeFlow spatialRecord : spatialRecords) {
                // Note: long distance = spatialRecord.getProperty("OrthodromicDistance")
                long geoCoordId = ( (Number) spatialRecord.getRecord().getProperty("topic_id")).longValue();
                if (dmx.getPrivilegedAccess().hasPermission(username, Operation.READ, geoCoordId)) {
                    geoCoords.add(dmx.getTopic(geoCoordId));
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

    /**
     * Fetches the name of the geometry (Feature) given a coordinate pair (WGS-84) located within
     * that feature (using neo4j-spatial shapefile).
     * @param coordinates   String lat,lng
     * @return String name
     */
    @GET
    @Path("/feature/{latlng}")
    public Response getGeometryFeatureName(@PathParam("latlng") String coordinates) {
        String name = getGeometryFeatureNameByCoordinate(coordinates);
        if (name != null) {
            return Response.ok(name).build();
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/set/geometry/{absoluteFilePath}")
    @Transactional
    @Override
    public Response setGeometryLayer(@PathParam("absoluteFilePath") String absoluteFile) {
        if (!acService.getUsername().equals(AccessControlService.ADMIN_USERNAME)) {
            throw new RuntimeException("Unauthorized to write default geometry layer to queries at /feature)");
        }
        GraphDatabaseService neo4j = (GraphDatabaseService) dmx.getDatabaseVendorObject();
        SpatialDatabaseService spatialDB = new SpatialDatabaseService(neo4j);
        logger.info("### Indexing new layer (\"" + DEFAULT_GEOMETRY_LAYER_NAME + "\")");
        try {
            if (spatialDB.containsLayer(DEFAULT_GEOMETRY_LAYER_NAME)) {
                geometryLayer = (EditableLayer) spatialDB.getLayer(DEFAULT_GEOMETRY_LAYER_NAME);
                spatialDB.deleteLayer(DEFAULT_GEOMETRY_LAYER_NAME,
                    new ProgressLoggingListener("Deleting layer '" + DEFAULT_GEOMETRY_LAYER_NAME + "'", logger));
                logger.info("### Removed previously stored geometry layer (\"" + DEFAULT_GEOMETRY_LAYER_NAME + "\")");
            }
            ShapefileImporter importer = new ShapefileImporter(neo4j);
            // The following line requires the "gt-api" module at compile time.
            importer.importFile(absoluteFile, DEFAULT_GEOMETRY_LAYER_NAME, Charset.forName("UTF-8"));
            logger.info("### Successfully wrote shapefile as default geometry layer (Filename: \""
                    + absoluteFile + "\") for queries at /feature");
            return Response.noContent().build();
        } catch (Exception ex) {
            logger.severe("IO/Error occured during indexing shapefile layer with neo4j-spatial");
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String getGeometryFeatureNameByCoordinate(@PathParam("latlng") String coordinates) {
        Object value = getGeometryFeatureValueByCoordinate(coordinates, "Name");
        return (String) value;
    }

    @Override
    public Object getGeometryFeatureValueByCoordinate(@PathParam("latlng") String coordinates, String key) {
        GraphDatabaseService neo4j = (GraphDatabaseService) dmx.getDatabaseVendorObject();
        SpatialDatabaseService spatialDB = new SpatialDatabaseService(neo4j);
        try {
            String[] latLng = coordinates.split(",");
            if (spatialDB.containsLayer(DEFAULT_GEOMETRY_LAYER_NAME)) {
                geometryLayer = (EditableLayer) spatialDB.getLayer(DEFAULT_GEOMETRY_LAYER_NAME);
                logger.info("### Inspecting layer (\"" + DEFAULT_GEOMETRY_LAYER_NAME + "\"), by "
                        + "coordinate: " + coordinates);
                // countGeometryIndex(DEFAULT_GEOMETRY_LAYER_NAME);
            } else {
                throw new RuntimeException("Geometry layer does not exist");
            }
            GeometryFactory geometryFactory = new GeometryFactory();
            Coordinate coordinate = new Coordinate(Double.parseDouble(latLng[1]), Double.parseDouble(latLng[0]));
            Point point = geometryFactory.createPoint(coordinate);
            GeoPipeline spatialRecords = GeoPipeline.start(geometryLayer);
            // GeoPipeline spatialRecords = GeoPipeline.startWithinSearch(geometryLayer, pointGeo);
            for (GeoPipeFlow spatialRecord : spatialRecords) {
                SpatialDatabaseRecord entry = spatialRecord.getRecord();
                Geometry geometry = entry.getGeometry();
                if (point.within(geometry)) {
                    Object value = getGeometryAttribute(entry, key);
                    logger.info("Found Point \"" + point + "\" is WITHIN => " +  value);
                    // inspectGeometryAttributes(entry);
                    return value;
                }
            }
            logger.info("No geometry found matching the coordinates");
            return null;
        } catch (RuntimeException ex) {
            logger.severe("Error occured during inspecting of geometry layer");
            throw new RuntimeException(ex);
        }
    }

    // ***********************************
    // *** PointFactory Implementation ***
    // ***********************************



    @Override
    public Point createPointByCoordinates(double lon, double lat) {
        return pointLayer.getGeometryFactory().createPoint(new Coordinate(lon, lat));
    }


    // ****************************
    // *** Hook Implementations ***
    // ****************************



    @Override
    public void init() {
        GraphDatabaseService neo4j = (GraphDatabaseService) dmx.getDatabaseVendorObject();
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
        if (spatialDB.containsLayer(DEFAULT_POINT_LAYER_NAME)) {
            logger.info("### Default geometry layer initialized, already exists. (\"" + DEFAULT_POINT_LAYER_NAME + "\")");
            pointLayer = (EditableLayer) spatialDB.getLayer(DEFAULT_POINT_LAYER_NAME);
        } else {
            logger.info("### Creating geometry layer (\"" + DEFAULT_POINT_LAYER_NAME + "\")");
            pointLayer = (EditableLayer) spatialDB.createLayer(DEFAULT_POINT_LAYER_NAME, GeoCoordinateEncoder.class,
                                                                              EditableLayerImpl.class);
            layerCreated = true;
        }
        if (spatialDB.containsLayer(DEFAULT_GEOMETRY_LAYER_NAME)) {
            logger.info("### Default geometry layer initialized (already exists). (\"" + DEFAULT_GEOMETRY_LAYER_NAME + "\")");
            geometryLayer = (EditableLayer) spatialDB.getLayer(DEFAULT_GEOMETRY_LAYER_NAME);
        }
        //
        ((GeoCoordinateEncoder) pointLayer.getGeometryEncoder()).init(this, dmx);
        // initial indexing
        if (layerCreated) {
            indexAllGeoCoordinateTopics();
        }
    }

    @Override
    public boolean validWGS84Coordinates(GeoCoordinate coords) {
        return ((coords.lat < 91 && coords.lat > -91)
            && (coords.lon < 91 && coords.lon > -91));
    }

    // ********************************
    // *** Listener Implementations ***
    // ********************************



    @Override
    public void postCreateTopic(Topic topic) {
        if (topic.getTypeUri().equals("dmx.geomaps.geo_coordinate")) {
            logger.info("### Adding Geo Coordinate to geospatial index (" + topic.getId() + ")");
            addToIndex(topic.loadChildTopics());
        }
    }

    @Override
    public void postUpdateTopic(Topic topic, TopicModel newModel, TopicModel oldModel) {
        if (topic.getTypeUri().equals("dmx.geomaps.geo_coordinate")) {
            logger.info("### Updating Geo Coordinate " + topic.getId() + " in geospatial index");
            updateIndex(topic.loadChildTopics());
        }
    }

    // ---

    @Override
    public void preDeleteTopic(Topic topic) {
        if (topic.getTypeUri().equals("dmx.geomaps.geo_coordinate")) {
            logger.info("### Removing Geo Coordinate " + topic.getId() + " from geospatial index");
            removeFromIndex(topic);
        }
    }



    // ------------------------------------------------------------------------------------------------- Private Methods

    private Object getGeometryAttribute(SpatialDatabaseRecord entry, String attributeKey) {
        try {
            return entry.getProperties().get(attributeKey);
        } catch (RuntimeException re) {
            logger.severe("Could not find an attribute named \"" + attributeKey
                + "\" at geospatial entry " + entry.toString());
        }
        return null;
    }

    /** Useful for inspecting geometries, not in use by default. **/
    private void inspectGeometryAttributes(SpatialDatabaseRecord entry) {
        logger.info("=> Inspecting spatial database entry => " +  entry.getId());
        Map<String, Object> props = entry.getProperties();
        for (String key : props.keySet()) {
            logger.info("=> Attribute \""+key+"\": \"" +  props.get(key).toString() + "\"");
        }
    }

    /** Useful for inspecting geometries, not in use by default. **/
    private void countGeometryIndex(String layerName) throws IOException {
        GraphDatabaseService neo4j = (GraphDatabaseService) dmx.getDatabaseVendorObject();
        SpatialDatabaseService spatial = new SpatialDatabaseService(neo4j);
        Layer layer = spatial.getLayer(layerName);
        if (layer.getIndex().count() < 1) {
            logger.warning("Checking Layer Warning: index count zero: " + layer.getName());
        }
        logger.info("Checking Layer: '" + layer.getName() + "' has " + layer.getIndex().count() + " entries in the index");
        /** DataStore store = new Neo4jSpatialDataStoreFactory();
        SimpleFeatureCollection features = store.getFeatureSource(layer.getName()).getFeatures();
        logger.info("Checking Layer: '" + layer.getName() + "' has " + features.size() + " features"); **/
    }

    private void addToIndex(Topic geoCoord) {
        // Old: layer.add((Node) geoCoord.getDatabaseVendorObject()); // this triggers a decodeGeom before encoding
        geoCoord.loadChildTopics();
        double longitude = geoCoord.getChildTopics().getDouble("dmx.geomaps.longitude");
        double latitude = geoCoord.getChildTopics().getDouble("dmx.geomaps.latitude");
        // Note 1: we store the reference to the geo coordinate topic (id) in the geometry node to assemble the
        // resulting list of topics after a spatial query
        String propertyKeys[] = { "topic_id" };
        Object values[] = { geoCoord.getId() };
        // New: We directly (and just) trigger encodeGeometryShape (no decodeGeom is called in our AGE before initial 
        //      encoding) via not using layer.add(Node) but layer.add(Geometry).
        SpatialDatabaseRecord sr = pointLayer.add(createPointByCoordinates(longitude, latitude), propertyKeys, values);
        // Note 2: we store a reference to the geometry node in a dmx-node property to easify alteration of index
        geoCoord.setProperty(GEO_NODE_PROPERTY_ID, sr.getGeomNode().getId(), true);
    }

    private void updateIndex(Topic geoCoord) {
        // get updated values
        geoCoord.loadChildTopics();
        double longitude = geoCoord.getChildTopics().getDouble("dmx.geomaps.longitude");
        double latitude = geoCoord.getChildTopics().getDouble("dmx.geomaps.latitude");
        // update indexed geometry node
        try {
            long nodeId = ( (Number) geoCoord.getProperty(GEO_NODE_PROPERTY_ID)).longValue();
            pointLayer.update(nodeId, createPointByCoordinates(longitude, latitude));
        } catch (NotFoundException nfe) {
            logger.severe("### Geo Coordinate (id="+geoCoord.getId()+") has no geometry node id set/indexed as property"
                + " - Spatial index layer can not be updated");
        }
    }

    private void removeFromIndex(Topic geoCoord) {
        try {
            long nodeId = ( (Number) geoCoord.getProperty(GEO_NODE_PROPERTY_ID)).longValue();
            pointLayer.removeFromIndex(nodeId);
        } catch (NotFoundException nfe) {
            logger.severe("### Geo Coordinate (id="+geoCoord.getId()+") has no geometry node id set/indexed as property"
                + "- Node can not be removed from spatial index layer");
        }
    }

    // ---

    private void indexAllGeoCoordinateTopics() {
        DMXTransaction tx = dmx.beginTx();
        try {
            List<Topic> geoCoords = dmx.getTopicsByType("dmx.geomaps.geo_coordinate");
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
