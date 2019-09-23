/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.druid.data.input.bigo;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Binder;
import org.apache.druid.initialization.DruidModule;

import java.util.Arrays;
import java.util.List;

public class BigoExtensionsModule implements DruidModule
{

  @Override
  public List<? extends Module> getJacksonModules()
  {
    return Arrays.asList(
        new SimpleModule("BigoInputRowParserModule")
            .registerSubtypes(
                new NamedType(BigoInputRowParser.class, "bigo")
            )
    );
  }

  @Override
  public void configure(Binder binder)
  { }
}