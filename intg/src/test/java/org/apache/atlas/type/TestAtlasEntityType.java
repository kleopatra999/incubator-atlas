/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.ModelTestUtil;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.typedef.AtlasBaseTypeDef;
import org.apache.atlas.model.typedef.AtlasEntityDef;
import org.apache.atlas.model.typedef.AtlasStructDef.AtlasAttributeDef;
import org.apache.atlas.model.typedef.AtlasStructDef.AtlasConstraintDef;
import org.apache.atlas.type.AtlasTypeRegistry.AtlasTransientTypeRegistry;
import org.apache.atlas.type.AtlasEntityType.ForeignKeyReference;
import org.testng.annotations.Test;

import static org.testng.Assert.*;


public class TestAtlasEntityType {
    private static final String TYPE_TABLE   = "my_table";
    private static final String TYPE_COLUMN  = "my_column";
    private static final String ATTR_TABLE   = "table";
    private static final String ATTR_COLUMNS = "columns";

    private final AtlasEntityType entityType;
    private final List<Object>    validValues   = new ArrayList<>();
    private final List<Object>    invalidValues = new ArrayList<>();

    {
        entityType  = getEntityType(ModelTestUtil.getEntityDefWithSuperTypes());

        AtlasEntity         invalidValue1 = entityType.createDefaultValue();
        AtlasEntity         invalidValue2 = entityType.createDefaultValue();
        Map<String, Object> invalidValue3 = entityType.createDefaultValue().getAttributes();

        // invalid value for int
        invalidValue1.setAttribute(ModelTestUtil.getDefaultAttributeName(AtlasBaseTypeDef.ATLAS_TYPE_INT), "xyz");
        // invalid value for date
        invalidValue2.setAttribute(ModelTestUtil.getDefaultAttributeName(AtlasBaseTypeDef.ATLAS_TYPE_DATE), "xyz");
        // invalid value for bigint
        invalidValue3.put(ModelTestUtil.getDefaultAttributeName(AtlasBaseTypeDef.ATLAS_TYPE_BIGINTEGER), "xyz");

        validValues.add(null);
        validValues.add(entityType.createDefaultValue());
        validValues.add(entityType.createDefaultValue().getAttributes()); // Map<String, Object>
        invalidValues.add(invalidValue1);
        invalidValues.add(invalidValue2);
        invalidValues.add(invalidValue3);
        invalidValues.add(new AtlasEntity());             // no values for mandatory attributes
        invalidValues.add(new HashMap<>()); // no values for mandatory attributes
        invalidValues.add(1);               // incorrect datatype
        invalidValues.add(new HashSet());   // incorrect datatype
        invalidValues.add(new ArrayList()); // incorrect datatype
        invalidValues.add(new String[] {}); // incorrect datatype
    }

    @Test
    public void testEntityTypeDefaultValue() {
        AtlasEntity defValue = entityType.createDefaultValue();

        assertNotNull(defValue);
        assertEquals(defValue.getTypeName(), entityType.getTypeName());
    }

    @Test
    public void testEntityTypeIsValidValue() {
        for (Object value : validValues) {
            assertTrue(entityType.isValidValue(value), "value=" + value);
        }

        for (Object value : invalidValues) {
            assertFalse(entityType.isValidValue(value), "value=" + value);
        }
    }

    @Test
    public void testEntityTypeGetNormalizedValue() {
        assertNull(entityType.getNormalizedValue(null), "value=" + null);

        for (Object value : validValues) {
            if (value == null) {
                continue;
            }

            Object normalizedValue = entityType.getNormalizedValue(value);

            assertNotNull(normalizedValue, "value=" + value);
        }

        for (Object value : invalidValues) {
            assertNull(entityType.getNormalizedValue(value), "value=" + value);
        }
    }

    @Test
    public void testEntityTypeValidateValue() {
        List<String> messages = new ArrayList<>();
        for (Object value : validValues) {
            assertTrue(entityType.validateValue(value, "testObj", messages));
            assertEquals(messages.size(), 0, "value=" + value);
        }

        for (Object value : invalidValues) {
            assertFalse(entityType.validateValue(value, "testObj", messages));
            assertTrue(messages.size() > 0, "value=" + value);
            messages.clear();
        }
    }

    @Test
    public void testForeignKeyConstraintValid() {
        AtlasTypeRegistry          typeRegistry = new AtlasTypeRegistry();
        AtlasTransientTypeRegistry ttr          = null;
        boolean                    commit       = false;
        List<AtlasEntityDef>       entityDefs   = new ArrayList<>();
        String                     failureMsg   = null;

        entityDefs.add(createTableEntityDef());
        entityDefs.add(createColumnEntityDef());

        try {
            ttr = typeRegistry.lockTypeRegistryForUpdate();

            ttr.addTypes(entityDefs);

            AtlasEntityType typeTable  = ttr.getEntityTypeByName(TYPE_TABLE);
            AtlasEntityType typeColumn = ttr.getEntityTypeByName(TYPE_COLUMN);

            assertEquals(typeTable.getForeignKeyReferences().size(), 1);

            ForeignKeyReference fkRef = typeTable.getForeignKeyReferences().get(0);
            assertEquals(fkRef.fromTypeName(), TYPE_COLUMN);
            assertEquals(fkRef.fromAttributeName(), ATTR_TABLE);
            assertEquals(fkRef.toTypeName(), TYPE_TABLE);
            assertTrue(fkRef.isOnDeleteCascade());
            assertFalse(fkRef.isOnDeleteUpdate());

            assertEquals(typeTable.getForeignKeyAttributes().size(), 0);
            assertEquals(typeTable.getMappedFromRefAttributes().size(), 1);
            assertTrue(typeTable.getMappedFromRefAttributes().contains(ATTR_COLUMNS));

            assertEquals(typeColumn.getForeignKeyReferences().size(), 0);
            assertEquals(typeColumn.getForeignKeyAttributes().size(), 1);
            assertTrue(typeColumn.getForeignKeyAttributes().contains(ATTR_TABLE));
            assertEquals(typeColumn.getMappedFromRefAttributes().size(), 0);

            commit = true;
        } catch (AtlasBaseException excp) {
            failureMsg = excp.getMessage();
        } finally {
            typeRegistry.releaseTypeRegistryForUpdate(ttr, commit);
        }
        assertNull(failureMsg, "failed to create types " + TYPE_TABLE + " and " + TYPE_COLUMN);
    }

