package de.deepamehta.plugins.geospatial.migrations;

import de.deepamehta.core.Topic;
import de.deepamehta.core.service.Migration;
import static de.deepamehta.plugins.geospatial.GeospatialPlugin.GEO_NODE_PROPERTY_ID;
import java.util.List;

import java.util.logging.Logger;
import org.neo4j.graphdb.NotFoundException;


/**
 * Fix geometry node id property indexes on all geo coordinate topics. Does not alter any data values, just indexes.
 * Note: This migration is just needed to clean up a very specific 0.2.2-SNAPSHOT database in existence.
 */
public class Migration1 extends Migration {

    private Logger log = Logger.getLogger(getClass().getName());

    @Override
    public void run() {
        log.warning("### Geospatial Migration1: Start fixing geometry node id property indexes on coordinate topics.");
        List<Topic> geoCoordinates = dm4.getTopicsByType("dm4.geomaps.geo_coordinate");
        for (Topic coordinate : geoCoordinates) {
            try {
                long nodeId = ( (Number) coordinate.getProperty(GEO_NODE_PROPERTY_ID)).longValue();
                coordinate.setProperty(GEO_NODE_PROPERTY_ID, nodeId, true); // re-set node id but with index=true
            } catch (NotFoundException nfe) {
                log.warning("### Geo Coordinate (id="+coordinate.getId()+") has no geometry node id set/indexed as property"
                    + " - Spatial indexed topic can not be fixed");
            }
        }
        log.warning("### Geospatial Migration1: Completed fixing property indexes on all geo coordinate topics.");

    }
}