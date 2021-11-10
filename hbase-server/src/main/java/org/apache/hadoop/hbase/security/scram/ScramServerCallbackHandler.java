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
import org.apache.hadoop.hbase.ipc.RpcServer;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.scram.CredentialCache;
import org.apache.hadoop.security.scram.ScramCredential;
import org.apache.hadoop.security.scram.ScramCredentialCallback;
import org.apache.hadoop.security.token.SecretManager;
import org.apache.hadoop.security.token.TokenIdentifier;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

/**
 * This is copy of {@link org.apache.hadoop.security.scram.ScramClientCallbackHandler}
 * Only difference is, when hbase needs to create SaslServer, the connection type needs to be RpcServer.Connection
 * On hadoop side the connection type is Server.Connection
 * Those two classes cannot be cast to each other, and this difference does not allow us to use hadoop scram mechanism
 * Hence, we copied the class to be able to override the method RpcAuthMethod.createSaslServer()
 */
public class ScramServerCallbackHandler implements CallbackHandler {
  private final CredentialCache.Cache<ScramCredential> credentialCache;
  private SecretManager<TokenIdentifier> secretManager;
  private RpcServer.Connection connection;

  public ScramServerCallbackHandler(CredentialCache.Cache<ScramCredential> credentialCache, SecretManager<TokenIdentifier> secretManager,
      RpcServer.Connection connection) {
    this.credentialCache = credentialCache;
    this.secretManager = secretManager;
    this.connection = connection;
  }

  public static <T extends TokenIdentifier> T getIdentifier(String id,
      SecretManager<T> secretManager) throws SecretManager.InvalidToken {
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
  public static byte[] decodeIdentifier(String identifier) {
    return Base64.decodeBase64(identifier.getBytes());
  }

  @Override
  public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
    String username = null;
    for (Callback callback : callbacks) {
      if (callback instanceof NameCallback)
        username = ((NameCallback) callback).getDefaultName();
      else if (callback instanceof ScramCredentialCallback) {
        TokenIdentifier tokenIdentifier = getIdentifier(username, secretManager);
        connection.attemptingUser = tokenIdentifier.getUser();
        ((ScramCredentialCallback) callback).scramCredential(
            credentialCache.get(UserGroupInformation.getLoginUser().getUserName()));
      } else
        throw new UnsupportedCallbackException(callback);
    }
  }
}
