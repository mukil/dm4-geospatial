
DeepaMehta 4 Geospatial
=======================

A DeepaMehta 4 plugin that provides geospatial "Within Distance" queries.

DeepaMehta 4 is a platform for collaboration and knowledge management.  
<https://github.com/jri/deepamehta>


API
---

Java API:

    List<Topic> getTopicsWithinDistance(GeoCoordinate geoCoord, double maxDistanceInKm)

Returned is a list of Geo Coordinate topics (as defined in the Geomaps plugin).

REST API:

    GET /geospatial/<lon>,<lat>/distance/<km>

The response is an array of Geo Coordinate topics.

If you want include the Geo Coordinate topic's Longitude and Latitude child topics in the result as well append `?include_childs=true` to the request.


Example
-------

Java API:

    import de.deepamehta.plugins.geospatial.GeospatialService;
    import de.deepamehta.plugins.geomaps.GeomapsService;
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

**0.2.2-SNAPSHOT** -- CURRENT

* Compatible with DeepaMehta 4.7

**0.2.1-SNAPSHOT** -- UNRELEASED

* Much faster build of query-results (5-6 times faster).
  Query Example A: 0.5km radius with 31 results (700-800ms instead of 5000ms)
  Query Example B: 1.18km radius with 141 results (near 2600ms instead of 10200ms)
  Plugin maintains previous accuracy and its automatic (index) update-functionality.

**0.2** -- Oct 24, 2014

* Compatible with DeepaMehta 4.4

**0.1** -- Aug 10, 2014

* "Within Distance" query
* Based on Neo4j Spatial
* Compatible with DeepaMehta 4.4-SNAPSHOT

------------
Jörg Richter & Malte Reißig
Dec 11 , 2015
