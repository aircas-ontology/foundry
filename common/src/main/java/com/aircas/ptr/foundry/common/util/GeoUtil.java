package com.aircas.ptr.foundry.common.util;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.io.WKTReader;
import org.geotools.data.FeatureSource;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureIterator;
import org.geotools.geojson.geom.GeometryJSON;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.Charset;

public class GeoUtil {

    private static final Logger LOG = LoggerFactory.getLogger(GeoUtil.class);

    // Geometry精度
    static int deciamls = 6;

    public static GeometryJSON WKT2GeometryJSON(String wkt) throws Exception {
        WKTReader reader = new WKTReader();
        Geometry geometry = reader.read(wkt);
        StringWriter writer = new StringWriter();
        GeometryJSON g = new GeometryJSON(deciamls);
        g.write(geometry, writer);
        return g;
    }

    public static String WKT2GeometryJSONString(String wkt) throws Exception {
        WKTReader reader = new WKTReader();
        Geometry geometry = reader.read(wkt);
        StringWriter writer = new StringWriter();
        GeometryJSON g = new GeometryJSON(deciamls);
        g.write(geometry, writer);
        return writer.toString();
    }

    public static Geometry WKTStr2WKT(String wkt) throws Exception {
        WKTReader reader = new WKTReader();
        return reader.read(wkt);
    }

    public static Geometry GeometryJSONStr2Geometry(String geoJson) {
        GeometryJSON json = new GeometryJSON();
        geoJson = geoJson.replace("polygon", "Polygon");
        geoJson = geoJson.replace("point", "Point");
        Geometry geometry = null;
        try {
            geometry = json.read(geoJson);
        } catch (IOException e) {
            LOG.error("GeometryJSON: {} 解析失败", geoJson);
        }
        return geometry;
    }

    public static Geometry GeometryJSON2WKT(String geoJson) throws Exception {
        GeometryJSON g = new GeometryJSON(deciamls);
        geoJson = geoJson.replace("polygon", "Polygon");
        geoJson = geoJson.replace("point", "Point");
        return g.read(geoJson);
    }

    public static Geometry Shp2Geometry(String path) throws Exception {
        File shpFile = new File(path);
        // 当shapefile文件不存在，不是文件格式或者不以shp结尾时
        if (!shpFile.exists() || !shpFile.isFile() || !shpFile.getName().endsWith(".shp")) {
            return null;
        }
        URL url = shpFile.toURI().toURL();
        ShapefileDataStore dataStore = (ShapefileDataStore) new ShapefileDataStoreFactory()
                .createDataStore(url);
        dataStore.setCharset(Charset.forName("UTF-8"));

        String typeName = dataStore.getTypeNames()[0];  //By default the first field is the geom

        FeatureSource<SimpleFeatureType, SimpleFeature> source =
                dataStore.getFeatureSource(typeName);
        Filter filter = Filter.INCLUDE;

        FeatureCollection<SimpleFeatureType, SimpleFeature> collection = source.getFeatures(filter);

        Geometry geometry = null;
        try (FeatureIterator<SimpleFeature> features = collection.features()) {
            while (features.hasNext()) {
                SimpleFeature feature = features.next();
                if (geometry == null) {
                    geometry = WKTStr2WKT(
                            feature.getDefaultGeometryProperty().getValue().toString());
                } else {
                    Geometry tmpGeometry = WKTStr2WKT(
                            feature.getDefaultGeometryProperty().getValue().toString());
                    geometry = geometry.union(tmpGeometry);
                }
            }
        }
        return geometry;
    }
}
