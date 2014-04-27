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
package org.apache.olingo.client.core.v4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.apache.olingo.client.api.v4.ODataClient;
import org.apache.olingo.client.core.AbstractTest;
import org.apache.olingo.commons.api.data.Feed;
import org.apache.olingo.commons.api.data.ResWrap;
import org.apache.olingo.commons.api.domain.v4.ODataEntity;
import org.apache.olingo.commons.api.domain.v4.ODataEntitySet;
import org.apache.olingo.commons.api.format.ODataPubFormat;
import org.apache.olingo.commons.core.op.ResourceFactory;
import org.junit.Test;

public class EntitySetTest extends AbstractTest {

  @Override
  protected ODataClient getClient() {
    return v4Client;
  }

  private void read(final ODataPubFormat format) throws IOException {
    final InputStream input = getClass().getResourceAsStream("Customers." + getSuffix(format));
    final ODataEntitySet entitySet = getClient().getBinder().getODataEntitySet(
            getClient().getDeserializer().toFeed(input, format));
    assertNotNull(entitySet);

    assertEquals(2, entitySet.getEntities().size());
    assertNull(entitySet.getNext());

    final ODataEntitySet written = getClient().getBinder().getODataEntitySet(new ResWrap<Feed>((URI) null, null,
            getClient().getBinder().getFeed(
                    entitySet, ResourceFactory.feedClassForFormat(format == ODataPubFormat.ATOM))));
    assertEquals(entitySet, written);
  }

  @Test
  public void fromAtom() throws IOException {
    read(ODataPubFormat.ATOM);
  }

  @Test
  public void fromJSON() throws IOException {
    read(ODataPubFormat.JSON);
  }

  private void ref(final ODataPubFormat format) {
    final InputStream input = getClass().getResourceAsStream("collectionOfEntityReferences." + getSuffix(format));
    final ODataEntitySet entitySet = getClient().getBinder().getODataEntitySet(
            getClient().getDeserializer().toFeed(input, format));
    assertNotNull(entitySet);

    for (ODataEntity entity : entitySet.getEntities()) {
      assertNotNull(entity.getReference());
    }
    entitySet.setCount(entitySet.getEntities().size());

    final ODataEntitySet written = getClient().getBinder().getODataEntitySet(new ResWrap<Feed>((URI) null, null,
            getClient().getBinder().getFeed(
                    entitySet, ResourceFactory.feedClassForFormat(format == ODataPubFormat.ATOM))));
    assertEquals(entitySet, written);
  }

  @Test
  public void atomRef() {
    ref(ODataPubFormat.ATOM);
  }

  @Test
  public void jsonRef() {
    ref(ODataPubFormat.JSON);
  }
}
