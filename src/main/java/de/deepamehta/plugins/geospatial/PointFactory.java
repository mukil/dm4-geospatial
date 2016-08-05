package de.deepamehta.plugins.geospatial;


import com.vividsolutions.jts.geom.Point;



interface PointFactory {

    Point createPointByCoordinates(double longitude, double latitude);

}
