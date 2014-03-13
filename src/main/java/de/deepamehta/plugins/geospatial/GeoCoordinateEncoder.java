package de.deepamehta.plugins.geospatial;

import org.neo4j.graphdb.PropertyContainer;

import org.neo4j.gis.spatial.AbstractGeometryEncoder;

import com.vividsolutions.jts.geom.Geometry;



public class GeoCoordinateEncoder extends AbstractGeometryEncoder {

    @Override
    public void encodeGeometryShape(Geometry geometry, PropertyContainer container) {    // abstract in AGE
        // ### TODO
    }

    @Override
    public Geometry decodeGeometry(PropertyContainer container) {
        // ### TODO
        return null;
    }
}
