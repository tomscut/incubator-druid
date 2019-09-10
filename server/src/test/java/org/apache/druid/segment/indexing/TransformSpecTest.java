/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.segment.indexing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.druid.data.input.InputRow;
import org.apache.druid.data.input.impl.DimensionsSpec;
import org.apache.druid.data.input.impl.InputRowParser;
import org.apache.druid.data.input.impl.MapInputRowParser;
import org.apache.druid.data.input.impl.TimeAndDimsParseSpec;
import org.apache.druid.data.input.impl.TimestampSpec;
import org.apache.druid.java.util.common.DateTimes;
import org.apache.druid.query.expression.TestExprMacroTable;
import org.apache.druid.query.filter.AndDimFilter;
import org.apache.druid.query.filter.SelectorDimFilter;
import org.apache.druid.segment.TestHelper;
import org.apache.druid.segment.transform.ExpressionTransform;
import org.apache.druid.segment.transform.JsonValueTransform;
import org.apache.druid.segment.transform.RowFunction;
import org.apache.druid.segment.transform.TransformSpec;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class TransformSpecTest {
    private static final MapInputRowParser PARSER = new MapInputRowParser(
            new TimeAndDimsParseSpec(
                    new TimestampSpec("t", "auto", DateTimes.of("2000-01-01"), null),
                    new DimensionsSpec(
                            DimensionsSpec.getDefaultSchemas(ImmutableList.of("f", "x", "y")),
                            null,
                            null
                    )
            )
    );

    private static final Map<String, Object> ROW1 = ImmutableMap.<String, Object>builder()
            .put("x", "foo")
            .put("y", "bar")
            .put("a", 2.0)
            .put("b", 3L)
            .build();

    private static final Map<String, Object> ROW2 = ImmutableMap.<String, Object>builder()
            .put("x", "foo")
            .put("y", "baz")
            .put("a", 2.0)
            .put("b", 4L)
            .build();

    private static final Map<String, Object> ROW3 = ImmutableMap.<String, Object>builder()
            .put("x", "foo")
            .put("y", "baz")
            .put("z", "{\"a\":\"1\",\"b\":2,\"c\":{\"d\":{\"g\":{\"h\":3}}}, \"e\":null, \"f\":\"null\"}")
            .put("a", 2.0)
            .put("b", 5L)
            .build();

    @Test
    public void testTransforms() {
        final TransformSpec transformSpec = new TransformSpec(
                null,
                ImmutableList.of(
                        new ExpressionTransform("f", "concat(x,y)", TestExprMacroTable.INSTANCE),
                        new ExpressionTransform("g", "a + b", TestExprMacroTable.INSTANCE),
                        new ExpressionTransform("h", "concat(f,g)", TestExprMacroTable.INSTANCE)
                )
        );

        final InputRowParser<Map<String, Object>> parser = transformSpec.decorate(PARSER);
        final InputRow row = parser.parseBatch(ROW1).get(0);

        Assert.assertNotNull(row);
        Assert.assertEquals(DateTimes.of("2000-01-01").getMillis(), row.getTimestampFromEpoch());
        Assert.assertEquals(DateTimes.of("2000-01-01"), row.getTimestamp());
        Assert.assertEquals(ImmutableList.of("f", "x", "y"), row.getDimensions());
        Assert.assertEquals(ImmutableList.of("foo"), row.getDimension("x"));
        Assert.assertEquals(3.0, row.getMetric("b").doubleValue(), 0);
        Assert.assertEquals("foobar", row.getRaw("f"));
        Assert.assertEquals(ImmutableList.of("foobar"), row.getDimension("f"));
        Assert.assertEquals(ImmutableList.of("5.0"), row.getDimension("g"));
        Assert.assertEquals(ImmutableList.of(), row.getDimension("h"));
        Assert.assertEquals(5L, row.getMetric("g").longValue());
    }

    @Test
    public void testJsonValueTransforms() {
        final TransformSpec transformSpec = new TransformSpec(
                null,
                ImmutableList.of(
                        new JsonValueTransform("o1", "z", "a", 10),
                        new JsonValueTransform("o2", "z", "b", 10),
                        new JsonValueTransform("o3", "z", "c", 10),
                        new JsonValueTransform("o4", "z", "e", 10),
                        new JsonValueTransform("o5", "z", "f", 10)
                )
        );

        final InputRowParser<Map<String, Object>> parser = transformSpec.decorate(PARSER);
        final InputRow row = parser.parseBatch(ROW3).get(0);

        Assert.assertNotNull(row);
        Assert.assertEquals("1", transformSpec.getTransforms().get(0).getRowFunction().eval(row));
        Assert.assertEquals("2", transformSpec.getTransforms().get(1).getRowFunction().eval(row));
        Assert.assertEquals("{\"d\":{\"g\":{\"h\":3}}}", transformSpec.getTransforms().get(2).getRowFunction().eval(row));
        Assert.assertEquals(null, transformSpec.getTransforms().get(3).getRowFunction().eval(row));
        Assert.assertEquals("null", transformSpec.getTransforms().get(4).getRowFunction().eval(row));
    }


    @Test
    public void testTransformOverwriteField() {
        // Transforms are allowed to overwrite fields, and to refer to the fields they overwrite; double-check this.

        final TransformSpec transformSpec = new TransformSpec(
                null,
                ImmutableList.of(
                        new ExpressionTransform("x", "concat(x,y)", TestExprMacroTable.INSTANCE)
                )
        );

        final InputRowParser<Map<String, Object>> parser = transformSpec.decorate(PARSER);
        final InputRow row = parser.parseBatch(ROW1).get(0);

        Assert.assertNotNull(row);
        Assert.assertEquals(DateTimes.of("2000-01-01").getMillis(), row.getTimestampFromEpoch());
        Assert.assertEquals(DateTimes.of("2000-01-01"), row.getTimestamp());
        Assert.assertEquals(ImmutableList.of("f", "x", "y"), row.getDimensions());
        Assert.assertEquals(ImmutableList.of("foobar"), row.getDimension("x"));
        Assert.assertEquals(3.0, row.getMetric("b").doubleValue(), 0);
        Assert.assertNull(row.getRaw("f"));
    }

    @Test
    public void testFilterOnTransforms() {
        // Filters are allowed to refer to transformed fields; double-check this.

        final TransformSpec transformSpec = new TransformSpec(
                new AndDimFilter(
                        ImmutableList.of(
                                new SelectorDimFilter("x", "foo", null),
                                new SelectorDimFilter("f", "foobar", null),
                                new SelectorDimFilter("g", "5.0", null)
                        )
                ),
                ImmutableList.of(
                        new ExpressionTransform("f", "concat(x,y)", TestExprMacroTable.INSTANCE),
                        new ExpressionTransform("g", "a + b", TestExprMacroTable.INSTANCE)
                )
        );

        final InputRowParser<Map<String, Object>> parser = transformSpec.decorate(PARSER);
        Assert.assertNotNull(parser.parseBatch(ROW1).get(0));
        Assert.assertNull(parser.parseBatch(ROW2).get(0));
    }

    @Test
    public void testTransformTimeFromOtherFields() {
        final TransformSpec transformSpec = new TransformSpec(
                null,
                ImmutableList.of(
                        new ExpressionTransform("__time", "(a + b) * 3600000", TestExprMacroTable.INSTANCE)
                )
        );

        final InputRowParser<Map<String, Object>> parser = transformSpec.decorate(PARSER);
        final InputRow row = parser.parseBatch(ROW1).get(0);

        Assert.assertNotNull(row);
        Assert.assertEquals(DateTimes.of("1970-01-01T05:00:00Z"), row.getTimestamp());
        Assert.assertEquals(DateTimes.of("1970-01-01T05:00:00Z").getMillis(), row.getTimestampFromEpoch());
    }

    @Test
    public void testTransformTimeFromTime() {
        final TransformSpec transformSpec = new TransformSpec(
                null,
                ImmutableList.of(
                        new ExpressionTransform("__time", "__time + 3600000", TestExprMacroTable.INSTANCE)
                )
        );

        final InputRowParser<Map<String, Object>> parser = transformSpec.decorate(PARSER);
        final InputRow row = parser.parseBatch(ROW1).get(0);

        Assert.assertNotNull(row);
        Assert.assertEquals(DateTimes.of("2000-01-01T01:00:00Z"), row.getTimestamp());
        Assert.assertEquals(DateTimes.of("2000-01-01T01:00:00Z").getMillis(), row.getTimestampFromEpoch());
    }

    @Test
    public void testSerde() throws Exception {
        final TransformSpec transformSpec = new TransformSpec(
                new AndDimFilter(
                        ImmutableList.of(
                                new SelectorDimFilter("x", "foo", null),
                                new SelectorDimFilter("f", "foobar", null),
                                new SelectorDimFilter("g", "5.0", null)
                        )
                ),
                ImmutableList.of(
                        new ExpressionTransform("f", "concat(x,y)", TestExprMacroTable.INSTANCE),
                        new ExpressionTransform("g", "a + b", TestExprMacroTable.INSTANCE)
                )
        );

        final ObjectMapper jsonMapper = TestHelper.makeJsonMapper();
        Assert.assertEquals(
                transformSpec,
                jsonMapper.readValue(jsonMapper.writeValueAsString(transformSpec), TransformSpec.class)
        );
    }
}
