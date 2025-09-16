package com.aircas.ptr.foundry.ontology.entity.controller;


import com.aircas.ptr.foundry.ontology.entity.service.OntologyService;
import com.aircas.ptr.foundry.ontology.entity.util.SatelliteUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试控制器
 * 提供各种测试接口
 */
@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private SatelliteUtils satelliteUtils;
    
    @Autowired
    private OntologyService ontologyService;


    

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "运行中");
        response.put("timestamp", ZonedDateTime.now().toString());
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

//    /**
//     * 测试Orekit数据加载状态
//     */
//    @GetMapping("/orekit-status")
//    public ResponseEntity<Map<String, Object>> orekitStatus() {
//        Map<String, Object> response = new HashMap<>();
//        try {
//            // 尝试通过计算简单的卫星可见性来测试Orekit功能
//            String tle1 = "1 25544U 98067A   21086.42859259  .00001795  00000-0  38792-4 0  9993";
//            String tle2 = "2 25544  51.6435 123.2046 0003169  68.0050  83.7047 15.48910268276667";
//            double latitude = 39.9042; // 北京纬度
//            double longitude = 116.4074; // 北京经度
//            double altitude = 0.0;
//
//            Map<String, Object> result = satelliteUtils.calculateVisibilityWindow(tle1, tle2, latitude, longitude, altitude, 10.0, ZonedDateTime.now().toString(), 24);
//
//            response.put("status", "正常");
//            response.put("message", "Orekit数据加载成功");
//            response.put("testResult", result);
//        } catch (Exception e) {
//            response.put("status", "异常");
//            response.put("message", "Orekit数据加载失败: " + e.getMessage());
//            response.put("stackTrace", e.getStackTrace());
//        }
//        return ResponseEntity.ok(response);
//    }

    /**
     * 测试时间解析功能
     */
    @GetMapping("/parse-time")
    public ResponseEntity<Map<String, Object>> parseTime(@RequestParam String timeStr) {
        Map<String, Object> response = new HashMap<>();
        try {
            ZonedDateTime time = ZonedDateTime.parse(timeStr);
            response.put("status", "成功");
            response.put("originalTime", timeStr);
            response.put("parsedTime", time.toString());
            response.put("year", time.getYear());
            response.put("month", time.getMonthValue());
            response.put("day", time.getDayOfMonth());
            response.put("hour", time.getHour());
            response.put("minute", time.getMinute());
            response.put("second", time.getSecond());
            response.put("zone", time.getZone().toString());
        } catch (Exception e) {
            response.put("status", "失败");
            response.put("message", "时间解析失败: " + e.getMessage());
            response.put("tip", "请使用ISO-8601格式，例如：2023-04-15T14:30:00+08:00[Asia/Shanghai]");
        }
        return ResponseEntity.ok(response);
    }

    /**
     * 测试本体数据查询功能
     */
