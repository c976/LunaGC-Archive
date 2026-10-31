package emu.grasscutter.game.drop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Checks all checked-in native pool-invocation laws, without inventing item identities.
 * External DropSubTable candidates and quota/delivery metadata are outside this graph.
 */
class NativeDropGraphSafetyTest {
    @Test void allNativeServerTablesMeetTheNewBudgets() throws Exception {
        var source = JsonParser.parseString(Files.readString(
                Path.of("src/test/resources/native-snapshots/DropTableExcelConfigData.json"))).getAsJsonArray();
        var graph = new LootRecursionSafety.Graph();
        var nodes = new HashMap<Integer, LootRecursionSafety.Graph.Node>();
        for (var row : source) {
            int id = row.getAsJsonObject().get("id").getAsInt();
            // IdentityHashMap requires stable identity objects, independent of Integer caching.
            Object identity = new Object();
            graph.root("native_" + id, identity);
            nodes.put(id, graph.node(identity, "native_" + id));
        }
        assertEquals(16895, nodes.size());
        for (var row : source) {
            var table = row.getAsJsonObject();
            var node = nodes.get(table.get("id").getAsInt());
            int mode = table.get("randomType").getAsInt();
            assertTrue(mode == 0 || mode == 1);
            var drops = table.getAsJsonArray("dropVec");
            BigDecimal weightSum = BigDecimal.ZERO;
            for (var raw : drops) {
                var entry = raw.getAsJsonObject();
                if (entry.get("itemId").getAsInt() > 0 && entry.get("weight").getAsInt() > 0) {
                    weightSum = weightSum.add(entry.get("weight").getAsBigDecimal());
                }
            }
            var laws = new ArrayList<LootProbability.Law>();
            var weights = new ArrayList<BigDecimal>();
            for (var raw : drops) {
                var entry = raw.getAsJsonObject();
                int id = entry.get("itemId").getAsInt();
                BigDecimal weight = entry.get("weight").getAsBigDecimal();
                if (id <= 0 || weight.signum() == 0) continue;
                String count = NativeLootYamlExporter.normalizeNativeCount(
                        entry.get("countRange").getAsString());
                var child = nodes.get(id);
                LootProbability.Law law = child == null ? LootProbability.empty()
                        : LootProbability.repeat(child, count);
                if (child != null) {
                    var probability = LootRecursionSafety.Fraction.decimal(weight)
                            .divide(LootRecursionSafety.Fraction.decimal(
                                    mode == 0 ? weightSum : BigDecimal.valueOf(10000)));
                    graph.edge(node, child, probability.multiply(LootRecursionSafety.expectedCount(count)));
                }
                if (mode == 0) {
                    laws.add(law);
                    weights.add(weight);
                } else {
                    assertTrue(weight.compareTo(BigDecimal.valueOf(10000)) <= 0);
                    laws.add(LootProbability.choice(List.of(LootProbability.empty(), law),
                            List.of(BigDecimal.valueOf(10000).subtract(weight), weight)));
                }
            }
            node.law = mode == 0 && !laws.isEmpty()
                    ? LootProbability.choice(laws, weights) : LootProbability.all(laws);
        }
        var reports = graph.verify();
        BigDecimal maximumMean = reports.values().stream()
                .map(report -> new BigDecimal(report.expectedNodes())).max(BigDecimal::compareTo).orElseThrow();
        assertEquals(0, new BigDecimal("22").compareTo(maximumMean));
        assertTrue(reports.values().stream().allMatch(report ->
                report.nodeTailUpperBound().compareTo(new BigDecimal("1e-30")) < 0
                && report.depthTailUpperBound().compareTo(new BigDecimal("1e-30")) < 0));
        System.out.println("Native graph: " + reports.size() + " tables; maximum mean=" + maximumMean
                + "; both tail bounds below 1e-30 (external candidates/quotas excluded)");
    }
}
