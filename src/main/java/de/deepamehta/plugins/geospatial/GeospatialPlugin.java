package de.deepamehta.plugins.geospatial;

import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.DeepaMehtaService;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.gis.spatial.EditableLayerImpl;
import org.neo4j.gis.spatial.Layer;
import org.neo4j.gis.spatial.SpatialDatabaseService;

import java.util.logging.Logger;



public class GeospatialPlugin extends PluginActivator {

    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String DEFAULT_LAYER_NAME = "dm4.geospatial.layer";

    // ---------------------------------------------------------------------------------------------- Instance Variables

    private GraphDatabaseService neo4j;

    private Logger logger = Logger.getLogger(getClass().getName());

    // ---------------------------------------------------------------------------------------------------- Constructors



    // ****************************
    // *** Hook Implementations ***
    // ****************************



    @Override
    public void init() {
        GraphDatabaseService neo4j = (GraphDatabaseService) dms.getDatabaseVendorObject();
        SpatialDatabaseService spatialDB = new SpatialDatabaseService(neo4j);
        //
        Layer layer;
        if (spatialDB.containsLayer(DEFAULT_LAYER_NAME)) {
            logger.info("########## Default layer exists already (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = spatialDB.getLayer(DEFAULT_LAYER_NAME);
        } else {
            logger.info("########## Creating default layer (\"" + DEFAULT_LAYER_NAME + "\")");
            layer = spatialDB.createLayer(DEFAULT_LAYER_NAME, GeoCoordinateEncoder.class, EditableLayerImpl.class);
        }
    }
}
