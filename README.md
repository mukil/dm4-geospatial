
DeepaMehta 4 Geospatial
=======================

A DeepaMehta 4 plugin that provides geospatial "Within Distance" queries.

DeepaMehta 4 is a platform for collaboration and knowledge management.  
<https://github.com/jri/deepamehta>


Usage
-----

Java API:

    import de.deepamehta.plugins.geospatial.service.GeospatialService;
    import de.deepamehta.plugins.geomaps.model.GeoCoordinate;

    List<Topic> getTopicsWithinDistance(GeoCoordinate geoCoord, double maxDistanceInKm)

REST API:

    GET /geospatial/<lon>,<lat>/distance/<km>

Returned are Geo Coordinate topics (as defined in the Geomaps plugin).


Version History
---------------

**0.1** -- Jan 8, 2014

* "Within Distance" query
* Based on Neo4j Spatial
* Compatible with DeepaMehta 4.4-SNAPSHOT


------------
Jörg Richter  
Aug 10, 2014
