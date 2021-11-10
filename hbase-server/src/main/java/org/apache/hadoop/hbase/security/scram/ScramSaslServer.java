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

import org.apache.hadoop.security.scram.ScramCredential;
import org.apache.hadoop.security.scram.ScramCredentialCallback;
import org.apache.hadoop.security.scram.ScramFormatter;
import org.apache.hadoop.security.scram.ScramMechanism;
import org.apache.hadoop.security.scram.ScramMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import javax.security.sasl.SaslServerFactory;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * SaslServer implementation for SASL/SCRAM. This server is configured with a callback
 * handler for integration with a credential manager. Kafka brokers provide callbacks
 * based on a Zookeeper-based password store.
 *
 * @see <a href="https://tools.ietf.org/html/rfc5802">RFC 5802</a>
 * This is copy of {@link org.apache.hadoop.security.scram.ScramSaslServer}
 * Only difference is, when hbase needs to create SaslServer, the connection type needs to be RpcServer.Connection
 * On hadoop side the connection type is Server.Connection
 * Those two classes cannot be cast to each other, and this difference does not allow us to use hadoop scram mechanism
 * Hence, we copied the class to be able to override the method RpcAuthMethod.createSaslServer()
 */
public class ScramSaslServer implements SaslServer {
  private static final Logger log = LoggerFactory.getLogger(ScramSaslServer.class);

  enum State {
    RECEIVE_CLIENT_FIRST_MESSAGE,
    RECEIVE_CLIENT_FINAL_MESSAGE,
    COMPLETE,
    FAILED
  };

  private final ScramMechanism mechanism;
  private final ScramFormatter formatter;
  private final CallbackHandler callbackHandler;
  private State state;
  private String username;
  private ScramMessages.ClientFirstMessage clientFirstMessage;
  private ScramMessages.ServerFirstMessage serverFirstMessage;
  private String serverNonce;
  private ScramCredential scramCredential;

  public ScramSaslServer(ScramMechanism mechanism, Map<String, ?> props, CallbackHandler callbackHandler) throws
      NoSuchAlgorithmException {
    this.mechanism = mechanism;
    this.formatter = new ScramFormatter(mechanism);
    this.callbackHandler = callbackHandler;
    setState(State.RECEIVE_CLIENT_FIRST_MESSAGE);
  }

  @Override
  public byte[] evaluateResponse(byte[] response) throws SaslException {
    try {
      switch (state) {
      case RECEIVE_CLIENT_FIRST_MESSAGE:
        this.clientFirstMessage = new ScramMessages.ClientFirstMessage(response);
        serverNonce = formatter.secureRandomString();
        try {
          String saslName = clientFirstMessage.saslName();
          this.username = formatter.username(saslName);
          NameCallback nameCallback = new NameCallback("username", username);
          ScramCredentialCallback credentialCallback = new ScramCredentialCallback();
          callbackHandler.handle(new Callback[]{nameCallback, credentialCallback});
          this.scramCredential = credentialCallback.scramCredential();
          if (scramCredential == null)
            throw new SaslException("Authentication failed: Invalid user credentials");
          if (scramCredential.iterations() < mechanism.minIterations())
            throw new SaslException("Iterations " + scramCredential.iterations() +  " is less than the minimum " + mechanism.minIterations() + " for " + mechanism);
          this.serverFirstMessage = new ScramMessages.ServerFirstMessage(clientFirstMessage.nonce(),
              serverNonce,
              scramCredential.salt(),
              scramCredential.iterations());
          setState(State.RECEIVE_CLIENT_FINAL_MESSAGE);
          return serverFirstMessage.toBytes();
        } catch (IOException | NumberFormatException | UnsupportedCallbackException e) {
          throw new SaslException("Authentication failed: Credentials could not be obtained", e);
        }

      case RECEIVE_CLIENT_FINAL_MESSAGE:
        try {
          ScramMessages.ClientFinalMessage clientFinalMessage = new ScramMessages.ClientFinalMessage(response);
          verifyClientProof(clientFinalMessage);
          byte[] serverKey = scramCredential.serverKey();
          byte[] serverSignature = formatter.serverSignature(serverKey, clientFirstMessage, serverFirstMessage, clientFinalMessage);
          ScramMessages.ServerFinalMessage serverFinalMessage = new ScramMessages.ServerFinalMessage(null, serverSignature);
          setState(State.COMPLETE);
          return serverFinalMessage.toBytes();
        } catch (InvalidKeyException e) {
          throw new SaslException("Authentication failed: Invalid client final message", e);
        }

      default:
        throw new SaslException("Unexpected challenge in Sasl server state " + state);
      }
    } catch (SaslException e) {
      setState(State.FAILED);
      throw e;
    }
  }

  @Override
  public String getAuthorizationID() {
    if (!isComplete())
      throw new IllegalStateException("Authentication exchange has not completed");
    String authzId = clientFirstMessage.authorizationId();
    return authzId == null || authzId.length() == 0 ? username : authzId;
  }

  @Override
  public String getMechanismName() {
    return mechanism.mechanismName();
  }

  @Override
  public Object getNegotiatedProperty(String propName) {
    if (!isComplete())
      throw new IllegalStateException("Authentication exchange has not completed");
    return null;
  }

  @Override
  public boolean isComplete() {
    return state == State.COMPLETE;
  }

  @Override
  public byte[] unwrap(byte[] incoming, int offset, int len) throws SaslException {
    if (!isComplete())
      throw new IllegalStateException("Authentication exchange has not completed");
    return Arrays.copyOfRange(incoming, offset, offset + len);
  }

  @Override
  public byte[] wrap(byte[] outgoing, int offset, int len) throws SaslException {
    if (!isComplete())
      throw new IllegalStateException("Authentication exchange has not completed");
    return Arrays.copyOfRange(outgoing, offset, offset + len);
  }

  @Override
  public void dispose() throws SaslException {
  }

  private void setState(State state) {
    log.debug("Setting SASL/{} server state to {}", mechanism, state);
    this.state = state;
  }

  private void verifyClientProof(ScramMessages.ClientFinalMessage clientFinalMessage) throws SaslException {
    try {
      byte[] expectedStoredKey = scramCredential.storedKey();
      byte[] clientSignature = formatter.clientSignature(expectedStoredKey, clientFirstMessage, serverFirstMessage, clientFinalMessage);
      byte[] computedStoredKey = formatter.storedKey(clientSignature, clientFinalMessage.proof());
      if (!Arrays.equals(computedStoredKey, expectedStoredKey))
        throw new SaslException("Invalid client credentials");
    } catch (InvalidKeyException e) {
      throw new SaslException("Sasl client verification failed", e);
    }
  }

  public static class ScramSaslServerFactory implements SaslServerFactory {

    @Override
    public SaslServer createSaslServer(String mechanism, String protocol, String serverName, Map<String, ?> props, CallbackHandler cbh)
        throws SaslException {

      if (!ScramMechanism.isScram(mechanism)) {
        throw new SaslException(String.format("Requested mechanism '%s' is not supported. Supported mechanisms are '%s'.",
            mechanism, ScramMechanism.mechanismNames()));
      }
      try {
        return new ScramSaslServer(ScramMechanism.forMechanismName(mechanism), props, cbh);
      } catch (NoSuchAlgorithmException e) {
        throw new SaslException("Hash algorithm not supported for mechanism " + mechanism, e);
      }
    }

    @Override
    public String[] getMechanismNames(Map<String, ?> props) {
      Collection<String> mechanisms = ScramMechanism.mechanismNames();
      return mechanisms.toArray(new String[mechanisms.size()]);
    }
  }
}
