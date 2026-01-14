package com.aircas.ptr.foundry.ontology.utils;//package com.aircas.ptr.foundry.ontology.common.util;
//
//import com.aircas.ptr.foundry.common.base.ApiResult;
//import org.apache.commons.collections4.ListUtils;
//import org.apache.commons.lang3.StringUtils;
//import org.elasticsearch.common.geo.builders.CoordinatesBuilder;
//import org.elasticsearch.common.geo.builders.MultiPolygonBuilder;
//import org.elasticsearch.common.geo.builders.PolygonBuilder;
//import org.elasticsearch.common.unit.DistanceUnit;
//import org.elasticsearch.index.query.BoolQueryBuilder;
//import org.elasticsearch.index.query.GeoShapeQueryBuilder;
//import org.elasticsearch.index.query.QueryBuilders;
//import org.elasticsearch.index.query.RangeQueryBuilder;
//import org.elasticsearch.rest.RestStatus;
//import org.elasticsearch.search.sort.SortBuilder;
//import org.elasticsearch.search.sort.SortBuilders;
//import org.elasticsearch.search.sort.SortOrder;
//import org.locationtech.jts.geom.Coordinate;
//import org.locationtech.jts.geom.Geometry;
//import org.locationtech.jts.io.WKTReader;
//
//import java.io.IOException;
//import java.util.List;
//
//public interface ESHelper {
//
//    //时间范围
//    static void timeRange(BoolQueryBuilder builder, String field, String begin, String end, String pattern) {
//        if (StringUtils.isNotBlank(begin) || StringUtils.isNotBlank(end)) {
//            RangeQueryBuilder temp = QueryBuilders.rangeQuery(field).format(pattern);
//            if (StringUtils.isNotBlank(begin)) {
//                temp.from(begin);
//            }
//            if (StringUtils.isNotBlank(end)) {
//                temp.to(end);
//            }
//            builder.must(temp);
//        }
//    }
//
//
//    //坐标点搜索
//    static void pointInclude(BoolQueryBuilder builder, String field, Double lonValue, Double latValue) {
//        if (lonValue == null || latValue == null) {
//            return;
//        }
//        try {
//            builder.must(
////                    GeoShapeQueryBuilder
//
//                    QueryBuilders.geoWithinQuery(field,
//
//                            ShapeBuilders.newPoint(lonValue, latValue))
//            );
////            builder.must(
////                    QueryBuilders.geoWithinQuery(field,
////                            ShapeBuilders.newPoint(lonValue, latValue))
////            );
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    //圆形搜索
//    static void circleRange(BoolQueryBuilder builder, String field, Double centralLon, Double centralLat, Double radius) {
//        if (centralLon == null || centralLat == null || radius == null) {
//            return;
//        }
//        try {
//            builder.must(
//                    QueryBuilders.geoIntersectionQuery(field,
//                            ShapeBuilders.newCircleBuilder()
//                                    .center(centralLon, centralLat)
//                                    .radius(radius, DistanceUnit.METERS))
//            );
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    static List<Coordinate> generateCoordinates(Double[] lons, Double[] lats) {
//        CoordinatesBuilder coordinatesBuilder = new CoordinatesBuilder();
//        for (int i = 0; i < lons.length; i++) {
//            coordinatesBuilder.coordinate(lons[i], lats[i]);
//        }
//        coordinatesBuilder.coordinate(lons[0], lats[0]);
//        return coordinatesBuilder.build();
//    }
//
//    //多边形搜索
//    static void polygonRange(BoolQueryBuilder builder, String field, Double[] lon, Double[] lat) {
//        if (lon == null || lon.length < 3 || lat == null || lat.length < 3 || lon.length != lat.length) {
//            return;
//        }
//        try {
//
//            builder.must(
//                    QueryBuilders.geoIntersectionQuery(field,
//                            ShapeBuilders.newPolygon(generateCoordinates(lon, lat))));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    //多边形搜索
//    static void polygonRange(BoolQueryBuilder builder, String field, String shape) {
//        WKTReader wktReader = new WKTReader();
//        try {
//            Geometry geometry = wktReader.read(shape);
//            Coordinate[] coordinates = geometry.getCoordinates();
//            MultiPolygonBuilder multiPolygonBuilder = new MultiPolygonBuilder();
//            CoordinatesBuilder coordinatesBuilder = new CoordinatesBuilder();
//            coordinatesBuilder.coordinates(coordinates);
//            PolygonBuilder polygonBuilder = new PolygonBuilder(coordinatesBuilder);
//            multiPolygonBuilder.polygon(polygonBuilder);
//            GeoShapeQueryBuilder geoShapeQueryBuilder = new GeoShapeQueryBuilder(field, multiPolygonBuilder);
//            builder.must(geoShapeQueryBuilder);
//        } catch (org.locationtech.jts.io.ParseException e) {
//            e.printStackTrace();
//        }
//    }
//
//    //矩形搜索
//    static void rectangleRange(BoolQueryBuilder builder, String field,
//                               Double northWestLon, Double northWestLat, Double northEastLon, Double northEastLat,
//                               Double southWestLon, Double southWestLat, Double southEastLon, Double southEastLat) {
//        if (northWestLon == null || northWestLat == null || northEastLon == null || northEastLat == null ||
//                southWestLon == null || southWestLat == null || southEastLon == null || southEastLat == null) {
//            return;
//        }
//        Double[] lon = {northWestLon, southWestLon, southEastLon, northEastLon};
//        Double[] lat = {northWestLat, southWestLat, southEastLat, northEastLat};
//        try {
//            builder.must(
//                    QueryBuilders.geoIntersectionQuery(field,
//                            ShapeBuilders.newPolygon(generateCoordinates(lon, lat))));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
//
//    //范围
//    static void range(BoolQueryBuilder builder, String field, Object from, Object to) {
//        if (from == null && to == null) {
//            return;
//        }
//        RangeQueryBuilder temp = QueryBuilders.rangeQuery(field);
//        if (from != null) {
//            temp.from(from);
//        }
//        if (to != null) {
//            temp.to(to);
//        }
//        builder.must(temp);
//    }
//
//    //分词搜索
//    static void match(BoolQueryBuilder builder, String field, String value) {
//        if (StringUtils.isNotBlank(value)) {
//            builder.must(QueryBuilders.matchQuery(field, value));
//        }
//    }
//
//    //精确搜索
//    static void term(BoolQueryBuilder builder, String field, String value) {
//        if (StringUtils.isNotBlank(value)) {
//            builder.must(QueryBuilders.termQuery(field, value));
//        }
//    }
//
//    static void shouldTerm(BoolQueryBuilder builder, String field, List<String> value) {
//        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
//        ListUtils.emptyIfNull(value).stream()
//                .filter(StringUtils::isNotEmpty)
//                .forEach(v -> boolQuery.should(QueryBuilders.termQuery(field, v)));
//        builder.must(boolQuery);
//    }
//
//    static void mustNotTerm(BoolQueryBuilder builder, String field, List<String> value) {
//        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
//        ListUtils.emptyIfNull(value).stream()
//                .filter(StringUtils::isNotEmpty)
//                .forEach(v -> boolQuery.mustNot(QueryBuilders.termQuery(field, v)));
//        builder.must(boolQuery);
//    }
//
//    //精确搜索
//    static void term(BoolQueryBuilder builder, String field, Integer value) {
//        if (value != null) {
//            builder.must(QueryBuilders.termQuery(field, value));
//        }
//    }
//
//    //精确搜索
//    static void term(BoolQueryBuilder builder, String field, Long value) {
//        if (value != null) {
//            builder.must(QueryBuilders.termQuery(field, value));
//        }
//    }
//
//    //精确搜索
//    static void term(BoolQueryBuilder builder, String field, Double value) {
//        if (value != null) {
//            builder.must(QueryBuilders.termQuery(field, value));
//        }
//    }
//
//    //like搜索
//    static void wildcard(BoolQueryBuilder builder, String field, String value) {
//        if (StringUtils.isNotBlank(value)) {
//            builder.must(QueryBuilders.wildcardQuery(field, "*" + value + "*"));
//        }
//    }
//
//    static ApiResult toApiResult(RestStatus restStatus) {
//        return restStatus.getStatus() >= 200 && restStatus.getStatus() < 300
//                ? ApiResult.success()
//                : ApiResult.fail(String.valueOf(restStatus.getStatus()));
//    }
//
//    //升序排序
//    static SortBuilder ASCSort(String field) {
//        return SortBuilders.fieldSort(field)
//                .order(SortOrder.ASC).unmappedType("long");
//    }
//
//    //降序排序
//    static SortBuilder DESCSort(String field) {
//        return SortBuilders.fieldSort(field)
//                .order(SortOrder.DESC).unmappedType("long");
//    }
//
//}
