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
package com.google.protobuf;  // This is a lie.

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper class to extract byte arrays from {@link ByteString} without copy.
 * <p>
 * Without this protobufs would force us to copy every single byte array out
 * of the objects de-serialized from the wire (which already do one copy, on
 * top of the copies the JVM does to go from kernel buffer to C buffer and
 * from C buffer to JVM buffer).
 *
 * @since 0.96.1
 */
public final class HBaseZeroCopyByteString {
  // Gotten from AsyncHBase code base with permission.
  /** Private constructor so this class cannot be instantiated. */
  private HBaseZeroCopyByteString() {
    throw new UnsupportedOperationException("Should never be here.");
  }

  /**
   * Wraps a byte array in a {@link ByteString} without copying it.
   * @param array array to be wrapped
   * @return wrapped array
   */
  public static ByteString wrap(final byte[] array) {
    return ByteString.wrap(array);
  }

  /**
   * Wraps a subset of a byte array in a {@link ByteString} without copying it.
   * @param array array to be wrapped
   * @param offset from
   * @param length length
   * @return wrapped array
   */
  public static ByteString wrap(final byte[] array, int offset, int length) {
    return ByteString.wrap(array, offset, length);
  }

  // TODO:
  // ZeroCopyLiteralByteString.wrap(this.buf, 0, this.count);

  /**
   * Extracts the byte array from the given {@link ByteString} without copy.
   * @param buf A buffer from which to extract the array.  This buffer must be
   * actually an instance of a {@code LiteralByteString}.
   * @return byte[] representation
   */
  public static byte[] zeroCopyGetBytes(final ByteString buf) {
    try {
      if (Class.forName("com.google.protobuf.ByteString$LiteralByteString").isAssignableFrom(buf.getClass())) {
        java.lang.reflect.Field bytes = getDeclaredFieldWithInherited("bytes", buf.getClass());
        bytes.setAccessible(true);
        return (byte[])bytes.get(buf);
      }
    } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException e) {
      return buf.toByteArray();
    }
    // In case it's not LiteralByteString
    return buf.toByteArray();
  }

  private static java.lang.reflect.Field getDeclaredFieldWithInherited(String fieldName, Class clazz)
          throws NoSuchFieldException {
    List<Field> allFields = getAllFields(new ArrayList<>(), clazz);

    for (java.lang.reflect.Field field: allFields) {
      if (field.getName().equals(fieldName)) {
        return field;
      }
    }
    throw new NoSuchFieldException();
  }

  private static List<java.lang.reflect.Field> getAllFields(List<java.lang.reflect.Field> fields, Class clazz) {
    fields.addAll(Arrays.asList(clazz.getDeclaredFields()));

    if (clazz.getSuperclass() != null) {
      getAllFields(fields, clazz.getSuperclass());
    }

    return fields;
  }
}
