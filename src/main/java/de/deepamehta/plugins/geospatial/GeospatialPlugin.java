package de.deepamehta.plugins.geospatial;

import de.deepamehta.plugins.geospatial.service.GeospatialService;
import de.deepamehta.plugins.geomaps.model.GeoCoordinate;

import de.deepamehta.core.CompositeValue;
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

import org.neo4j.gis.spatial.SimplePointLayer;
import org.neo4j.gis.spatial.SpatialDatabaseRecord;
import org.neo4j.gis.spatial.SpatialDatabaseService;
import org.neo4j.gis.spatial.pipes.GeoPipeFlow;
import org.neo4j.gis.spatial.pipes.GeoPipeline;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Point;
// ### import com.vividsolutions.jts.geom.Geometry;
// ### import com.vividsolutions.jts.geom.GeometryFactory;

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

    private static final String DEFAULT_LAYER_NAME = "dm4.geospatial.layer";

    private static final String PROP_GEO_COORD_ID = "geo_coord_id";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    SimplePointLayer layer;

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
                long geoCoordId = (Long) spatialRecord.getRecord().getGeomNode().getProperty(PROP_GEO_COORD_ID, -1L);
                if (geoCoordId == -1) {
                    Point p = (Point) spatialRecord.getGeometry();
                    throw new RuntimeException("A spatial database record misses the \"" + PROP_GEO_COORD_ID +
                        "\" property (lon=" + p.getX() + ", lat=" + p.getY() + ")");
                }
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
            SpatialDatabaseRecord record = layer.add(geoCoord.lon, geoCoord.lat);
            record.setProperty(PROP_GEO_COORD_ID, topic.getId());
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

    // ### not used
    private Map nodeProperties(Node node) {
        Map props = new HashMap();
        for (String key : node.getPropertyKeys()) {
            props.put(key, node.getProperty(key));
        }
        return props;
    }
}
