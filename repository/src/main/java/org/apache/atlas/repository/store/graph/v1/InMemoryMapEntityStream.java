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
package org.apache.atlas.repository.store.graph.v1;


import org.apache.atlas.model.instance.AtlasEntity;
import org.apache.atlas.model.instance.AtlasObjectId;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class InMemoryMapEntityStream implements EntityStream {

    private final Map<AtlasObjectId, AtlasEntity>                 entities = new HashMap<>();
    private       Iterator<Map.Entry<AtlasObjectId, AtlasEntity>> iterator;

    public InMemoryMapEntityStream(Map<String, AtlasEntity> entityMap) {
        for (AtlasEntity entity : entityMap.values()) {
            entities.put(entity.getAtlasObjectId(), entity);
        }

        this.iterator = entities.entrySet().iterator();
    }

    @Override
    public boolean hasNext() {
        return iterator.hasNext();
    }

    @Override
    public AtlasEntity next() {
        return iterator.hasNext() ? iterator.next().getValue() : null;
    }

    @Override
    public void reset() {
        iterator = entities.entrySet().iterator();
    }

    @Override
    public AtlasEntity getById(final AtlasObjectId id) {
        return entities.get(id);
    }
}
