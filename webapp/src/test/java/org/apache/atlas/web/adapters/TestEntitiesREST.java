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
package org.apache.atlas.web.adapters;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.atlas.AtlasClient;
import org.apache.atlas.RepositoryMetadataModule;
import org.apache.atlas.RequestContext;
import org.apache.atlas.TestUtilsV2;
import org.apache.atlas.model.instance.AtlasClassification;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntityHeader;
import org.apache.atlas.model.instance.AtlasObjectId;
import org.apache.atlas.model.instance.AtlasStruct;
import org.apache.atlas.model.instance.ClassificationAssociateRequest;
import org.apache.atlas.model.instance.EntityMutationResponse;
import org.apache.atlas.model.instance.EntityMutations;
import org.apache.atlas.model.typedef.AtlasTypesDef;
import org.apache.atlas.repository.graph.AtlasGraphProvider;
import org.apache.atlas.store.AtlasTypeDefStore;
import org.apache.atlas.web.rest.EntitiesREST;

import org.apache.atlas.web.rest.EntityREST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Guice;
import org.testng.annotations.Test;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Guice(modules = {RepositoryMetadataModule.class})
public class TestEntitiesREST {

    private static final Logger LOG = LoggerFactory.getLogger(TestEntitiesREST.class);

    @Inject
    private AtlasTypeDefStore typeStore;

    @Inject
    private EntitiesREST entitiesREST;

    @Inject
    private EntityREST entityREST;

    private List<String> createdGuids = new ArrayList<>();

    private Map<String, AtlasEntity> dbEntityMap;

    private Map<String, AtlasEntity> tableEntityMap;

    private AtlasEntity dbEntity;

    private AtlasEntity tableEntity;

    private List<AtlasEntity> columns;

    @BeforeClass
    public void setUp() throws Exception {
        AtlasTypesDef typesDef = TestUtilsV2.defineHiveTypes();
        typeStore.createTypesDef(typesDef);
        dbEntityMap = TestUtilsV2.createDBEntity();
        dbEntity = dbEntityMap.values().iterator().next();

        tableEntityMap = TestUtilsV2.createTableEntity(dbEntity.getGuid());
        tableEntity = tableEntityMap.values().iterator().next();

        final AtlasEntity colEntity = TestUtilsV2.createColumnEntity(tableEntity.getGuid());
        columns = new ArrayList<AtlasEntity>() {{ add(colEntity); }};
        tableEntity.setAttribute("columns", columns);
    }

    @AfterMethod
    public void cleanup() throws Exception {
        RequestContext.clear();
    }

    @AfterClass
    public void tearDown() throws Exception {
        AtlasGraphProvider.cleanup();
    }

    @Test
    public void testCreateOrUpdateEntities() throws Exception {
        Map<String, AtlasEntity> entities = new HashMap<>();
        entities.put(dbEntity.getGuid(), dbEntity);
        entities.put(tableEntity.getGuid(), tableEntity);

        EntityMutationResponse response = entitiesREST.createOrUpdate(entities);
        List<AtlasEntityHeader> guids = response.getEntitiesByOperation(EntityMutations.EntityOperation.CREATE);

        Assert.assertNotNull(guids);
        Assert.assertEquals(guids.size(), 3);

        for (AtlasEntityHeader header : guids) {
            createdGuids.add(header.getGuid());
        }
    }

    @Test(dependsOnMethods = "testCreateOrUpdateEntities")
    public void testTagToMultipleEntities() throws Exception{
        AtlasClassification tag = new AtlasClassification(TestUtilsV2.CLASSIFICATION, new HashMap<String, Object>() {{ put("tag", "tagName"); }});
        ClassificationAssociateRequest classificationAssociateRequest = new ClassificationAssociateRequest(createdGuids, tag);
        entitiesREST.addClassification(classificationAssociateRequest);
        for (String guid : createdGuids) {
            final AtlasClassification result_tag = entityREST.getClassification(guid, TestUtilsV2.CLASSIFICATION);
            Assert.assertNotNull(result_tag);
            Assert.assertEquals(result_tag, tag);
        }
    }

