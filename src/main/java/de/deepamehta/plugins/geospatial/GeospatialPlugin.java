package de.deepamehta.plugins.geospatial;

import de.deepamehta.core.osgi.PluginActivator;
import de.deepamehta.core.service.DeepaMehtaService;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.gis.spatial.SpatialDatabaseService;

import java.util.logging.Logger;



public class GeospatialPlugin extends PluginActivator {

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
        SpatialDatabaseService spatialService = new SpatialDatabaseService(neo4j);
    }
}