//    @GetMapping("/ontology-count")
//    public ResponseEntity<Map<String, Object>> ontologyCount() {
//        Map<String, Object> response = new HashMap<>();
//        try {
//            long nodeCount = ontologyService.countNodes();
//            long relationCount = ontologyService.countRelations();
//
//            response.put("status", "成功");
//            response.put("nodeCount", nodeCount);
//            response.put("relationCount", relationCount);
//        } catch (Exception e) {
//            response.put("status", "失败");
//            response.put("message", "查询失败: " + e.getMessage());
//        }
//        return ResponseEntity.ok(response);
//    }

    /**
     * 测试系统环境
     */
    @GetMapping("/env")
    public ResponseEntity<Map<String, Object>> environmentInfo() {
        Map<String, Object> response = new HashMap<>();
        
        // Java环境信息
        Map<String, String> javaInfo = new HashMap<>();
        javaInfo.put("version", System.getProperty("java.version"));
        javaInfo.put("vendor", System.getProperty("java.vendor"));
        javaInfo.put("home", System.getProperty("java.home"));
        
        // 操作系统信息
        Map<String, String> osInfo = new HashMap<>();
        osInfo.put("name", System.getProperty("os.name"));
        osInfo.put("version", System.getProperty("os.version"));
        osInfo.put("arch", System.getProperty("os.arch"));
        
        // 用户信息
        Map<String, String> userInfo = new HashMap<>();
        userInfo.put("name", System.getProperty("user.name"));
        userInfo.put("home", System.getProperty("user.home"));
        userInfo.put("dir", System.getProperty("user.dir"));
        
        response.put("java", javaInfo);
        response.put("os", osInfo);
        response.put("user", userInfo);
        response.put("timestamp", ZonedDateTime.now().toString());
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取舰船类型数据
     * 返回驱逐舰和传感器相关信息
     */
    @GetMapping("/ship-data")
    public ResponseEntity<Map<String, Object>> getShipData() {
        Map<String, Object> response = new HashMap<>();
        response.put("result", "true");
        response.put("rejectReason", "");
        response.put("errorCode", -1);
        response.put("thumbPath", "");
        response.put("strategyVersionNumber", 0);
        response.put("cause", null);
        
        // 构建detail数组
        List<Map<String, Object>> detailList = new ArrayList<>();
        
        // 第一个元素 - 驱逐舰-弹药
        Map<String, Object> item1 = new HashMap<>();
        item1.put("id", 197);
        item1.put("uniqueIdentifier", "7d050fc2-e0ff-427e-81fe-2ce9c2d9e033");
        item1.put("parentUniqueIdentifier", "null");
        item1.put("status", 1);
        item1.put("createTime", "2024-09-04 15:15:26");
        item1.put("updateTime", "2024-09-04 15:15:26");
        item1.put("icon", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACgAAAAoCAYAAACM/rhtAAAABHNCSVQICAgIfAhkiAAAA21JREFUWEdjZIAC6403eLl//5Jm/MPCxcD0nwUmTlf6H+Of/yz/vn1lZXt61F/jM8huRhDhtOqCNCsDqzRdHUPAst8Mv5/uCzN4yggKOZ6ffzUHk+NgbvnCznyd0W3NJQ3Gf8x8g9GB/5n+fmJ0X3HNaMDSHKFQAaZJRvdVV80IqRtI+VEHUhr6dAvBeZ4qheLcrN7vvv89uPzaqznb7n94Tozj6eZAkGN0hbl5kg3EfJQEOCLW335bOv/Sq9uEHEk3B053U0plZ2bi7j/1fI6LEp+ao5xAXfWBRzGX3379gs+RVHdgop6Yqp+SUF3d4ceZyJZ7KQpIuikJ2svzsvsEbrgRs9hbpe7Zlz9nyw8+2EpXB67wU+8SYGe2//773y2QI0FRKsPLbvfk88+DBXvvrwTJn3z+aYU8H4caOzMjb+aue7Pp6sAdoVonYRaCHDnnwuuSFAPRXk5WJtWVN9/EvPz868vjT7/BDQFZPlZeQpmFqCgGJe4mW9npm+69ayKUsJEdCHIEsiMPPf3Y13/6+TlCGQNZnqADYY679f7HClB6qbWUtm8+/vQgLks2BWtuYGNilESWhzny0ecfXwllCnRzCTpwfYDGEqDjloMcB0rYIANit95pwuVAUHknxcMWgS4PS5NUdyAo94HSCbLjOu0VvEEOQM+BoBx85unX560OckvQQxEW3ei5m1B0EwxBkAEgi51k+SJBIQdynJogRyTQogxQaMCKixvvv90KUROeDqop9j3+sCJQVbgbmyMvvvrWRKhoISkNIivG5jhRLjZwaP79//8zMyMjL4j9+tuvrTDP8LEzSfGwMkl++f3v+abb77YQyrUkp0FkDaAMsuHWh7OwkIM5DlkN0KFf1tx6m0EotxOKWpg8UVGMbhgoWrE5DqTu/scfcwgVvsQ6DqSOZAeC6lRFfo4UfJbsfPA+k9TyDpd5JDtwa4jmXmBa48FlICz9kRJK+NRS1YHUdhxVoxjmOFKqRWJCmeQQBBmKnknQHQerFkHlJ6W5mSwHghwJKnKkeNnVPv389wxW8JJaLdIsBHEZTEq1SIzjyEqDhAzGVy0S0otNnuwoJmQZKAkYifOmwupsQuqpVg4SaxFytUisHrqGICWOQtZLsygeQQ4c7MNvg34Ac9APAYMS86AeRIfltsE6DQEAlQgtN1i+Z5sAAAAASUVORK5CYII=");
        item1.put("displayName", "驱逐舰-弹药");
        item1.put("pluralDisplayName", null);
        item1.put("backingDatasourceId", "datadestroyermagazines");
        item1.put("description", "");
        item1.put("apiName", "destroyerMagazines");
        item1.put("visibility", 1);
        item1.put("experimentalStatus", 0);
        item1.put("indexStatus", 1);
        item1.put("writebackFlag", 0);
        item1.put("metaGroupId", null);
        
        // 第二个元素 - 阿利伯克级驱逐舰-传感器
        Map<String, Object> item2 = new HashMap<>();
        item2.put("id", 214);
        item2.put("uniqueIdentifier", "e894c961-483a-455d-b43a-a3fb90de6fd2");
        item2.put("parentUniqueIdentifier", "7d050fc2-e0ff-427e-81fe-2ce9c2d9e033");
        item2.put("status", 1);
        item2.put("createTime", "2024-09-13 02:35:05");
        item2.put("updateTime", "2024-09-13 02:35:05");
        item2.put("icon", "data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSI0NCIgaGVpZ2h0PSI0NCIgdmlld0JveD0iMCAwIDQ0IDQ0Ij48ZyB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtODk3LjI3MyAtMzM2LjI3MykiPjxnIHRyYW5zZm9ybT0idHJhbnNsYXRlKDg5NyAzMzYpIj48cmVjdCB3aWR0aD0iNDQiIGhlaWdodD0iNDQiIHJ4PSI2IiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgwLjI3MyAwLjI3MykiIGZpbGw9IiMxMTM5NDUiLz48ZyB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtNjkuNzM2IC0xMTAwLjgwNCkiPjxwYXRoIGQ9Ik0xMTMuMTQ0LDExNTQuNzgxaDEwLjU2OXYtMTAuNTY5SDExMy4xNDRabTYuNTM4LTkuOTYyaDMuMDA3YS40MTcuNDE3LDAsMCwxLC40MTcuNDE3djMuMDA3YS40MTcuNDE3LDAsMCwxLS44MzQsMHYtMi41OWgtMi41OWEuNDE3LjQxNywwLDAsMSwwLS44MzRabTAsOC41MjFoMi41OXYtMi41OWEuNDE3LjQxNywwLDAsMSwuODM0LDB2My4wMDdhLjQxNy40MTcsMCwwLDEtLjQxNy40MTdoLTMuMDA3YS40MTcuNDE3LDAsMSwxLDAtLjgzNFptLTUuOTMxLTguMWEuNDE3LjQxNywwLDAsMSwuNDE3LS40MTdoMy4wMDdhLjQxNy40MTcsMCwwLDEsMCwuODM0aC0yLjU5djIuNTlhLjQxNy40MTcsMCwwLDEtLjgzNCwwWm0wLDUuNTE0YS40MTcuNDE3LDAsMCwxLC44MzQsMHYyLjU5aDIuNTlhLjQxNy40MTcsMCwxLDEsMCwuODM0aC0zLjAwN2EuNDE3LjQxNywwLDAsMS0uNDE3LS40MTdaIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtMjYuNDE4IC0yNi40MTgpIiBmaWxsPSIjZmZmIi8+PHBhdGggZD0iTTExMi44NDMsMTEzMC42NDVIMTAxLjQ4YTEuOSwxLjksMCwwLDAtMS45LDEuOXYxMS4zNjNhMS45LDEuOSwwLDAsMCwxLjksMS45aDExLjM2M2ExLjksMS45LDAsMCwwLDEuOS0xLjl2LTExLjM2M0ExLjksMS45LDAsMCwwLDExMi44NDMsMTEzMC42NDVabS40MzcsMTMuMjg1YS40MTcuNDE3LDAsMCwxLS40MTcuNDE3aC0xMS40YS40MTcuNDE3LDAsMCwxLS40MTctLjQxN3YtMTEuNGEuNDE3LjQxNywwLDAsMSwuNDE3LS40MTdoMTEuNGEuNDE3LjQxNywwLDAsMSwuNDE3LjQxN1oiIHRyYW5zZm9ybT0idHJhbnNsYXRlKC0xNS4xNTEgLTE1LjE1MSkiIGZpbGw9IiNmZmYiLz48cGF0aCBkPSJNMTEyLjQzMywxMTE0Ljg4N2EuNDE3LjQxNywwLDAsMCwuNDE3LS40MTd2LTEuNjQ5YS40MTcuNDE3LDAsMSwwLS44MzQsMHYxLjY0OUEuNDE3LjQxNywwLDAsMCwxMTIuNDMzLDExMTQuODg3WiIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoLTI1LjQ4MSAwKSIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Ik0xMzIuMzQsMTExNC44ODdhLjQxNy40MTcsMCwwLDAtLjQxNy40MTd2LTEuNjQ5YS40MTcuNDE3LDAsMSwwLS44MzQsMHYxLjY0OUEuNDE3LjQxNywwLDAsMCwxMzIuMzQsMTExNC44ODdaIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtNDIuMDE2IC05Mi40OCkiIGZpbGw9IiNmZmYiLz48cGF0aCBkPSJNMTUyLjI0NywxMTE0Ljg4N2EuNDE3LjQxNywwLDAsMC0uNDE3LjQxN3YtMS42NDlhLjQxNy40MTcsMCwxLDAtLjgzNCwwdjEuNjQ5QS40MTcuNDE3LDAsMCwwLDE1Mi4yNDcsMTExNC44ODdaIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtNTguNTUgLTkyLjQ4KSIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Ik0xNzIuMTU0LDExMTQuODg3YS40MTcuNDE3LDAsMCwwLS40MTcuNDE3djEuNjQ5YS40MTcuNDE3LDAsMSwwLC44MzQsMHYtMS42NDlBLjQxNy40MTcsMCwwLDAsMTcyLjE1NCwxMTE0Ljg4N1oiIHRyYW5zZm9ybT0idHJhbnNsYXRlKC03NS4wODUgLTkyLjQ4KSIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Ik0xMTIuNDMzLDEyMjMuNzQ3YS40MTcuNDE3LDAsMCwwLS40MTcuNDE3djEuNjQ5YS40MTcuNDE3LDAsMSwwLC44MzQsMHYtMS42NDlBLjQxNy40MTcsMCwwLDAsMTEyLjQzMywxMjIzLjc0N1oiIHRyYW5zZm9ybT0idHJhbnNsYXRlKC0yNS40ODEgLTkyLjQ4KSIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Ik0xMzIuMzQsMTIyMy43NDdhLjQxNy40MTcsMCwwLDAtLjQxNy40MTd2MS42NDlhLjQxNy40MTcsMCwxLDAsLjgzNCwwdi0xLjY0OUEuNDE3LjQxNywwLDAsMCwxMzIuMzQsMTIyMy43NDdaIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtNDIuMDE2IC05Mi40OCkiIGZpbGw9IiNmZmYiLz48cGF0aCBkPSJNMTUyLjI0NywxMjIzLjc0N2EuNDE3LjQxNywwLDAsMC0uNDE3LjQxN3YxLjY0OWEuNDE3LjQxNywwLDEsMCwuODM0LDB2LTEuNjQ5QS40MTcuNDE3LDAsMCwwLDE1Mi4yNDcsMTIyMy43NDdaIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtNTguNTUgLTkyLjQ4KSIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Ik0xNzIuMTU0LDEyMjMuNzQ3YS40MTcuNDE3LDAsMCwwLS40MTcuNDE3djEuNjQ5YS40MTcuNDE3LDAsMSwwLC44MzQsMHYtMS42NDlBLjQxNy40MTcsMCwwLDAsMTcyLjE1NCwxMjIzLjc0N1oiIHRyYW5zZm9ybT0idHJhbnNsYXRlKC03NS4wODUgLTkyLjQ4KSIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Ik0xOTMuMSwxMTQzLjkxN2gxLjY0OWEuNDE3LjQxNywwLDEsMCwwLS44MzRIMTkzLjFhLjQxNy40MTcsMCwxLDAsMCwuODM0WiIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoLTkyLjQ4IC0yNS40ODEpIiBmaWxsPSIjZmZmIi8+PHBhdGggZD0iTTE5NC43NDYsMTE2Mi45OUgxOTMuMWEuNDE3LjQxNywwLDEsMCwwLC44MzRoMS42NDlhLjQxNy40MTcsMCwxLDAsMC0uODM0WiIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoLTkyLjQ4IC00Mi4wMTYpIiBmaWxsPSIjZmZmIi8+PHBhdGggZD0iTTE5NC43NDYsMTE4Mi45SDE5My4xYS40MTcuNDE3LDAsMSwwLDAsLjgzNGgxLjY0OWEuNDE3LjQxNywwLDEsMCwwLS44MzRaIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgtOTIuNDggLTU4LjU1KSIgZmlsbD0iI2ZmZiIvPjxwYXRoIGQ9Ik0xOTQuNzQ2LDEyMDIuOEgxOTMuMWEuNDE3LjQxNywwLDAsMCwwLC44MzRoMS42NDlhLjQxNy40MTcsMCwwLDAsMC0uODM0WiIgdHJhbnNmb3JtPSJ0cmFuc2xhdGUoLTkyLjQ4IC03NS4wODUpIiBmaWxsPSIjZmZmIi8+PHBhdGggZD0iTTgzLjQsMTE0My4wODNIODEuNzU0YS40MTcuNDE3LDAsMSwwLDAsLjgzNEg4My40YS40MTcuNDE3LDAsMSwwLDAtLjgzNFoiIHRyYW5zZm9ybT0idHJhbnNsYXRlKDAgLTI1LjQ4MSkiIGZpbGw9IiNmZmYiLz48cGF0aCBkPSJNODMuNCwxMTYyLjk5SDgxLjc1NGEuNDE3LjQxNywwLDEsMCwwLC44MzRIODMuNGEuNDE3LjQxNywwLDEsMCwwLS44MzRaIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgwIC00Mi4wMTYpIiBmaWxsPSIjZmZmIi8+PHBhdGggZD0iTTgzLjQsMTE4Mi45SDgxLjc1NGEuNDE3LjQxNywwLDEsMCwwLC44MzRIODMuNGEuNDE3LjQxNywwLDEsMCwwLS44MzRaIiB0cmFuc2Zvcm09InRyYW5zbGF0ZSgwIC01OC41NSkiIGZpbGw9IiNmZmYiLz48cGF0aCBkPSJNODMuNCwxMjAyLjhIODEuNzU0YS40MTcuNDE3LDAsMCwwLDAsLjgzNEg4My40YS40MTcuNDE3LDAsMCwwLDAtLjgzNFoiIHRyYW5zZm9ybT0idHJhbnNsYXRlKDAgLTc1LjA4NSkiIGZpbGw9IiNmZmYiLz48L2c+PC9nPjwvZz48L3N2Zz4=");
        item2.put("displayName", "阿利伯克级驱逐舰-传感器");
        item2.put("pluralDisplayName", null);
        item2.put("backingDatasourceId", "arleighburkeclassdestroyerssensors");
        item2.put("description", "");
        item2.put("apiName", "arleighburkeclassdestroyerssensors");
        item2.put("visibility", 1);
        item2.put("experimentalStatus", 0);
        item2.put("indexStatus", 1);
        item2.put("writebackFlag", 0);
        item2.put("metaGroupId", "3e6dee87-b5d6-441a-aed1-06adf0af219d");
        
        detailList.add(item1);
        detailList.add(item2);
        
        response.put("detail", detailList);
        response.put("pagination", null);
        
        return ResponseEntity.ok(response);
    }
} 