    @Test
    public void testUpdateWithSerializedEntities() throws  Exception {
        //Check with serialization and deserialization of entity attributes for the case
        // where attributes which are de-serialized into a map
        Map<String, AtlasEntity> dbEntityMap = TestUtilsV2.createDBEntity();
        AtlasEntity dbEntity = dbEntityMap.values().iterator().next();

        Map<String, AtlasEntity> tableEntityMap = TestUtilsV2.createTableEntity(dbEntity.getGuid());
        AtlasEntity tableEntity = tableEntityMap.values().iterator().next();

        final AtlasEntity colEntity = TestUtilsV2.createColumnEntity(tableEntity.getGuid());
        List<AtlasEntity> columns = new ArrayList<AtlasEntity>() {{ add(colEntity); }};
        tableEntity.setAttribute("columns", columns);

        AtlasEntity newDBEntity = serDeserEntity(dbEntity);
        AtlasEntity newTableEntity = serDeserEntity(tableEntity);

        Map<String, AtlasEntity> newEntities = new HashMap<>();
        newEntities.put(newDBEntity.getGuid(), newDBEntity);
        newEntities.put(newTableEntity.getGuid(), newTableEntity);
        EntityMutationResponse response2 = entitiesREST.createOrUpdate(newEntities);

        List<AtlasEntityHeader> newGuids = response2.getEntitiesByOperation(EntityMutations.EntityOperation.CREATE);
        Assert.assertNotNull(newGuids);
        Assert.assertEquals(newGuids.size(), 3);
    }

    @Test(dependsOnMethods = "testCreateOrUpdateEntities")
    public void testGetEntities() throws Exception {

        final AtlasEntity.AtlasEntities response = entitiesREST.getById(createdGuids);
        final List<AtlasEntity> entities = response.getList();

        Assert.assertNotNull(entities);
        Assert.assertEquals(entities.size(), 3);
        verifyAttributes(entities);
    }

    @Test(dependsOnMethods = "testGetEntities")
    public void testDeleteEntities() throws Exception {

        final EntityMutationResponse response = entitiesREST.deleteById(createdGuids);
        final List<AtlasEntityHeader> entities = response.getEntitiesByOperation(EntityMutations.EntityOperation.DELETE);

        Assert.assertNotNull(entities);
        Assert.assertEquals(entities.size(), 3);
    }

    private void verifyAttributes(List<AtlasEntity> retrievedEntities) throws Exception {
        AtlasEntity retrievedDBEntity = null;
        AtlasEntity retrievedTableEntity = null;
        AtlasEntity retrievedColumnEntity = null;
        for (AtlasEntity entity:  retrievedEntities ) {
            if ( entity.getTypeName().equals(TestUtilsV2.DATABASE_TYPE)) {
                retrievedDBEntity = entity;
            }

            if ( entity.getTypeName().equals(TestUtilsV2.TABLE_TYPE)) {
                retrievedTableEntity = entity;
            }

            if ( entity.getTypeName().equals(TestUtilsV2.COLUMN_TYPE)) {
                retrievedColumnEntity = entity;
            }
        }

        if ( retrievedDBEntity != null) {
            LOG.info("verifying entity of type {} ", dbEntity.getTypeName());
            verifyAttributes(retrievedDBEntity.getAttributes(), dbEntity.getAttributes());
        }

        if ( retrievedColumnEntity != null) {
            LOG.info("verifying entity of type {} ", columns.get(0).getTypeName());
            Assert.assertEquals(columns.get(0).getAttribute(AtlasClient.NAME), retrievedColumnEntity.getAttribute(AtlasClient.NAME));
            Assert.assertEquals(columns.get(0).getAttribute("type"), retrievedColumnEntity.getAttribute("type"));
        }

        if ( retrievedTableEntity != null) {
            LOG.info("verifying entity of type {} ", tableEntity.getTypeName());

            //String
            Assert.assertEquals(tableEntity.getAttribute(AtlasClient.NAME), retrievedTableEntity.getAttribute(AtlasClient.NAME));
            //Map
            Assert.assertEquals(tableEntity.getAttribute("parametersMap"), retrievedTableEntity.getAttribute("parametersMap"));
            //enum
            Assert.assertEquals(tableEntity.getAttribute("tableType"), retrievedTableEntity.getAttribute("tableType"));
            //date
            Assert.assertEquals(tableEntity.getAttribute("created"), retrievedTableEntity.getAttribute("created"));
            //array of Ids
            Assert.assertEquals(((List<AtlasObjectId>) retrievedTableEntity.getAttribute("columns")).get(0).getGuid(), retrievedColumnEntity.getGuid());
            //array of structs
            Assert.assertEquals(((List<AtlasStruct>) retrievedTableEntity.getAttribute("partitions")), tableEntity.getAttribute("partitions"));
        }
    }

    public static void verifyAttributes(Map<String, Object> actual, Map<String, Object> expected) throws Exception {
        for (String name : actual.keySet() ) {
            LOG.info("verifying attribute {} ", name);

            if ( expected.get(name) != null) {
                Assert.assertEquals(actual.get(name), expected.get(name));
            }
        }
    }

    AtlasEntity serDeserEntity(AtlasEntity entity) throws IOException {
        //Convert from json to object and back to trigger the case where it gets translated to a map for attributes instead of AtlasEntity
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        String entityJson = mapper.writeValueAsString(entity);
        //JSON from String to Object
        AtlasEntity newEntity = mapper.readValue(entityJson, AtlasEntity.class);
        return newEntity;
    }
}
