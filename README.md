
DeepaMehta 4 Geospatial
=======================

A DeepaMehta 4 plugin that provides geospatial "Within Distance" queries.

DeepaMehta 4 is a platform for collaboration and knowledge management.  
<https://github.com/jri/deepamehta>


API
---

Java API:

    List<Topic> getTopicsWithinDistance(GeoCoordinate geoCoord, double maxDistanceInKm)

REST API:

    GET /geospatial/<lon>,<lat>/distance/<km>

Returned are Geo Coordinate topics (as defined in the Geomaps plugin).


Example
-------

    import de.deepamehta.plugins.geospatial.service.GeospatialService;
    import de.deepamehta.plugins.geomaps.service.GeomapsService;
    import de.deepamehta.plugins.geomaps.model.GeoCoordinate;

    GeospatialService geospatialService;
    GeomapsService geomapsService;

    List<Topic> geoCoordTopics = geospatialService.getTopicsWithinDistance(new GeoCoordinate(13.4, 52.5), 10.0);
    for (Topic geoCoordTopic : geoCoordTopics) {
        GeoCoordinate geoCoord = geomapsService.geoCoordinate(geoCoordTopic);
        double lon = geoCoord.lon;
        double lat = geoCoord.lat;
    }
    

Version History
---------------

**0.1** -- Aug 10, 2014

* "Within Distance" query
* Based on Neo4j Spatial
* Compatible with DeepaMehta 4.4-SNAPSHOT


------------
Jörg Richter  
Aug 10, 2014