    @Test
    public void testForeignKeyConstraintInValidMappedFromRef() {
        AtlasTypeRegistry    typeRegistry = new AtlasTypeRegistry();
        List<AtlasEntityDef> entityDefs   = new ArrayList<>();
        String               failureMsg   = null;

        entityDefs.add(createTableEntityDef());

        AtlasTransientTypeRegistry ttr    = null;
        boolean                    commit = false;

        try {
            ttr = typeRegistry.lockTypeRegistryForUpdate();

            ttr.addTypes(entityDefs);

            commit = true;
        } catch (AtlasBaseException excp) {
            failureMsg = excp.getMessage();
        } finally {
            typeRegistry.releaseTypeRegistryForUpdate(ttr, commit);
        }
        assertNotNull(failureMsg, "expected invalid constraint failure - unknown attribute in mappedFromRef");
    }

    @Test
    public void testForeignKeyConstraintInValidMappedFromRef2() {
        AtlasTypeRegistry          typeRegistry = new AtlasTypeRegistry();
        AtlasTransientTypeRegistry ttr          = null;
        boolean                    commit       = false;
        List<AtlasEntityDef>       entityDefs   = new ArrayList<>();
        String                     failureMsg   = null;

        entityDefs.add(createTableEntityDefWithMissingRefAttribute());
        entityDefs.add(createColumnEntityDef());

        try {
            ttr = typeRegistry.lockTypeRegistryForUpdate();

            ttr.addTypes(entityDefs);

            commit = true;
        } catch (AtlasBaseException excp) {
            failureMsg = excp.getMessage();
        } finally {
            typeRegistry.releaseTypeRegistryForUpdate(ttr, commit);
        }
        assertNotNull(failureMsg, "expected invalid constraint failure - missing refAttribute in mappedFromRef");
    }

    @Test
    public void testForeignKeyConstraintInValidForeignKey() {
        AtlasTypeRegistry          typeRegistry = new AtlasTypeRegistry();
        AtlasTransientTypeRegistry ttr          = null;
        boolean                    commit       = false;
        List<AtlasEntityDef>       entityDefs   = new ArrayList<>();
        String                     failureMsg   = null;

        entityDefs.add(createColumnEntityDef());

        try {
            ttr = typeRegistry.lockTypeRegistryForUpdate();

            ttr.addTypes(entityDefs);

            commit = true;
        } catch (AtlasBaseException excp) {
            failureMsg = excp.getMessage();
        } finally {
            typeRegistry.releaseTypeRegistryForUpdate(ttr, commit);
        }
        assertNotNull(failureMsg, "expected invalid constraint failure - unknown attribute in foreignKey");
    }

    private static AtlasEntityType getEntityType(AtlasEntityDef entityDef) {
        try {
            return new AtlasEntityType(entityDef, ModelTestUtil.getTypesRegistry());
        } catch (AtlasBaseException excp) {
            return null;
        }
    }

    private AtlasEntityDef createTableEntityDef() {
        AtlasEntityDef    table       = new AtlasEntityDef(TYPE_TABLE);
        AtlasAttributeDef attrColumns = new AtlasAttributeDef(ATTR_COLUMNS,
                                                              AtlasBaseTypeDef.getArrayTypeName(TYPE_COLUMN));

        Map<String, Object> params = new HashMap<>();
        params.put(AtlasConstraintDef.CONSTRAINT_PARAM_REF_ATTRIBUTE, ATTR_TABLE);

        attrColumns.addConstraint(new AtlasConstraintDef(AtlasConstraintDef.CONSTRAINT_TYPE_MAPPED_FROM_REF, params));
        table.addAttribute(attrColumns);

        return table;
    }

    private AtlasEntityDef createTableEntityDefWithMissingRefAttribute() {
        AtlasEntityDef    table       = new AtlasEntityDef(TYPE_TABLE);
        AtlasAttributeDef attrColumns = new AtlasAttributeDef(ATTR_COLUMNS,
                                                              AtlasBaseTypeDef.getArrayTypeName(TYPE_COLUMN));

        attrColumns.addConstraint(new AtlasConstraintDef(AtlasConstraintDef.CONSTRAINT_TYPE_MAPPED_FROM_REF, null));
        table.addAttribute(attrColumns);

        return table;
    }

    private AtlasEntityDef createColumnEntityDef() {
        AtlasEntityDef    column    = new AtlasEntityDef(TYPE_COLUMN);
        AtlasAttributeDef attrTable = new AtlasAttributeDef(ATTR_TABLE, TYPE_TABLE);

        Map<String, Object> params = new HashMap<>();
        params.put(AtlasConstraintDef.CONSTRAINT_PARAM_ON_DELETE, AtlasConstraintDef.CONSTRAINT_PARAM_VAL_CASCADE);

        attrTable.addConstraint(new AtlasConstraintDef(AtlasConstraintDef.CONSTRAINT_TYPE_FOREIGN_KEY, params));
        column.addAttribute(attrTable);

        return column;
    }
}
