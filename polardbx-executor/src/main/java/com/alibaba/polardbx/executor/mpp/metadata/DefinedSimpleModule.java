/*
 * Copyright [2013-2021], Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.polardbx.executor.mpp.metadata;

import com.alibaba.polardbx.common.jdbc.ParameterContext;
import com.alibaba.polardbx.common.jdbc.PruneRawString;
import com.alibaba.polardbx.common.jdbc.RawString;
import com.fasterxml.jackson.databind.module.SimpleModule;

import javax.inject.Inject;

public class DefinedSimpleModule extends SimpleModule {
    @Inject
    public DefinedSimpleModule() {
        addSerializer(ParameterContext.class, new DefinedJsonSerde.ParameterContextSerializer());
        addDeserializer(ParameterContext.class, new DefinedJsonSerde.ParameterContextDeserializer());
        addSerializer(RawString.class, new DefinedJsonSerde.RawStringSerializer());
        addDeserializer(RawString.class, new DefinedJsonSerde.RawStringDeserializer());
        addSerializer(PruneRawString.class, new DefinedJsonSerde.PruneRawStringSerializer());
        addDeserializer(PruneRawString.class, new DefinedJsonSerde.PruneRawStringDeserializer());
    }
}
