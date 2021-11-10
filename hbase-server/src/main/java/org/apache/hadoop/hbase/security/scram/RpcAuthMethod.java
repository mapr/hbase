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

import org.apache.hadoop.hbase.ipc.RpcServer;
import org.apache.hadoop.ipc.protobuf.IpcConnectionContextProtos;
import org.apache.hadoop.security.AccessControlException;
import org.apache.hadoop.security.UserGroupInformation;
import org.apache.hadoop.security.token.SecretManager;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslServer;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

/**
 * This is copy of {@link org.apache.hadoop.security.rpcauth.RpcAuthMethod}
 * Only difference is, when hbase needs to create SaslServer, the connection type needs to be RpcServer.Connection
 * On hadoop side the connection type is Server.Connection
 * Those two classes cannot be cast to each other, and this difference does not allow us to use hadoop scram mechanism
 * Hence, we copied the class to be able to override the method RpcAuthMethod.createSaslServer()
 */
public abstract class RpcAuthMethod {
  private static final String[] LOGIN_MODULES = new String[0];
  /** @deprecated */
  @Deprecated
  protected final byte authcode;
  protected final String simpleName;
  protected final String mechanismName;
  protected final UserGroupInformation.AuthenticationMethod authenticationMethod;

  protected RpcAuthMethod(byte code, String simpleName, String mechanismName, UserGroupInformation.AuthenticationMethod authMethod) {
    this.authcode = code;
    this.simpleName = simpleName;
    this.mechanismName = mechanismName;
    this.authenticationMethod = authMethod;
  }

  /** @deprecated */
  @Deprecated
  public byte getAuthCode() {
    return this.authcode;
  }

  public String getMechanismName() {
    return this.mechanismName;
  }

  public UserGroupInformation.AuthenticationMethod getAuthenticationMethod() {
    return this.authenticationMethod;
  }

  public CallbackHandler createCallbackHandler() {
    throw new UnsupportedOperationException(this.getClass().getCanonicalName() + " does not support createCallbackHandler()");
  }

  public final int hashCode() {
    return this.getClass().getName().hashCode();
  }

  public final boolean equals(Object that) {
    if (this == that) {
      return true;
    } else {
      if (that instanceof org.apache.hadoop.security.rpcauth.RpcAuthMethod) {
        org.apache.hadoop.security.rpcauth.RpcAuthMethod other = (org.apache.hadoop.security.rpcauth.RpcAuthMethod)that;
        this.getClass().getName().equals(other.getClass().getName());
      }

      return false;
    }
  }

  public String[] loginModules() {
    return LOGIN_MODULES;
  }

  public void write(DataOutput out) throws IOException {
    out.write(this.authcode);
  }

  public UserGroupInformation getUser(UserGroupInformation ticket) {
    return ticket;
  }

  public void writeUGI(UserGroupInformation ugi, IpcConnectionContextProtos.UserInformationProto.Builder ugiProto) {
  }

  public UserGroupInformation getAuthorizedUgi(String authorizedId, SecretManager secretManager) throws IOException {
    return UserGroupInformation.createRemoteUser(authorizedId);
  }

  public boolean shouldReLogin() throws IOException {
    return false;
  }

  public void reLogin() throws IOException {
  }

  public boolean isProxyAllowed() {
    return true;
  }

  public String toString() {
    return this.simpleName.toUpperCase();
  }

  public boolean isNegotiable() {
    return false;
  }

  public boolean isSasl() {
    return false;
  }

  public String getProtocol() throws IOException {
    throw new AccessControlException("Server does not support SASL " + this.simpleName.toUpperCase());
  }

  public String getServerId() throws IOException {
    throw new AccessControlException("Server does not support SASL " + this.simpleName.toUpperCase());
  }

  public SaslClient createSaslClient(Map<String, Object> saslProperties) throws IOException {
    throw new UnsupportedOperationException(this.getClass().getCanonicalName() + " does not support createSaslClient()");
  }

  public SaslServer createSaslServer(RpcServer.Connection connection, Map<String, Object> saslProperties) throws IOException, InterruptedException {
    throw new UnsupportedOperationException(this.getClass().getCanonicalName() + " does not support createSaslServer()");
  }
}
