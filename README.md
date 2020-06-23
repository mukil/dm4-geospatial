
DMX Geospatial
=======================

A DMX plugin that provides geospatial "Within Distance" queries.

DMX is a platform for collaboration and knowledge management.  
<https://git.dmx.sytems/dmx-platform/dmx-platform>


API
---

Java API (Within Distance Example):

    List<Topic> getTopicsWithinDistance(GeoCoordinate geoCoord, double maxDistanceInKm)

Returned is a list of Geo Coordinate topics (as defined in the Geomaps plugin).

Java API (Query Shapefile Properties by Geo Coordinates):

    Object `getGeometryFeatureValueByCoordinate(String coordinates, String key)`

REST API (Within Distance Example):

    GET /geospatial/<lon>,<lat>/distance/<km>

The response is an array of Geo Coordinate topics.

If you want include the Geo Coordinate topic's Longitude and Latitude child topics in the result as well append `?include_children=true` to the request.

REST API Get Feature Name:

    `/feature/{latlng}`

The response is the `Name` property of the geometry's `Feature` in which a given coordinate pair (WGS-84) is located (using neo4j-spatial shapefile).




Example (Within Distance)
-------------------------

Java API:

    import systems.dmx.geospatial.GeospatialService;
    import systems.dmx.geomaps.GeomapsService;
    import systems.dmx.geomaps.model.GeoCoordinate;

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

**0.5** -- Upcoming

* Compatible with DMX 5.0 API
* No support for database upgrades for geometry layers in deepamehta-db's (need to re-index)
* Released under the AGPL-3.0 License

**0.4** -- Mar 30, 2018

* Inroduced shapefile-layer capabilities
* Extended service to query shapefile for key, values by geo coordinate
* Compatible with DeepaMehta 4.9.x

**0.3** -- Aug 05, 2016

* Compatible with DeepaMehta 4.8
* Checks READ permissions for the requesting user automatically<br/>
  while assembling the resulting set of geo coordinate topics
* Much faster build of query-results (5-6 times faster).<br/>
  Query Example A: 0.5km radius with 31 results (700-800ms instead of 5000ms)<br/>
  Query Example B: 1.18km radius with 141 results (near 2600ms instead of 10200ms)<br/>
  Plugin maintains previous accuracy and its automatic (index) update-functionality.

Note: This release is not compatible with one of the previous releases. If you have a deepamehta database with a spatial index created with version `0.2` or `0.1` of this plugin please contact us and we will most probably find a way to upgrade your data.

**0.2** -- Oct 24, 2014

* Compatible with DeepaMehta 4.4

**0.1** -- Aug 10, 2014

* "Within Distance" query
* Based on Neo4j Spatial
* Compatible with DeepaMehta 4.4-SNAPSHOT

Licensing
---------

DMX Geospatial software is available freely under the GNU Affero General Public License, version 3.

All third party components incorporated into the DMX Geospatial Software are licensed under the original license provided by the owner of the applicable component.


Copyright
---------
Copyright (C) 2016 Jörg Richter<br/>
Copyright (C) 2017-2018 Malte Reißig<br/>
Copyright (C) 2019 DMX Systems<br/>
