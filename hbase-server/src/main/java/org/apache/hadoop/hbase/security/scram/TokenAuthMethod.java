/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase.security.scram;

import org.apache.commons.codec.binary.Base64;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.SaslRpcServer;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.SecretManager;
import org.apache.hadoop.security.token.TokenIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * This is copy of {@link org.apache.hadoop.security.rpcauth.TokenAuthMethod}
 * Only difference is, when hbase needs to create SaslServer, the connection type needs to be RpcServer.Connection
 * On hadoop side the connection type is Server.Connection
 * Those two classes cannot be cast to each other, and this difference does not allow us to use hadoop scram mechanism
 * Hence, we copied the class to be able to override the method RpcAuthMethod.createSaslServer()
 */
public class TokenAuthMethod extends RpcAuthMethod {
  private static final Logger LOG = LoggerFactory.getLogger(TokenAuthMethod.class);

  protected TokenAuthMethod(byte code, String simpleName, String mechanismName, UserGroupInformation.AuthenticationMethod authMethod) {
    super(code, simpleName, mechanismName, authMethod);
  }

  @Override
  public boolean isProxyAllowed() {
    return false;
  }

  @Override
  @SuppressWarnings("unchecked")
  public UserGroupInformation getAuthorizedUgi(String authorizedId,
      SecretManager secretManager) throws IOException {
    TokenIdentifier tokenId = getIdentifier(authorizedId, secretManager);
    UserGroupInformation ugi = tokenId.getUser();
    if (ugi == null) {
      throw new AccessControlException(
          "Can't retrieve username from tokenIdentifier.");
    }
    ugi.addTokenIdentifier(tokenId);
    return ugi;
  }

  @Override
  public boolean isSasl() {
    return true;
  }

  @Override
  public String getProtocol() throws IOException {
    return SaslRpcServer.SASL_DEFAULT_REALM;
  }

  @Override
  public String getServerId() throws IOException {
    return "";
  }
  public static char[] encodePassword(byte[] password) {
    return new String(Base64.encodeBase64(password)).toCharArray();
  }

  public static <T extends TokenIdentifier> T getIdentifier(String id,
      SecretManager<T> secretManager) throws
      SecretManager.InvalidToken {
    byte[] tokenId = decodeIdentifier(id);
    T tokenIdentifier = secretManager.createIdentifier();
    try {
      tokenIdentifier.readFields(new DataInputStream(new ByteArrayInputStream(
          tokenId)));
    } catch (IOException e) {
      throw (SecretManager.InvalidToken) new SecretManager.InvalidToken(
          "Can't de-serialize tokenIdentifier").initCause(e);
    }
    return tokenIdentifier;
  }

  public static String encodeIdentifier(byte[] identifier) {
    return new String(Base64.encodeBase64(identifier));
  }

  public static byte[] decodeIdentifier(String identifier) {
    return Base64.decodeBase64(identifier.getBytes());
  }
}
