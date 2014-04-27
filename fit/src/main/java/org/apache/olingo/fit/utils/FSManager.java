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
package org.apache.olingo.fit.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.EnumMap;
import java.util.Map;
import javax.ws.rs.NotFoundException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSelectInfo;
import org.apache.commons.vfs2.FileSelector;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.FileSystemManager;
import org.apache.commons.vfs2.VFS;
import org.apache.olingo.commons.api.data.ResWrap;
import org.apache.olingo.commons.api.edm.constants.ODataServiceVersion;
import org.apache.olingo.commons.core.data.AtomEntryImpl;
import org.apache.olingo.commons.core.data.AtomSerializer;
import org.apache.olingo.fit.serializer.JSONEntryContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FSManager {

  /**
   * Logger.
   */
  protected static final Logger LOG = LoggerFactory.getLogger(AbstractUtilities.class);

  private static String MEM_PREFIX = "ram://";

  private static String RES_PREFIX = "res://";

  private final FileSystemManager fsManager;

  private static Map<ODataServiceVersion, FSManager> instance =
          new EnumMap<ODataServiceVersion, FSManager>(ODataServiceVersion.class);

  private final ODataServiceVersion version;

  public static FSManager instance(final ODataServiceVersion version) throws Exception {
    if (!instance.containsKey(version)) {
      instance.put(version, new FSManager(version));
    }
    return instance.get(version);
  }

  private FSManager(final ODataServiceVersion version) throws Exception {
    this.version = version;
    fsManager = VFS.getManager();
  }

  public String getAbsolutePath(final String relativePath, final Accept accept) {
    return File.separatorChar + version.name() + File.separatorChar + relativePath
            + (accept == null ? "" : accept.getExtension());
  }

  public FileObject putInMemory(final InputStream is, final String path) throws IOException {
    final FileObject memObject = fsManager.resolveFile(MEM_PREFIX + path);

    if (memObject.exists()) {
      memObject.delete();
    }

    // create in-memory file
    memObject.createFile();

    // read in-memory content
    final OutputStream os = memObject.getContent().getOutputStream();
    IOUtils.copy(is, os);
    IOUtils.closeQuietly(is);
    IOUtils.closeQuietly(os);

    return memObject;
  }

  public void putInMemory(final ResWrap<AtomEntryImpl> container, final String relativePath)
          throws IOException {
    try {
      final AtomSerializer atomSerializer = Commons.getAtomSerializer(version);

      final ByteArrayOutputStream content = new ByteArrayOutputStream();
      final OutputStreamWriter writer = new OutputStreamWriter(content, Constants.ENCODING);

      atomSerializer.write(writer, container);
      writer.flush();

      putInMemory(new ByteArrayInputStream(content.toByteArray()), getAbsolutePath(relativePath, Accept.ATOM));
      content.reset();

      final ObjectMapper mapper = Commons.getJSONMapper(version);
      mapper.writeValue(
              writer, new JSONEntryContainer(
                      container.getContextURL(),
                      container.getMetadataETag(),
                      new DataBinder(version).toJSONEntry(container.getPayload())));

      putInMemory(new ByteArrayInputStream(content.toByteArray()), getAbsolutePath(relativePath, Accept.JSON_FULLMETA));
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  public InputStream readFile(final String relativePath) {
    return readFile(relativePath, null);
  }

  public InputStream readFile(final String relativePath, final Accept accept) {
    final String path = getAbsolutePath(relativePath, accept);
    LOG.info("Read {}", path);

    try {
      FileObject fileObject = fsManager.resolveFile(MEM_PREFIX + path);

      if (!fileObject.exists()) {
        LOG.warn("In-memory path '{}' not found", path);

        try {
          fileObject = fsManager.resolveFile(RES_PREFIX + path);
          fileObject = putInMemory(fileObject.getContent().getInputStream(), path);
        } catch (FileSystemException fse) {
          LOG.warn("Resource path '{}' not found", path, fse);
        }
      }

      if (!fileObject.exists()) {
        throw new NotFoundException();
      }

      // return new in-memory content
      return fileObject.getContent().getInputStream();
    } catch (IOException e) {
      throw new NotFoundException(e);
    }
  }

  public void deleteFile(final String relativePath) {
    for (Accept accept : Accept.values()) {
      final String path = getAbsolutePath(relativePath, accept);
      LOG.info("Delete {}", path);

      try {
        final FileObject fileObject = fsManager.resolveFile(MEM_PREFIX + path);

        if (fileObject.exists()) {
          fileObject.delete();
        }
      } catch (IOException ignore) {
        // ignore exception
      }
    }
  }

  public FileObject resolve(final String path) throws FileSystemException {
    FileObject res = fsManager.resolveFile(MEM_PREFIX + path);

    if (!res.exists()) {
      res = fsManager.resolveFile(RES_PREFIX + path);
    }

    if (!res.exists()) {
      throw new FileSystemException("Unresolved path " + path);
    }

    return res;
  }

  public FileObject[] findByExtension(final FileObject fo, final String ext) throws FileSystemException {
    return fo.findFiles(new FileSelector() {
      @Override
      public boolean includeFile(final FileSelectInfo fileInfo) throws Exception {
        return fileInfo.getFile().getName().getExtension().equals(ext);
      }

      @Override
      public boolean traverseDescendents(final FileSelectInfo fileInfo) throws Exception {
        return true;
      }
    });
  }
}
