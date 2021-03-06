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


import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.TypeCategory;
import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasEntityWithAssociations;
import org.apache.atlas.type.AtlasType;

import java.util.HashMap;
import java.util.Map;

public interface AtlasFormatConverter {
    Object fromV1ToV2(Object v1Obj, AtlasType type, ConverterContext context) throws AtlasBaseException;

    Object fromV2ToV1(Object v2Obj, AtlasType type, ConverterContext context) throws AtlasBaseException;

    TypeCategory getTypeCategory();

    public static class ConverterContext {

        private Map<String, AtlasEntityWithAssociations> entities = null;

        public void addEntity(AtlasEntityWithAssociations entity) {
            if (entities == null) {
                entities = new HashMap<>();
            }
            entities.put(entity.getGuid(), entity);
        }

        public void addEntity(AtlasEntity entity) {
            if (entities == null) {
                entities = new HashMap<>();
            }
            entities.put(entity.getGuid(), new AtlasEntityWithAssociations(entity));
        }

        public boolean exists(AtlasEntityWithAssociations entity) {
            return entities != null ? entities.containsKey(entity.getGuid()) : false;
        }

        public AtlasEntity getById(String guid) {

            if( entities != null) {
                return entities.get(guid);
            }

            return null;
        }

        public Map<String, AtlasEntityWithAssociations> getEntities() {
            return entities;
        }

        public void addEntities(Map<String, AtlasEntity> entities) {
            if (this.entities == null) {
                this.entities = new HashMap<>(entities.size());
            }
            for (String entityId : entities.keySet()) {
                this.entities.put(entityId, new AtlasEntityWithAssociations(entities.get(entityId)));
            }
        }
    }
}
