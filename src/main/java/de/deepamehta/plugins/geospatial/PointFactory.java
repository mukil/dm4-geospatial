package de.deepamehta.plugins.geospatial;

import de.deepamehta.core.Topic;

import com.vividsolutions.jts.geom.Point;



interface PointFactory {

    Point createPoint(Topic geoCoordTopic);
}
